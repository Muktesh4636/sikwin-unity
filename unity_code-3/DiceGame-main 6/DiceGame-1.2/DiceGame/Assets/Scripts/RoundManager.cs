using System;
using System.Collections;
using System.Globalization;
using Newtonsoft.Json.Linq;
using UnityEngine;

/// <summary>
/// RoundManager – replaces WebSocket for all real-time game-flow events.
///
/// How it works
/// ─────────────
/// 1. Call <see cref="StartRound"/> after login (or on focus/resume return).
/// 2. Fetch time API → get serverNow + round start time → compute elapsed (with half-RTT compensation).
/// 3. Fetch game settings once (cached).
/// 4. Fire game_state immediately so UI/camera snaps to correct phase.
/// 5. Schedule only the events that haven't happened yet as individual tracked coroutines.
/// 6. TimerLoop fires OnTimerUpdate every second (count-up: elapsed → TotalRoundDuration).
/// 7. When game_end fires, wait postRoundBuffer seconds then re-fetch with mustBeNewRound=true.
///
/// Bug fixes vs previous version
/// ──────────────────────────────
/// • StopAllCoroutines() removed everywhere — it was killing DelayedStartNextRound and
///   TimerLoop from inside themselves. Every coroutine is now tracked and stopped individually.
/// • Late join at/past RoundEndTime (e.g. 55 s into 60 s round): game_end fires immediately,
///   then we wait postRoundBuffer seconds before fetching the next round. If the server still
///   returns the old round_id (mustBeNewRound guard) we retry with backoff instead of looping.
/// • Periodic re-sync no longer blows away running event coroutines — it only updates the
///   clock anchor and replaces unfired event coroutines in their tracked slots.
/// </summary>
[RequireComponent(typeof(GameApiClient))]
public class RoundManager : MonoBehaviour
{
    // ── Inspector ──────────────────────────────────────────────────────────────
    [Header("Timing")]
    [Tooltip("Re-sync with time API every N seconds to correct local clock drift. 30 s recommended. 0 = off.")]
    [SerializeField] private float resyncIntervalSeconds = 30f;

    [Tooltip("Seconds to wait after game_end before fetching the next round. " +
             "Gives the server time to flip the round. Increase if stuck on the old round.")]
    [SerializeField] private float postRoundBuffer = 0.5f;

    [Tooltip("How many times to retry the time API before giving up")]
    [SerializeField] private int maxRetries = 5;

    [Tooltip("Seconds between retries")]
    [SerializeField] private float retryDelay = 2f;

    [Header("Fallback Settings (used when settings API fails)")]
    [SerializeField] private int fallbackTotalRoundDuration = 60;
    [SerializeField] private int fallbackBettingCloseTime   = 30;
    [SerializeField] private int fallbackDiceRollTime       = 35;
    [SerializeField] private int fallbackDiceResultTime     = 45;
    [SerializeField] private int fallbackResultAnnounceTime = 50;
    [SerializeField] private int fallbackRoundEndTime       = 55;

    // ── State ──────────────────────────────────────────────────────────────────
    private GameApiClient api;

    private string currentRoundId      = null;
    private float  roundStartUnityTime = 0f;

    private GameApiClient.GameSettings settings;

    // Every coroutine tracked individually. StopAllCoroutines() is never called.
    private Coroutine timerCoroutine;
    private Coroutine resyncCoroutine;
    private Coroutine fetchCoroutine;
    private Coroutine nextRoundCoroutine;

    // Per-event coroutine slots — allows re-sync to cancel and re-schedule only unfired events
    private Coroutine evtGameStart;
    private Coroutine evtDiceRollWarn;
    private Coroutine evtDiceResult;
    private Coroutine evtResultAnn;
    private Coroutine evtGameEnd;

    // Prevent double-firing on re-sync
    private bool firedGameStart    = false;
    private bool firedDiceRollWarn = false;
    private bool firedDiceResult   = false;
    private bool firedResultAnn    = false;
    private bool firedGameEnd      = false;

    // ── Unity Lifecycle ────────────────────────────────────────────────────────

    private void Awake() => api = GetComponent<GameApiClient>();
    private void OnDestroy() => StopEverything();

    // ── Public API ─────────────────────────────────────────────────────────────

    /// <summary>
    /// Call after login, focus return, or app resume.
    /// Safe to call multiple times — stops previous state cleanly first.
    /// </summary>
    public void StartRound()
    {
        StopEverything();
        fetchCoroutine = StartCoroutine(FetchAndSchedule(0, mustBeNewRound: false));
    }

    public void StopRound() => StopEverything();

    // ── Core ───────────────────────────────────────────────────────────────────

    /// <param name="attempt">Retry counter.</param>
    /// <param name="mustBeNewRound">
    /// When true, reject the response if it still contains the previous round_id.
    /// Used after game_end to wait for the server round flip.
    /// </param>
    private IEnumerator FetchAndSchedule(int attempt, bool mustBeNewRound)
    {
        // 1. Load settings (cached after first success) ─────────────────────────
        if (settings == null)
        {
            bool ok = false; string err = null; GameApiClient.GameSettings s = null;
            api.GetGameSettings((b, v, e) => { ok = b; s = v; err = e; });
            yield return new WaitUntil(() => ok || err != null);
            settings = (ok && s != null) ? s : BuildFallbackSettings();
            if (!ok) Debug.LogWarning($"[RM] Settings failed: {err} – using fallback.");
        }

        // 2. Fetch time API ─────────────────────────────────────────────────────
        bool timeOk = false; string timeErr = null;
        GameApiClient.ServerTimeResponse timeResp = null;

        float sentAt = Time.realtimeSinceStartup;
        api.GetServerTime((b, v, e) => { timeOk = b; timeResp = v; timeErr = e; });
        yield return new WaitUntil(() => timeOk || timeErr != null);
        float halfRtt = (Time.realtimeSinceStartup - sentAt) * 0.5f;

        if (!timeOk || timeResp == null)
        {
            if (attempt < maxRetries)
            {
                Debug.LogWarning($"[RM] Time API failed ({attempt + 1}/{maxRetries}): {timeErr}. Retrying in {retryDelay}s…");
                yield return new WaitForSeconds(retryDelay);
                fetchCoroutine = StartCoroutine(FetchAndSchedule(attempt + 1, mustBeNewRound));
            }
            else
            {
                Debug.LogError("[RM] Time API failed after max retries. Giving up.");
            }
            yield break;
        }

        // 3. Parse ──────────────────────────────────────────────────────────────
        if (!TryParseIST(timeResp.ist, out DateTime serverNow))
        {
            Debug.LogError($"[RM] Cannot parse server time: '{timeResp.ist}'");
            yield break;
        }

        if (timeResp.round_started == null)
        {
            Debug.LogWarning("[RM] No round_started. Retrying…");
            yield return new WaitForSeconds(retryDelay);
            fetchCoroutine = StartCoroutine(FetchAndSchedule(0, mustBeNewRound));
            yield break;
        }

        if (!TryParseIST(timeResp.round_started.start_time_ist, out DateTime roundStartTime))
        {
            Debug.LogError($"[RM] Cannot parse round start: '{timeResp.round_started.start_time_ist}'");
            yield break;
        }

        string incoming = timeResp.round_started.round_id;

        // 4. Guard: server hasn't flipped the round yet ─────────────────────────
        // If we just fired game_end and fetch too quickly, the server still returns
        // the same round_id. Retry with backoff until a genuinely new round appears.
        if (mustBeNewRound && incoming == currentRoundId)
        {
            Debug.LogWarning($"[RM] Server still on old round {incoming}. Waiting {retryDelay}s…");
            yield return new WaitForSeconds(retryDelay);
            fetchCoroutine = StartCoroutine(FetchAndSchedule(0, mustBeNewRound: true));
            yield break;
        }

        // 5. New round vs re-sync ───────────────────────────────────────────────
        if (incoming != currentRoundId)
        {
            Debug.Log($"[RM] New round: {incoming} (was {currentRoundId})");
            currentRoundId = incoming;
            ResetEventFlags();
            CancelEventCoroutines();
            api.InvalidateCaches();
        }

        // 6. Elapsed (with half-RTT latency correction) ─────────────────────────
        double elapsed = (serverNow - roundStartTime).TotalSeconds + halfRtt;
        if (elapsed < 0) elapsed = 0;

        Debug.Log($"[RM] elapsed={elapsed:F2}s halfRtt={halfRtt * 1000:F0}ms round={currentRoundId}");

        // 7. Late join PAST round end ───────────────────────────────────────────
        // e.g. player joins at 55 s in a 60 s round where RoundEndTime = 55 s.
        // Nothing to schedule — just fire game_end, keep the timer ticking during
        // the gap, then fetch the next round after the server flip buffer.
        if (elapsed >= settings.RoundEndTime)
        {
            Debug.Log($"[RM] Late join past round end ({elapsed:F1}s >= {settings.RoundEndTime}s).");

            roundStartUnityTime = Time.time - (float)elapsed;

            // Fire game_end once (guard handles re-sync double-fire)
            if (!firedGameEnd)
            {
                firedGameEnd = true;
                FireGameEndPayload();
            }

            // Keep timer running so UI shows elapsed time during the inter-round gap
            RestartTimerLoop();

            // Wait for server flip then fetch next round
            ScheduleNextRoundFetch();
            yield break;
        }

        // 8. Normal path ────────────────────────────────────────────────────────
        roundStartUnityTime = Time.time - (float)elapsed;

        // Authoritative snapshot — lets GameController snap camera/dice/UI immediately
        api.FireGameState(new GameApiClient.GameState
        {
            type     = "game_state",
            round_id = currentRoundId,
            status   = GetStatusForElapsed((float)elapsed),
            timer    = Mathf.Min((int)elapsed, settings.TotalRoundDuration)
        });

        RestartTimerLoop();
        ScheduleEvents((float)elapsed);
        RestartResyncLoop();
    }

    // ── Timer ──────────────────────────────────────────────────────────────────

    private void RestartTimerLoop()
    {
        StopSafe(ref timerCoroutine);
        timerCoroutine = StartCoroutine(TimerLoop());
    }

    private IEnumerator TimerLoop()
    {
        while (true)
        {
            yield return new WaitForSeconds(1f);

            float localElapsed = Time.time - roundStartUnityTime;
            api.FireTimerUpdate(new GameApiClient.TimerUpdate
            {
                type     = "timer",
                timer    = Mathf.Min((int)localElapsed, settings.TotalRoundDuration),
                status   = GetStatusForElapsed(localElapsed),
                round_id = currentRoundId
            });
        }
    }

    // ── Resync ─────────────────────────────────────────────────────────────────

    private void RestartResyncLoop()
    {
        StopSafe(ref resyncCoroutine);
        if (resyncIntervalSeconds > 0f)
            resyncCoroutine = StartCoroutine(ResyncLoop());
    }

    private IEnumerator ResyncLoop()
    {
        yield return new WaitForSeconds(resyncIntervalSeconds);
        Debug.Log("[RM] Periodic re-sync…");
        // mustBeNewRound=false: we expect the same round, just correcting drift
        fetchCoroutine = StartCoroutine(FetchAndSchedule(0, mustBeNewRound: false));
    }

    // ── Event Scheduling ───────────────────────────────────────────────────────

    private void ScheduleEvents(float elapsedSeconds)
    {
        if (!firedGameStart)
            Schedule(ref evtGameStart,    0f,                         elapsedSeconds, FireGameStartEvent);

        if (!firedDiceRollWarn)
            Schedule(ref evtDiceRollWarn, settings.DiceRollTime,      elapsedSeconds, FireDiceRollWarnEvent);

        if (!firedDiceResult)
            Schedule(ref evtDiceResult,   settings.DiceResultTime,    elapsedSeconds, FireDiceResultEvent);

        if (!firedResultAnn)
            Schedule(ref evtResultAnn,    settings.ResultAnnounceTime, elapsedSeconds, FireResultAnnEvent);

        if (!firedGameEnd)
            Schedule(ref evtGameEnd,      settings.RoundEndTime,      elapsedSeconds, FireGameEndEvent);
    }

    /// <summary>
    /// Schedules action in (eventTime - elapsed) seconds.
    /// Events clearly in the past (delay &lt; -0.5 s) are marked fired and skipped.
    /// Cancels any previous coroutine in the slot so re-sync doesn't double-schedule.
    /// </summary>
    private void Schedule(ref Coroutine slot, float eventTime, float elapsed, Action action)
    {
        float delay = eventTime - elapsed;

        if (delay < -0.5f)
        {
            MarkFired(action);
            Debug.Log($"[RM] Skip past event t={eventTime}s elapsed={elapsed:F1}s");
            return;
        }

        StopSafe(ref slot);
        slot = StartCoroutine(RunAfter(Mathf.Max(0f, delay), action));
    }

    private IEnumerator RunAfter(float delay, Action action)
    {
        if (delay > 0f) yield return new WaitForSeconds(delay);
        action?.Invoke();
    }

    // ── Event Firers ───────────────────────────────────────────────────────────

    private void FireGameStartEvent()
    {
        if (firedGameStart) return;
        firedGameStart = true;
        Debug.Log($"[RM] → game_start {currentRoundId}");

        api.FireRoundUpdate(new GameApiClient.RoundUpdate
            { type = "round_update", round_id = currentRoundId, status = "BETTING" });

        api.FireGameStart(new GameApiClient.GameStart
            { type = "game_start", round_id = currentRoundId, status = "BETTING", timer = 0 });
    }

    private void FireDiceRollWarnEvent()
    {
        if (firedDiceRollWarn) return;
        firedDiceRollWarn = true;
        Debug.Log($"[RM] → dice_roll_warn {currentRoundId}");

        api.FireDiceRollWarn(new GameApiClient.DiceRollWarning
        {
            type           = "dice_roll",
            round_id       = currentRoundId,
            status         = "BETTING_CLOSING",
            timer          = Mathf.Min((int)(Time.time - roundStartUnityTime), settings.TotalRoundDuration),
            dice_roll_time = settings.DiceRollTime
        });
    }

    private void FireDiceResultEvent()
    {
        if (firedDiceResult) return;
        firedDiceResult = true;
        int tv = Mathf.Min((int)(Time.time - roundStartUnityTime), settings.TotalRoundDuration);
        Debug.Log($"[RM] → dice_result {currentRoundId}");

        api.GetCurrentRoundResult((ok, result, err) =>
        {
            if (!ok || result?.Round == null)
            {
                Debug.LogWarning($"[RM] dice_result fetch failed: {err}");
                api.FireDiceResult(new GameApiClient.DiceResult
                    { type = "dice_result", round_id = currentRoundId, status = "ROLLING",
                      timer = tv, result = string.Empty, dice_values = new int[0] });
                return;
            }
            var r = result.Round;
            api.FireDiceResult(new GameApiClient.DiceResult
            {
                type        = "dice_result", round_id = currentRoundId, status = "ROLLING",
                timer       = tv, result = r.DiceResult,
                dice_values = new[] { r.Dice1, r.Dice2, r.Dice3, r.Dice4, r.Dice5, r.Dice6 }
            });
        });
    }

    private void FireResultAnnEvent()
    {
        if (firedResultAnn) return;
        firedResultAnn = true;
        int tv = Mathf.Min((int)(Time.time - roundStartUnityTime), settings.TotalRoundDuration);
        Debug.Log($"[RM] → result_ann {currentRoundId}");

        api.GetCurrentRoundResult((ok, result, err) =>
        {
            if (!ok || result?.Round == null)
            {
                Debug.LogWarning($"[RM] result_ann fetch failed: {err}");
                api.FireResultAnn(new GameApiClient.ResultAnnouncement
                    { type = "result", round_id = currentRoundId, status = "RESULT",
                      timer = tv, dice_result = 0, dice_values = new int[0] });
                return;
            }
            var r = result.Round;
            int.TryParse(r.DiceResult, out int di);
            api.FireResultAnn(new GameApiClient.ResultAnnouncement
            {
                type        = "result", round_id = currentRoundId, status = "RESULT",
                timer       = tv, dice_result = di,
                dice_values = new[] { r.Dice1, r.Dice2, r.Dice3, r.Dice4, r.Dice5, r.Dice6 },
                raw         = JObject.FromObject(r)
            });
        });
    }

    private void FireGameEndEvent()
    {
        if (firedGameEnd) return;
        firedGameEnd = true;
        Debug.Log($"[RM] → game_end {currentRoundId}");
        FireGameEndPayload();
        ScheduleNextRoundFetch();
    }

    /// <summary>Shared payload builder for both normal and late-join game_end paths.</summary>
    private void FireGameEndPayload()
    {
        api.GetLastRoundResult((ok, last, err) =>
        {
            var ge = new GameApiClient.GameEnd
            {
                Type = "game_end", RoundId = currentRoundId,
                Status = "ENDED", Timer = settings.TotalRoundDuration, IsRolling = false
            };
            if (ok && last != null)
            {
                ge.DiceResult = last.diceResult;
                ge.Dice1 = last.dice1; ge.Dice2 = last.dice2; ge.Dice3 = last.dice3;
                ge.Dice4 = last.dice4; ge.Dice5 = last.dice5; ge.Dice6 = last.dice6;
            }
            api.FireGameEnd(ge);
        });
    }

    // ── Next Round ─────────────────────────────────────────────────────────────

    /// <summary>
    /// Waits postRoundBuffer seconds (letting the server flip the round),
    /// then fetches with mustBeNewRound=true so we retry if the old round_id is still live.
    /// </summary>
    private void ScheduleNextRoundFetch()
    {
        StopSafe(ref nextRoundCoroutine);
        nextRoundCoroutine = StartCoroutine(NextRoundAfterBuffer());
    }

    private IEnumerator NextRoundAfterBuffer()
    {
        Debug.Log($"[RM] Waiting {postRoundBuffer}s for server round flip…");
        yield return new WaitForSeconds(postRoundBuffer);
        StopSafe(ref resyncCoroutine); // will be restarted by FetchAndSchedule for the new round
        FireGameStartEvent();
        MarkFired(FireGameStartEvent);
        fetchCoroutine = StartCoroutine(FetchAndSchedule(0, mustBeNewRound: true));
    }

    // ── Coroutine Management ───────────────────────────────────────────────────

    private void StopEverything()
    {
        StopSafe(ref timerCoroutine);
        StopSafe(ref resyncCoroutine);
        StopSafe(ref fetchCoroutine);
        StopSafe(ref nextRoundCoroutine);
        CancelEventCoroutines();
    }

    private void CancelEventCoroutines()
    {
        StopSafe(ref evtGameStart);
        StopSafe(ref evtDiceRollWarn);
        StopSafe(ref evtDiceResult);
        StopSafe(ref evtResultAnn);
        StopSafe(ref evtGameEnd);
    }

    private void StopSafe(ref Coroutine c)
    {
        if (c == null) return;
        try { StopCoroutine(c); } catch { }
        c = null;
    }

    // ── Misc Helpers ───────────────────────────────────────────────────────────

    private void ResetEventFlags()
    {
        firedGameStart = firedDiceRollWarn = firedDiceResult =
        firedResultAnn = firedGameEnd = false;
    }

    private void MarkFired(Action action)
    {
        if (action == (Action)FireGameStartEvent)    { firedGameStart    = true; return; }
        if (action == (Action)FireDiceRollWarnEvent) { firedDiceRollWarn = true; return; }
        if (action == (Action)FireDiceResultEvent)   { firedDiceResult   = true; return; }
        if (action == (Action)FireResultAnnEvent)    { firedResultAnn    = true; return; }
        if (action == (Action)FireGameEndEvent)      { firedGameEnd      = true; return; }
    }

    private string GetStatusForElapsed(float e)
    {
        if (e < settings.BettingCloseTime)   return "BETTING";
        if (e < settings.DiceRollTime)       return "CLOSED";
        if (e < settings.DiceResultTime)     return "ROLLING";
        if (e < settings.RoundEndTime)       return "RESULT";
        return "COMPLETED";
    }

    private static bool TryParseIST(string raw, out DateTime result)
    {
        if (string.IsNullOrEmpty(raw)) { result = default; return false; }
        return DateTime.TryParseExact(raw,
            new[] { "yyyy-MM-ddTHH:mm:ss.fff", "yyyy-MM-ddTHH:mm:ss.ff",
                    "yyyy-MM-ddTHH:mm:ss.f",   "yyyy-MM-ddTHH:mm:ss" },
            CultureInfo.InvariantCulture, DateTimeStyles.None, out result);
    }

    private GameApiClient.GameSettings BuildFallbackSettings() =>
        new GameApiClient.GameSettings
        {
            TotalRoundDuration      = fallbackTotalRoundDuration,
            BettingDuration         = fallbackBettingCloseTime,
            BettingCloseTime        = fallbackBettingCloseTime,
            DiceRollTime            = fallbackDiceRollTime,
            DiceResultTime          = fallbackDiceResultTime,
            ResultAnnounceTime      = fallbackResultAnnounceTime,
            RoundEndTime            = fallbackRoundEndTime,
            ResultSelectionDuration = fallbackRoundEndTime - fallbackDiceResultTime,
            ResultDisplayDuration   = fallbackRoundEndTime - fallbackResultAnnounceTime,
        };
}
