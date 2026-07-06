using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using static GameApiClient;

public enum RoundStatus
{
    NONE,
    WAITING,
    BETTING,
    CLOSED,
    ROLLING,
    RESULT,
    COMPLETED
}

public class GameController : MonoBehaviour
{
    [Header("References")]
    public DiceCameraController diceCamController;

    private GameApiClient apiClient;
    private RoundManager roundManager;   // ← replaces WebSocket reconnect
    private GameplayUIManager gameplayui;
    private DiceAndBox diceBox;

    private RoundStatus currentStatus;
    private GameApiClient.RoundData currentRoundData;

    // Cache last dice result (late join support)
    private int[] lastDiceValues;

    private List<GameApiClient.MyBet> currentRoundBets;
    private int timer = 0;

    // True only for the very first Unity focus event after the scene loads.
    // Instance field (not static) so each scene reload correctly counts as a fresh join.
    private bool isFreshLaunched = true;

    // Tracks which round we last ran the full join/resume setup for.
    // Prevents OnPlayerJoinOrResume from being called twice for the same round
    // if RoundManager re-syncs mid-round (game_state fires again with same round_id).
    private string lastHandledRoundId = null;

    // reconnect coroutine & config (now used for RoundManager re-sync, not WebSocket)
    private Coroutine reconnectRoutine;
    private int reconnectAttempt = 0;
    private const int MAX_RECONNECT_ATTEMPTS = -1; // -1 = infinite
    private const float RECONNECT_BASE_DELAY = 1.0f;
    private const float RECONNECT_MAX_DELAY = 15f;

    private void Start()
    {
        apiClient = GameManager.Instance.ApiClient;
        roundManager = apiClient.GetComponent<RoundManager>();

        gameplayui = UIManager.Instance.gameplayUIManager;
        diceBox = FindFirstObjectByType<DiceAndBox>();

        // subscribe to dice playback complete so SFX plays only when dice land
        if (diceBox != null && diceBox.diceRoller != null)
            diceBox.diceRoller.OnPlaybackComplete += HandleDicePlaybackComplete;

        SubscribeEvents();
    }

    private void OnDestroy()
    {
        if (diceBox != null && diceBox.diceRoller != null)
            diceBox.diceRoller.OnPlaybackComplete -= HandleDicePlaybackComplete;

        UnsubscribeEvents();
    }

    /// <summary>
    /// Fires when the app window gains focus.
    /// Covers: alt-tab back, browser recent-tab restore, Android/iOS app resume from recents.
    ///
    /// We do NOT call OnPlayerJoinOrResume(timer) directly here because <c>timer</c> is the
    /// last cached value and will be stale if the player was away (e.g. away for 30 s mid-round).
    /// Instead we kick RoundManager.StartRound() via TryResumeReconnect(), which re-fetches the
    /// time API and fires HandleGameState with the real server-authoritative elapsed time —
    /// HandleGameState then calls OnPlayerJoinOrResume with the correct value.
    /// </summary>
    private void OnApplicationFocus(bool focus)
    {
        if (!focus) return;

        if (isFreshLaunched)
        {
            // Very first focus after scene load — Unity fires this automatically before the
            // player has done anything. Skip re-sync here; HandleLoginSuccess will start it.
            isFreshLaunched = false;
            return;
        }

        // Player returned to the game (recent tab, alt-tab, app switcher, etc.)
        // Re-sync round timing; OnPlayerJoinOrResume fires inside HandleGameState.
        TryResumeReconnect();
    }

    /// <summary>
    /// Fires on mobile when the app goes to / comes back from the background.
    /// On resume we re-sync for the same reason as OnApplicationFocus.
    /// </summary>
    private void OnApplicationPause(bool paused)
    {
        if (!paused)
            TryResumeReconnect();
    }

    #region Event Subscriptions
    // All game-flow events now come from GameApiClient event relays,
    // which are fired by RoundManager — no WebSocket required.

    private void SubscribeEvents()
    {
        apiClient.OnGameState += HandleGameState;
        apiClient.OnTimerUpdate += HandleTimerUpdate;
        apiClient.OnGameStart += HandleGameStart;
        apiClient.OnDiceRollWarn += HandleDiceRollWarn;
        apiClient.OnDiceResult += HandleDiceResult;
        apiClient.OnResultAnn += HandleResultAnn;
        apiClient.OnGameEnd += HandleGameEnd;
        apiClient.OnRoundUpdate += HandleRoundUpdate;

        apiClient.OnShowHidePopup += UIManager.Instance.ShowNoInternetPopup;
        apiClient.OnLoginSuccess += HandleLoginSuccess;

        // Connection recovery events: now driven by RoundManager sync status
        apiClient.OnReconnect += HandleApiReconnected;
    }

    private void UnsubscribeEvents()
    {
        apiClient.OnGameState -= HandleGameState;
        apiClient.OnTimerUpdate -= HandleTimerUpdate;
        apiClient.OnGameStart -= HandleGameStart;
        apiClient.OnDiceRollWarn -= HandleDiceRollWarn;
        apiClient.OnDiceResult -= HandleDiceResult;
        apiClient.OnResultAnn -= HandleResultAnn;
        apiClient.OnGameEnd -= HandleGameEnd;
        apiClient.OnRoundUpdate -= HandleRoundUpdate;

        apiClient.OnShowHidePopup -= UIManager.Instance.ShowNoInternetPopup;
        apiClient.OnLoginSuccess -= HandleLoginSuccess;

        apiClient.OnReconnect -= HandleApiReconnected;
    }

    #endregion

    #region UI Updates

    public void UpdateResult()
    {
        if (apiClient == null || gameplayui == null)
        {
            Debug.LogWarning("UpdateResult aborted: apiClient or gameplayui is null.");
            return;
        }

        void CallAllApis()
        {
            // 1️⃣ Winning Frequency
            apiClient.GetWinningFrequency((okFreq, winResp, errFreq) =>
            {
                if (okFreq && winResp?.WinningNumbers != null)
                {
                    try
                    {
                        var winningsFrequency = new List<Tuple<int, int>>();
                        foreach (var win in winResp.WinningNumbers)
                            winningsFrequency.Add(new Tuple<int, int>(win.Number, win.Frequency));

                        gameplayui.UpdateFrequencyOfNum(winningsFrequency);
                        diceBox.diceRoller.SetGlowForWinDice(winningsFrequency);
                    }
                    catch (Exception ex) { Debug.LogWarning("WinningFrequency error: " + ex); }
                }
                else
                {
                    Debug.LogWarning("GetWinningFrequency failed: " + errFreq);
                }
            });

            // 2️⃣ Round Result
            if (!string.IsNullOrEmpty(currentRoundData?.RoundId))
            {
                apiClient.GetCurrentRoundResult((okRound, roundResp, errRound) =>
                {
                    if (okRound && roundResp != null)
                    {
                        Debug.Log("Net Result : " + roundResp.Summary.NetResult);
                        gameplayui.UpdateWinLoseText(roundResp.Summary.NetResult);
                    }
                    else
                    {
                        Debug.LogWarning("GetRoundResult failed: " + errRound);
                    }
                });
            }

            // 3️⃣ Last Round Result
            apiClient.GetLastRoundResult((okLast, last, errLast) =>
            {
                if (!okLast || last == null)
                {
                    Debug.LogWarning("GetLastRoundResult failed: " + errLast);
                    return;
                }

                CacheLastResult(last);

                bool afterDiceResult =
                    timer > (GameManager.Instance.GameSettings?.DiceResultTime ?? 0);

                gameplayui.UpdateRoundResults(lastDiceValues, afterDiceResult);
            });
        }

        // Ensure we have current round data before calling APIs
        if (currentRoundData == null || string.IsNullOrEmpty(currentRoundData.RoundId))
        {
            apiClient.GetCurrentRound((okCurrent, data, errCurrent) =>
            {
                if (okCurrent && data != null)
                {
                    currentRoundData = data;
                    try { UpdateRoundStatusFromString(data.Status); } catch { }
                }
                CallAllApis();
            });
        }
        else
        {
            CallAllApis();
        }
    }

    #endregion

    #region Initial Fetch

    /// <summary>
    /// Called on login success. Fetches the current round from REST then
    /// starts RoundManager so local event scheduling begins immediately.
    /// </summary>
    private void HandleLoginSuccess()
    {
        lastHandledRoundId = null; // ensure first HandleGameState always runs full join path
        FetchCurrentRound();

        if (roundManager != null)
            roundManager.StartRound();
        else
            Debug.LogWarning("[GameController] RoundManager not found on ApiClient GameObject.");
    }

    private void FetchCurrentRound()
    {
        apiClient.GetCurrentRound((ok, data, err) =>
        {
            if (!ok || data == null) return;

            currentRoundData = data;
            UpdateRoundStatusFromString(data.Status);
        });
    }

    #endregion

    #region Bet Management

    public void PlaceBet(int diceNo, float amount, Action<bool> onResult)
    {
        float walletAmount = GameManager.Instance.WalletAmount;

        if (amount > walletAmount)
        {
            onResult?.Invoke(false);
            return;
        }

        apiClient.PlaceBet(diceNo, amount, (ok, betResp, err) =>
        {
            if (!ok) { onResult?.Invoke(false); return; }
            GameManager.Instance.RefreshWallet();
            onResult?.Invoke(true);
        });
    }

    public void RemoveLastBets()
    {
        if (currentRoundBets == null || currentRoundBets.Count <= 0) return;

        apiClient.DeleteBet(currentRoundBets[0].number, (ok, err) =>
        {
            if (ok) GameManager.Instance.RefreshWallet();
        });
    }

    #endregion

    #region Game Flow Handlers
    // These handlers are identical in behaviour to the old WebSocket handlers.
    // The only change is the source: events now fire from RoundManager → GameApiClient.Fire*()
    // instead of arriving over a WebSocket connection.

    /// <summary>
    /// Fired by RoundManager on every StartRound() call (i.e. initial join, focus return,
    /// app resume, internet reconnect). This is the single authoritative entry point for
    /// the join/resume path — it always has the server-correct elapsed timer from the
    /// time API, so camera state, dice reconstruction, and UI are always accurate.
    /// </summary>
    private void HandleGameState(GameApiClient.GameState gs)
    {
        if (gs == null) return;

        UpdateRoundStatusFromString(gs.status);

        timer = gs.timer; // keep local timer in sync with server value

        bool isNewRound = gs.round_id != lastHandledRoundId;
        lastHandledRoundId = gs.round_id;

        // Always run the full join/resume setup:
        //  • New round:   set up camera, dice, UI from scratch.
        //  • Same round:  player returned mid-round (recent tab / app resume) — restore
        //                 correct state using the real server-elapsed time, not stale cache.
        OnPlayerJoinOrResume(gs.timer, gs.round_id);
    }

    private void HandleTimerUpdate(GameApiClient.TimerUpdate tu)
    {
        if (tu == null) return;

        timer = tu.timer;
        UpdateRoundStatusFromString(tu.status);
        gameplayui.UpdateTimer(timer);
    }

    private void HandleGameStart(GameApiClient.GameStart gs)
    {
        SafePlaySfx(SfxType.PlaceBetSfx, loop: false);

        try { if (diceBox != null) diceBox.SetBoxToTableImmediate(); } catch { }

        try
        {
            if (diceCamController != null)
                diceCamController.MoveCamera(DiceCameraState.BettingView, false);
        }
        catch { }

        apiClient.GetLastRoundResult((ok, r, e) =>
        {
            if (!ok || r == null) return;
            CacheLastResult(r);
            gameplayui.UpdateRoundResults(lastDiceValues, false);
        });
    }

    private void HandleDiceRollWarn(GameApiClient.DiceRollWarning warn)
    {
        if (diceCamController != null)
            diceCamController.MoveCamera(DiceCameraState.DiceView, true);

        if (diceBox != null)
            diceBox.ShakeDiceIfNeeded();

        SafePlaySfx(SfxType.ShakingSfx, loop: true);
        try { AudioManager.Instance?.PauseBackgroundMusic(); } catch { }
    }

    private void HandleDiceResult(GameApiClient.DiceResult dr)
    {
        if (dr?.dice_values == null) return;

        lastDiceValues = dr.dice_values;

        if (diceCamController != null)
            diceCamController.MoveCamera(DiceCameraState.DiceView, true);

        if (diceBox != null)
            diceBox.ThrowDiceIfNeeded(lastDiceValues, dr.round_id);

        SafeStopLoopingSfx();
        try { AudioManager.Instance?.PauseBackgroundMusic(); } catch { }
    }

    // Called when physics playback finished and dice are settled on table
    private void HandleDicePlaybackComplete(int[] results)
    {
        try { AudioManager.Instance?.PlayBackgroundMusic(); }
        catch (Exception ex) { Debug.LogWarning("HandleDicePlaybackComplete error: " + ex.Message); }
    }

    private void HandleResultAnn(GameApiClient.ResultAnnouncement res)
    {
        GameManager.Instance.RefreshWallet();
    }

    private void HandleGameEnd(GameApiClient.GameEnd ge)
    {
        if (diceCamController != null)
            diceCamController.MoveCamera(DiceCameraState.BettingView, true);

        if (diceBox != null)
            diceBox.ResetDice();

        lastDiceValues = null;

        SafeStopLoopingSfx();
        try { AudioManager.Instance?.PlayBackgroundMusic(); } catch { }

        GameManager.Instance.RefreshWallet();
        gameplayui.ResetUI();
    }

    private void HandleRoundUpdate(GameApiClient.RoundUpdate ru)
    {
        lastHandledRoundId = null; // next game_state for the new round runs full join path
        if (diceBox != null)
            diceBox.ResetDice();
    }

    #endregion

    #region Reconnect Handling
    // WebSocket reconnect replaced with RoundManager.StartRound() re-sync.
    // The reconnect UI panel and backoff logic are preserved unchanged.

    private void HandleApiReconnected()
    {
        // Internet came back → hide popup and re-sync round timing
        try
        {
            StopReconnectRoutine();
            gameplayui?.ShowReconnectPanel(false);

            if (roundManager != null)
                roundManager.StartRound();
        }
        catch { }
    }

    private void TryResumeReconnect()
    {
        if (apiClient == null) return;

        // Show reconnect panel and trigger a RoundManager re-sync if not already running
        bool internetOk = Application.internetReachability != NetworkReachability.NotReachable;
        if (!internetOk)
        {
            gameplayui?.ShowReconnectPanel(true);
            StartReconnectRoutine();
        }
        else
        {
            // Internet is fine — just kick a silent re-sync so timer stays accurate after resume
            gameplayui.ResetUI();
            if (roundManager != null)
                roundManager.StartRound();
        }
    }

    private void StartReconnectRoutine()
    {
        if (reconnectRoutine != null) return;
        reconnectAttempt = 0;
        reconnectRoutine = StartCoroutine(ReconnectCoroutine());
    }

    private void StopReconnectRoutine()
    {
        if (reconnectRoutine == null) return;
        try { StopCoroutine(reconnectRoutine); } catch { }
        reconnectRoutine = null;
    }

    /// <summary>
    /// Polls for internet connectivity, then kicks RoundManager.StartRound() on recovery.
    /// Replaces the old WebSocket health-check + apiClient.ReconnectWebSocket() calls.
    /// </summary>
    private IEnumerator ReconnectCoroutine()
    {
        while (true)
        {
            bool internetOk = Application.internetReachability != NetworkReachability.NotReachable;

            if (internetOk)
            {
                // Internet restored — re-sync round and clear UI
                HandleApiReconnected();
                yield break;
            }

            reconnectAttempt++;

            // Exponential backoff
            float delay = Mathf.Min(
                RECONNECT_BASE_DELAY * Mathf.Pow(2, reconnectAttempt - 1),
                RECONNECT_MAX_DELAY);
            yield return new WaitForSeconds(delay);

            if (MAX_RECONNECT_ATTEMPTS > 0 && reconnectAttempt >= MAX_RECONNECT_ATTEMPTS)
                break;
        }

        reconnectRoutine = null;
    }

    #endregion

    #region Audio Helpers

    private void SafePlaySfx(SfxType sfx, bool loop = false)
    {
        try { if (AudioManager.Instance != null) AudioManager.Instance.PlaySfx(sfx, loop); }
        catch { }
    }

    private void SafeStopLoopingSfx()
    {
        try { if (AudioManager.Instance != null) AudioManager.Instance.StopSfx(); }
        catch { }
    }

    #endregion

    #region Helpers

    private void CacheLastResult(GameApiClient.LastRoundResult r)
    {
        lastDiceValues = new[] { r.dice1, r.dice2, r.dice3, r.dice4, r.dice5, r.dice6 };
    }

    private void UpdateRoundStatusFromString(string status)
    {
        if (!Enum.TryParse(status, true, out RoundStatus rs)) return;
        if (currentStatus == rs) return;

        if (rs == RoundStatus.CLOSED)
            SafePlaySfx(SfxType.CloseBetSfx, loop: false);

        currentStatus = rs;
        apiClient.GetBetAmountSummary((success, summary, err) =>
        {
            BetAmountSummary[] summaryToSend;

            if (success && summary != null && summary.Count > 0)
            {
                summaryToSend = summary.ToArray();
            }
            else
            {
                summaryToSend = new BetAmountSummary[6];
                for (int i = 0; i < 6; i++)
                    summaryToSend[i] = new BetAmountSummary { number = i + 1, amount = 0 };
            }

            gameplayui.UpdateGameUI(
                currentStatus,
                GameManager.Instance.WalletAmount
            );
        });
    }

    private void OnPlayerJoinOrResume(int currentTime, string roundid)
    {
        if (GameManager.Instance.GameSettings == null) return;

        FetchCurrentRound();

        int diceRollTime = GameManager.Instance.GameSettings.DiceRollTime;
        int diceResultTime = GameManager.Instance.GameSettings.DiceResultTime;
        int roundEndTime = GameManager.Instance.GameSettings.RoundEndTime;
        int totalDuration = GameManager.Instance.GameSettings.TotalRoundDuration;

        // ── Derive phases from authoritative elapsed time ──────────────────────
        bool inDiceRollPhase = currentTime >= diceRollTime && currentTime < diceResultTime;
        bool afterDiceResult = currentTime >= diceResultTime && currentTime < roundEndTime;
        bool roundEnded = currentTime >= roundEndTime;

        // ── Camera ─────────────────────────────────────────────────────────────
        if (diceCamController != null)
        {
            bool showDiceView = inDiceRollPhase || afterDiceResult;
            diceCamController.MoveCamera(
                showDiceView ? DiceCameraState.DiceView : DiceCameraState.BettingView, false);
        }

        // ── DiceBox state ──────────────────────────────────────────────────────
        // We always need the box in the right position before touching dice.
        if (diceBox != null)
        {
            if (roundEnded || (!inDiceRollPhase && !afterDiceResult))
            {
                // Betting phase or round over: box on table, no dice
                diceBox.SetBoxToTableImmediate();
                diceBox.ResetDice();
            }
            else if (inDiceRollPhase)
            {
                // Box should be shaking — start it regardless of prior state
                diceBox.ResetDice();            // clear any stale dice first
                diceBox.ShakeDiceIfNeeded();
            }
            // afterDiceResult: dice must be statically on the table (handled below after API fetch)
        }

        // ── Audio ──────────────────────────────────────────────────────────────
        if (inDiceRollPhase)
        {
            SafePlaySfx(SfxType.ShakingSfx, loop: true);
            try { AudioManager.Instance?.PauseBackgroundMusic(); } catch { }
        }
        else
        {
            SafeStopLoopingSfx();
            if (currentStatus == RoundStatus.CLOSED) AudioManager.Instance?.PlaySfx(SfxType.CloseBetSfx, loop: false);
            else if (currentStatus == RoundStatus.BETTING) AudioManager.Instance?.PlaySfx(SfxType.PlaceBetSfx, loop: false);
        }

        // ── Data fetch → UI + dice reconstruction ─────────────────────────────
        apiClient.GetLastRoundResult((ok, r, e) =>
        {
            if (!ok || r == null) return;
            CacheLastResult(r);

            apiClient.GetBetAmountSummary((success, Summary, err) =>
            {
                BetAmountSummary[] summaryToSend;
                if (success && Summary != null && Summary.Count > 0)
                {
                    summaryToSend = Summary.ToArray();
                }
                else
                {
                    summaryToSend = new BetAmountSummary[6];
                    for (int i = 0; i < 6; i++)
                        summaryToSend[i] = new BetAmountSummary { number = i + 1, amount = 0 };
                }

                apiClient.GetMaxBetAmountSummary((done, maxBetResponse, maxErr) =>
                {
                    int maxBet = (done && maxBetResponse != null) ? maxBetResponse.max_bet_amount : -1;

                    gameplayui.SetGameUI(currentStatus, GameManager.Instance.WalletAmount,
                        lastDiceValues, afterDiceResult, summaryToSend, maxBet);

                    // ── Dice reconstruction on resume ──────────────────────────
                    // afterDiceResult: dice must be displayed statically on the table.
                    // We always reset + respawn here so that:
                    //   • Player left during dice throw → physics dice still flying/settled
                    //     in wrong positions → reset and place them correctly.
                    //   • Player left after dice settled → dice already spawned correctly
                    //     but we reset anyway to guarantee a clean snap-to-position.
                    // ResetDice() was already called in the box-state block above for
                    // betting/rolling, so this only runs for the result phase.
                    if (afterDiceResult && diceBox != null && lastDiceValues != null && !diceBox.diceRoller.IsDiceAlreadySpawnedForCurrentRound(roundid))
                    {
                        diceBox.ResetDice();
                        // Spawn static (non-physics) dice at correct face values
                        diceBox.SpawnDices(lastDiceValues, roundid);

                        // Glow + frequency UI
                        apiClient.GetWinningFrequency((okFreq, winResp, errFreq) =>
                        {
                            if (okFreq && winResp?.WinningNumbers != null)
                            {
                                try
                                {
                                    var winningsFrequency = new List<Tuple<int, int>>();
                                    foreach (var win in winResp.WinningNumbers)
                                        winningsFrequency.Add(new Tuple<int, int>(win.Number, win.Frequency));

                                    gameplayui.UpdateFrequencyOfNum(winningsFrequency);
                                    diceBox.diceRoller.SetGlowForWinDice(winningsFrequency);
                                }
                                catch (Exception ex) { Debug.LogWarning("WinningFrequency error: " + ex); }
                            }
                            else
                            {
                                Debug.LogWarning("GetWinningFrequency failed: " + errFreq);
                            }
                        });
                    }
                });
            });
        });
    }

    #endregion
}