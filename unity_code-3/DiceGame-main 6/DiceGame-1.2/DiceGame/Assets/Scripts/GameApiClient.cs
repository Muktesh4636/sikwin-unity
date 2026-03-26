using System;
using System.Collections;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using UnityEngine;
using UnityEngine.Networking;

/// <summary>
/// GameApiClient - Manages REST API calls only.
/// WebSocket removed; all real-time game events are now driven locally by RoundManager.
/// </summary>
public class GameApiClient : MonoBehaviour
{
    [Header("API Config")]
    [Tooltip("Base HTTP URL (no trailing slash)")]
    [SerializeField] private string baseUrl = "http://159.198.46.36:8232";

    [Tooltip("Server time API URL")]
    [SerializeField] private string timeApiUrl = "https://gunduata.club/api/time/";

    // Auth tokens (kept in memory; persist externally if desired)
    private string accessToken = null;
    private string refreshToken = null;

    // Main-thread queue for background callbacks
    private readonly ConcurrentQueue<Action> mainThreadQueue = new ConcurrentQueue<Action>();

    [Header("Connection Monitoring")]
    [SerializeField] private float connectionCheckInterval = 3f;

    private bool isPlayerLoggedIn = false;
    private bool popupVisible = false;
    private Coroutine connectionMonitorRoutine;

    // ── Core lifecycle events ──────────────────────────────────────────────────
    public event Action OnLoginSuccess;
    public event Action<string> OnError;
    public event Action OnReconnect;

    // ── Popup visibility event ─────────────────────────────────────────────────
    public event Action<bool> OnShowHidePopup;

    // ── Game-flow events (fired by RoundManager, kept here as public API so
    //    existing subscriber code needs no changes) ──────────────────────────────
    public event Action<GameState>         OnGameState;
    public event Action<TimerUpdate>       OnTimerUpdate;
    public event Action<GameStart>         OnGameStart;
    public event Action<BettingClosed>     OnBettingClose;
    public event Action<DiceRollWarning>   OnDiceRollWarn;
    public event Action<DiceResult>        OnDiceResult;
    public event Action<ResultAnnouncement>OnResultAnn;
    public event Action<GameEnd>           OnGameEnd;
    public event Action<RoundUpdate>       OnRoundUpdate;

    // ── Internal helpers ───────────────────────────────────────────────────────
    private const string JSON_CONTENT = "application/json";

    // Simple short-lived caches to reduce duplicate network calls
    private RoundData   cachedRoundData;
    private float       cachedRoundTimestamp  = 0f;
    private GameSettings cachedGameSettings;
    private float        cachedSettingsTimestamp = 0f;
    private const float  ROUND_CACHE_TTL    = 1f;  // 1 s cache for current round
    private const float  SETTINGS_CACHE_TTL = 5f;  // 5 s cache for settings

    #region ──── Models ────────────────────────────────────────────────────────

    [Serializable] public class AuthResponse { public string access; public string refresh; }
    [Serializable] public class WalletResponse { public string balance; }

    [Serializable]
    public class RegisterationErrorResponse
    {
        [JsonProperty("username")] public List<string> UsernameErrors { get; set; }
        [JsonProperty("password")] public List<string> PasswordErrors { get; set; }
    }

    [Serializable]
    public class GameSettings
    {
        [JsonProperty("BETTING_DURATION")]        public int BettingDuration        { get; set; }
        [JsonProperty("RESULT_SELECTION_DURATION")]public int ResultSelectionDuration{ get; set; }
        [JsonProperty("RESULT_DISPLAY_DURATION")] public int ResultDisplayDuration  { get; set; }
        [JsonProperty("TOTAL_ROUND_DURATION")]    public int TotalRoundDuration      { get; set; }
        [JsonProperty("DICE_ROLL_TIME")]          public int DiceRollTime            { get; set; }
        [JsonProperty("BETTING_CLOSE_TIME")]      public int BettingCloseTime        { get; set; }
        [JsonProperty("DICE_RESULT_TIME")]        public int DiceResultTime          { get; set; }
        [JsonProperty("RESULT_ANNOUNCE_TIME")]    public int ResultAnnounceTime      { get; set; }
        [JsonProperty("ROUND_END_TIME")]          public int RoundEndTime            { get; set; }
        [JsonProperty("CHIP_VALUES")]             public List<int> ChipValues        { get; set; }
        [JsonProperty("PAYOUT_RATIOS")]           public Dictionary<string, float> PayoutRatios { get; set; }
    }

    /// <summary>Response model for https://gunduata.club/api/time/</summary>
    [Serializable]
    public class ServerTimeResponse
    {
        /// <summary>Current IST datetime string, e.g. "2026-03-20T12:24:45.334"</summary>
        [JsonProperty("ist")]
        public string ist { get; set; }

        [JsonProperty("round_started")]
        public RoundStartedInfo round_started { get; set; }
    }

    [Serializable]
    public class RoundStartedInfo
    {
        [JsonProperty("round_id")]
        public string round_id { get; set; }

        /// <summary>Round start time in IST, e.g. "2026-03-20T12:23:56.478"</summary>
        [JsonProperty("start_time_ist")]
        public string start_time_ist { get; set; }
    }

    [Serializable]
    public class BetAmountSummary
    {
        [JsonProperty("number")] public int number { get; set; }
        [JsonProperty("amount")] public int amount { get; set; }
    }

    [Serializable]
    public class MaxBetResponse
    {
        [JsonProperty("max_bet")] public int max_bet_amount { get; set; }
    }

    public class PaymentMethod
    {
        [JsonProperty("id")]             public int    id             { get; set; }
        [JsonProperty("name")]           public string name           { get; set; }
        [JsonProperty("method_type")]    public PaymentMethodType method_type { get; set; }
        [JsonProperty("account_name")]   public string account_name   { get; set; }
        [JsonProperty("bank_name")]      public string bank_name      { get; set; }
        [JsonProperty("upi_id")]         public string upi_id         { get; set; }
        [JsonProperty("link")]           public string link            { get; set; }
        [JsonProperty("account_number")] public string account_number { get; set; }
        [JsonProperty("ifsc_code")]      public string ifsc_code      { get; set; }
        [JsonProperty("is_active")]      public bool   is_active      { get; set; }
        [JsonProperty("created_at")]     public string created_at     { get; set; }
        [JsonProperty("updated_at")]     public string updated_at     { get; set; }
    }

    [Serializable]
    public class RemoveLastBetResponse
    {
        [JsonProperty("message")]        public string message;
        [JsonProperty("refund_amount")]  public string refund_amount;
        [JsonProperty("bet_number")]     public int    bet_number;
        [JsonProperty("wallet_balance")] public string wallet_balance;
        [JsonProperty("round")]          public RemoveLastBetRound round;
    }

    [Serializable]
    public class RemoveLastBetRound
    {
        [JsonProperty("round_id")]     public string round_id;
        [JsonProperty("total_bets")]   public int    total_bets;
        [JsonProperty("total_amount")] public string total_amount;
    }

    [Serializable]
    public class DepositProofResponse
    {
        [JsonProperty("id")]             public int    id             { get; set; }
        [JsonProperty("user")]           public User   user           { get; set; }
        [JsonProperty("amount")]         public string amount         { get; set; }
        [JsonProperty("status")]         public string status         { get; set; }
        [JsonProperty("screenshot_url")] public string screenshot_url { get; set; }
        [JsonProperty("admin_note")]     public string admin_note     { get; set; }
        [JsonProperty("created_at")]     public string created_at     { get; set; }
        [JsonProperty("updated_at")]     public string updated_at     { get; set; }
    }

    [Serializable]
    public class User
    {
        [JsonProperty("id")]           public int    id           { get; set; }
        [JsonProperty("username")]     public string username     { get; set; }
        [JsonProperty("email")]        public string email        { get; set; }
        [JsonProperty("phone_number")] public string phone_number { get; set; }
        [JsonProperty("date_joined")]  public string date_joined  { get; set; }
        [JsonProperty("is_staff")]     public bool   is_staff     { get; set; }
    }

    [Serializable]
    public class RoundResultResponse
    {
        [JsonProperty("round")]          public RoundInfo    Round          { get; set; }
        [JsonProperty("bets")]           public List<BetInfo> Bets          { get; set; }
        [JsonProperty("summary")]        public RoundSummary  Summary       { get; set; }
        [JsonProperty("wallet_balance")] public string        WalletBalance { get; set; }
    }

    [Serializable]
    public class RoundInfo
    {
        [JsonProperty("round_id")]    public string   RoundId    { get; set; }
        [JsonProperty("status")]      public string   Status     { get; set; }
        [JsonProperty("dice_result")] public string   DiceResult { get; set; }
        [JsonProperty("dice_1")]      public int      Dice1      { get; set; }
        [JsonProperty("dice_2")]      public int      Dice2      { get; set; }
        [JsonProperty("dice_3")]      public int      Dice3      { get; set; }
        [JsonProperty("dice_4")]      public int      Dice4      { get; set; }
        [JsonProperty("dice_5")]      public int      Dice5      { get; set; }
        [JsonProperty("dice_6")]      public int      Dice6      { get; set; }
        [JsonProperty("start_time")]  public DateTime StartTime  { get; set; }
        [JsonProperty("result_time")] public DateTime ResultTime { get; set; }
    }

    [Serializable]
    public class BetInfo
    {
        [JsonProperty("id")]            public int    Id           { get; set; }
        [JsonProperty("number")]        public int    Number       { get; set; }
        [JsonProperty("chip_amount")]   public string ChipAmount   { get; set; }
        [JsonProperty("is_winner")]     public bool   IsWinner     { get; set; }
        [JsonProperty("payout_amount")] public string PayoutAmount { get; set; }
    }

    [Serializable]
    public class RoundSummary
    {
        [JsonProperty("total_bets")]       public int    TotalBets       { get; set; }
        [JsonProperty("total_bet_amount")] public string TotalBetAmount  { get; set; }
        [JsonProperty("total_payout")]     public string TotalPayout     { get; set; }
        [JsonProperty("net_result")]       public int    NetResult        { get; set; }
        [JsonProperty("winning_bets")]     public int    WinningBets      { get; set; }
        [JsonProperty("losing_bets")]      public int    LosingBets       { get; set; }
    }

    [Serializable]
    public class GameState
    {
        [JsonProperty("type")]     public string type     { get; set; }
        [JsonProperty("round_id")] public string round_id { get; set; }
        [JsonProperty("status")]   public string status   { get; set; }
        [JsonProperty("timer")]    public int    timer    { get; set; }
    }

    [Serializable]
    public class TimerUpdate
    {
        [JsonProperty("type")]     public string type     { get; set; }
        [JsonProperty("timer")]    public int    timer    { get; set; }
        [JsonProperty("status")]   public string status   { get; set; }
        [JsonProperty("round_id")] public string round_id { get; set; }
    }

    [Serializable]
    public class GameStart
    {
        [JsonProperty("type")]     public string type     { get; set; }
        [JsonProperty("round_id")] public string round_id { get; set; }
        [JsonProperty("status")]   public string status   { get; set; }
        [JsonProperty("timer")]    public int    timer    { get; set; }
    }

    [Serializable]
    public class BettingClosed
    {
        [JsonProperty("type")] public string type { get; set; }
        [JsonProperty("timer")] public int timer { get; set; }
        [JsonProperty("status")] public string status { get; set; }
        [JsonProperty("round_id")] public string round_id { get; set; }
    }

    [Serializable]
    public class DiceRollWarning
    {
        [JsonProperty("type")]           public string type           { get; set; }
        [JsonProperty("round_id")]       public string round_id       { get; set; }
        [JsonProperty("status")]         public string status         { get; set; }
        [JsonProperty("timer")]          public int    timer          { get; set; }
        [JsonProperty("dice_roll_time")] public int    dice_roll_time { get; set; }
    }

    [Serializable]
    public class DiceResult
    {
        [JsonProperty("type")]        public string type        { get; set; }
        [JsonProperty("round_id")]    public string round_id    { get; set; }
        [JsonProperty("status")]      public string status      { get; set; }
        [JsonProperty("timer")]       public int    timer       { get; set; }
        [JsonProperty("result")]      public string result      { get; set; }
        [JsonProperty("dice_values")] public int[]  dice_values { get; set; }
    }

    [Serializable]
    public class LastRoundResult
    {
        [JsonProperty("round_id")]    public string round_id;
        [JsonProperty("dice_1")]      public int    dice1;
        [JsonProperty("dice_2")]      public int    dice2;
        [JsonProperty("dice_3")]      public int    dice3;
        [JsonProperty("dice_4")]      public int    dice4;
        [JsonProperty("dice_5")]      public int    dice5;
        [JsonProperty("dice_6")]      public int    dice6;
        [JsonProperty("dice_result")] public string diceResult;
        [JsonProperty("timestamp")]   public string timestamp;
    }

    [Serializable]
    public class ResultAnnouncement
    {
        [JsonProperty("type")]        public string  type        { get; set; }
        [JsonProperty("round_id")]    public string  round_id    { get; set; }
        [JsonProperty("status")]      public string  status      { get; set; }
        [JsonProperty("timer")]       public int     timer       { get; set; }
        [JsonProperty("dice_result")] public int     dice_result { get; set; }
        [JsonProperty("dice_values")] public int[]   dice_values { get; set; }
        [JsonIgnore]                  public JObject raw         { get; set; }
    }

    [Serializable]
    public class GameEnd
    {
        [JsonProperty("type")]        public string Type        { get; set; }
        [JsonProperty("round_id")]    public string RoundId     { get; set; }
        [JsonProperty("status")]      public string Status      { get; set; }
        [JsonProperty("timer")]       public int    Timer       { get; set; }
        [JsonProperty("end_time")]    public long   EndTime     { get; set; }
        [JsonProperty("server_time")] public long   ServerTime  { get; set; }
        [JsonProperty("is_rolling")]  public bool   IsRolling   { get; set; }
        [JsonProperty("dice_result")] public string DiceResult  { get; set; }
        [JsonProperty("result")]      public string Result      { get; set; }
        [JsonProperty("dice_values")] public int[]  DiceValues  { get; set; }
        [JsonProperty("dice_1")]      public int    Dice1       { get; set; }
        [JsonProperty("dice_2")]      public int    Dice2       { get; set; }
        [JsonProperty("dice_3")]      public int    Dice3       { get; set; }
        [JsonProperty("dice_4")]      public int    Dice4       { get; set; }
        [JsonProperty("dice_5")]      public int    Dice5       { get; set; }
        [JsonProperty("dice_6")]      public int    Dice6       { get; set; }
    }

    [Serializable]
    public class RoundData
    {
        [JsonProperty("id")]           public int      Id           { get; set; }
        [JsonProperty("round_id")]     public string   RoundId      { get; set; }
        [JsonProperty("status")]       public string   Status       { get; set; }
        [JsonProperty("start_time")]   public DateTime StartTime    { get; set; }
        [JsonProperty("timer")]        public int      Timer        { get; set; }
        [JsonProperty("dice_result")]  public int?     DiceResult   { get; set; }
        [JsonProperty("dice_1")]       public int?     Dice1        { get; set; }
        [JsonProperty("dice_2")]       public int?     Dice2        { get; set; }
        [JsonProperty("dice_3")]       public int?     Dice3        { get; set; }
        [JsonProperty("dice_4")]       public int?     Dice4        { get; set; }
        [JsonProperty("dice_5")]       public int?     Dice5        { get; set; }
        [JsonProperty("dice_6")]       public int?     Dice6        { get; set; }
        [JsonProperty("total_bets")]   public int      TotalBets    { get; set; }
        [JsonProperty("total_amount")] public string   TotalAmount  { get; set; }
    }

    [Serializable]
    public class RoundUpdate
    {
        [JsonProperty("type")]     public string type     { get; set; }
        [JsonProperty("round_id")] public string round_id { get; set; }
        [JsonProperty("status")]   public string status   { get; set; }
    }

    [Serializable]
    public class MyBet
    {
        [JsonProperty("id")]            public int    id           { get; set; }
        [JsonProperty("round")]         public string round_id     { get; set; }
        [JsonProperty("number")]        public int    number       { get; set; }
        [JsonProperty("chip_amount")]   public string chip_amount  { get; set; }
        [JsonProperty("is_winner")]     public bool   is_winner    { get; set; }
        [JsonProperty("payout_amount")] public string payout_amount{ get; set; }
        [JsonProperty("created_at")]    public string created_at   { get; set; }
    }

    [Serializable] public class BetRequest { public int number; public string chip_amount; }

    [Serializable]
    public class BetResponse
    {
        public int    id;
        public int    number;
        public string chip_amount;
        public string created_at;
    }

    [Serializable]
    public class ServerResult
    {
        public string round_id;
        public string status;
        public string message;
    }

    [Serializable]
    public class WinningFrequenceResponse
    {
        [JsonProperty("round_id")]        public string              RoundId        { get; set; }
        [JsonProperty("dice_result")]     public string              DiceResult     { get; set; }
        [JsonProperty("round")]           public RoundDetails        Round          { get; set; }
        [JsonProperty("winning_numbers")] public List<WinningNumber> WinningNumbers { get; set; }
    }

    [Serializable]
    public class RoundDetails
    {
        [JsonProperty("round_id")]    public string     RoundId    { get; set; }
        [JsonProperty("status")]      public string     Status     { get; set; }
        [JsonProperty("dice_result")] public string     DiceResult { get; set; }
        [JsonProperty("dice_values")] public List<int>  DiceValues { get; set; }
        [JsonProperty("start_time")]  public DateTime   StartTime  { get; set; }
        [JsonProperty("result_time")] public DateTime   ResultTime { get; set; }
        [JsonProperty("end_time")]    public DateTime   EndTime    { get; set; }
    }

    [Serializable]
    public class WinningNumber
    {
        [JsonProperty("number")]           public int   Number           { get; set; }
        [JsonProperty("frequency")]        public int   Frequency        { get; set; }
        [JsonProperty("payout_multiplier")]public float PayoutMultiplier { get; set; }
    }

    [Serializable]
    public class Prediction
    {
        [JsonProperty("id")]         public int    id         { get; set; }
        [JsonProperty("user")]       public User   user       { get; set; }
        [JsonProperty("round")]      public int    round      { get; set; }
        [JsonProperty("number")]     public int    number     { get; set; }
        [JsonProperty("is_correct")] public bool   is_correct { get; set; }
        [JsonProperty("created_at")] public string created_at { get; set; }
    }

    [Serializable]
    public class PredictionResponse
    {
        [JsonProperty("message")]    public string     message    { get; set; }
        [JsonProperty("prediction")] public Prediction prediction { get; set; }
    }

    [Serializable]
    public class RoundPredictionOverall
    {
        [JsonProperty("total_predictions")] public int total_predictions { get; set; }
        [JsonProperty("total_unique_users")] public int total_unique_users { get; set; }
        [JsonProperty("total_correct")]      public int total_correct      { get; set; }
    }

    [Serializable]
    public class RoundPredictionByNumber
    {
        [JsonProperty("number")]              public int number              { get; set; }
        [JsonProperty("total_predictions")]   public int total_predictions   { get; set; }
        [JsonProperty("correct_predictions")] public int correct_predictions { get; set; }
    }

    [Serializable]
    public class RoundPredictionStatistics
    {
        [JsonProperty("overall")]   public RoundPredictionOverall          overall   { get; set; }
        [JsonProperty("by_number")] public List<RoundPredictionByNumber>   by_number { get; set; }
    }

    [Serializable]
    public class RoundPredictionsResponse
    {
        [JsonProperty("round")]           public JObject                    round           { get; set; }
        [JsonProperty("user_prediction")] public Prediction                 user_prediction  { get; set; }
        [JsonProperty("predictions")]     public List<Prediction>           predictions      { get; set; }
        [JsonProperty("statistics")]      public RoundPredictionStatistics  statistics       { get; set; }
        [JsonProperty("count")]           public int                        count            { get; set; }
    }

    [Serializable]
    public class IndividualBet
    {
        [JsonProperty("id")]           public int    id           { get; set; }
        [JsonProperty("user_id")]      public int    user_id      { get; set; }
        [JsonProperty("username")]     public string username     { get; set; }
        [JsonProperty("number")]       public int    number       { get; set; }
        [JsonProperty("chip_amount")]  public string chip_amount  { get; set; }
        [JsonProperty("created_at")]   public string created_at   { get; set; }
        [JsonProperty("is_winner")]    public bool   is_winner    { get; set; }
    }

    [Serializable]
    public class RoundBetsResponse
    {
        [JsonProperty("round")]            public JObject             round            { get; set; }
        [JsonProperty("bets")]             public List<JObject>       bets             { get; set; }
        [JsonProperty("individual_bets")]  public List<IndividualBet> individual_bets  { get; set; }
        [JsonProperty("statistics")]       public JObject             statistics       { get; set; }
        [JsonProperty("count")]            public int                 count            { get; set; }
        [JsonProperty("individual_count")] public int                 individual_count { get; set; }
    }

    [Serializable]
    public class ExposureEntry
    {
        [JsonProperty("player_id")]       public int    player_id       { get; set; }
        [JsonProperty("username")]        public string username        { get; set; }
        [JsonProperty("exposure_amount")] public string exposure_amount { get; set; }
    }

    [Serializable]
    public class RoundExposureResponse
    {
        [JsonProperty("round_id")] public string             round_id { get; set; }
        [JsonProperty("status")]   public string             status   { get; set; }
        [JsonProperty("exposure")] public List<ExposureEntry> exposure { get; set; }
    }

    [Serializable]
    public class LoadingTimeResponse
    {
        [JsonProperty("loading_time")] public int loading_time { get; set; }
    }

    [Serializable]
    public class SoundSettings
    {
        [JsonProperty("background_music_volume")] public float BackgroundMusicVolume { get; set; }
        [JsonProperty("is_muted")]                public bool  IsMuted              { get; set; }
    }

    #endregion

    #region ──── Lifecycle & Dispatching ───────────────────────────────────────

    private void Start()
    {
        Invoke(nameof(CheckConnection), 0.75f);
    }

    private void CheckConnection()
    {
        connectionMonitorRoutine = StartCoroutine(ConnectionMonitorLoop());
    }

    private void Update()
    {
        // Execute queued main-thread actions
        while (mainThreadQueue.TryDequeue(out var action))
        {
            try   { action?.Invoke(); }
            catch (Exception ex) { Debug.LogError("[MainThread] Action error: " + ex.Message); }
        }
    }

    private void OnDestroy()
    {
        if (connectionMonitorRoutine != null)
            StopCoroutine(connectionMonitorRoutine);
    }

    #endregion

    #region ──── Connection Monitor ────────────────────────────────────────────

    private IEnumerator ConnectionMonitorLoop()
    {
        var wait = new WaitForSeconds(connectionCheckInterval);
        while (true)
        {
            bool internetOk = IsInternetAvailable();
            bool shouldShowPopup = !internetOk;

            if (shouldShowPopup && !popupVisible)
            {
                popupVisible = true;
                mainThreadQueue.Enqueue(() => OnShowHidePopup?.Invoke(true));
            }
            else if (!shouldShowPopup && popupVisible)
            {
                popupVisible = false;
                mainThreadQueue.Enqueue(() =>
                {
                    OnShowHidePopup?.Invoke(false);
                    OnReconnect?.Invoke();
                });
            }
            yield return wait;
        }
    }

    private bool IsInternetAvailable() =>
        Application.internetReachability != NetworkReachability.NotReachable;

    private IEnumerator CheckServerHealth(Action<bool> callback)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/settings/"))
        {
            AddAuthHeader(req);
            req.timeout = 5;
            yield return req.SendWebRequest();
#if UNITY_2020_1_OR_NEWER
            bool error = req.result == UnityWebRequest.Result.ConnectionError ||
                         req.result == UnityWebRequest.Result.ProtocolError;
#else
            bool error = req.isNetworkError || req.isHttpError;
#endif
            callback(!error);
        }
    }

    #endregion

    #region ──── Time API ──────────────────────────────────────────────────────

    /// <summary>
    /// Fetches current server time and active round info from the time API.
    /// Callback: (success, response, errorMessage)
    /// </summary>
    public void GetServerTime(Action<bool, ServerTimeResponse, string> callback = null) =>
        StartCoroutine(GetServerTimeCoroutine(callback));

    private IEnumerator GetServerTimeCoroutine(Action<bool, ServerTimeResponse, string> callback)
    {
        using (var req = UnityWebRequest.Get(timeApiUrl))
        {
            req.timeout = 10;
            yield return req.SendWebRequest();

#if UNITY_2020_1_OR_NEWER
            bool isError = req.result == UnityWebRequest.Result.ConnectionError ||
                           req.result == UnityWebRequest.Result.ProtocolError;
#else
            bool isError = req.isNetworkError || req.isHttpError;
#endif
            if (isError)
            {
                Debug.LogError($"[TimeAPI] Request failed: {req.error}");
                callback?.Invoke(false, null, req.error);
                yield break;
            }

            try
            {
                var resp = JsonConvert.DeserializeObject<ServerTimeResponse>(req.downloadHandler.text);
                if (resp == null)
                {
                    callback?.Invoke(false, null, "Empty or null response from time API");
                    yield break;
                }
                callback?.Invoke(true, resp, null);
            }
            catch (Exception ex)
            {
                Debug.LogError($"[TimeAPI] Parse error: {ex.Message}");
                callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message);
            }
        }
    }

    #endregion

    #region ──── HTTP Coroutines ────────────────────────────────────────────────

    private IEnumerator RegisterCoroutine(string username, string password, string cnfPassword,
        Action<bool, RegisterationErrorResponse, string> callback = null)
    {
        var url  = $"{baseUrl}/api/auth/register/";
        var body = JsonConvert.SerializeObject(new { username, password, confirm_password = cnfPassword });

        using (var req = new UnityWebRequest(url, UnityWebRequest.kHttpVerbPOST))
        {
            req.uploadHandler   = new UploadHandlerRaw(Encoding.UTF8.GetBytes(body));
            req.downloadHandler = new DownloadHandlerBuffer();
            req.SetRequestHeader("Content-Type", JSON_CONTENT);
            yield return req.SendWebRequest();

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                try
                {
                    var errResp = JsonConvert.DeserializeObject<RegisterationErrorResponse>(req.downloadHandler.text);
                    callback?.Invoke(false, errResp, req.error);
                }
                catch
                {
                    callback?.Invoke(false, null, req.error);
                }
                yield break;
            }

            callback?.Invoke(true, null, null);
        }
    }

    private IEnumerator LoginCoroutine(string username, string password, Action<bool, string> callback = null)
    {
        var url  = $"{baseUrl}/api/auth/login/";
        var body = JsonConvert.SerializeObject(new { username, password });

        using (var req = new UnityWebRequest(url, UnityWebRequest.kHttpVerbPOST))
        {
            req.uploadHandler   = new UploadHandlerRaw(Encoding.UTF8.GetBytes(body));
            req.downloadHandler = new DownloadHandlerBuffer();
            req.SetRequestHeader("Content-Type", JSON_CONTENT);
            yield return req.SendWebRequest();

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                var auth = JsonConvert.DeserializeObject<AuthResponse>(req.downloadHandler.text);
                accessToken  = auth?.access;
                refreshToken = auth?.refresh;
                isPlayerLoggedIn = true;
                mainThreadQueue.Enqueue(() => OnLoginSuccess?.Invoke());
                callback?.Invoke(true, null);
            }
            catch (Exception ex)
            {
                callback?.Invoke(false, ex.Message);
            }
        }
    }

    private IEnumerator GetLoadingTimeCoroutine(Action<bool, LoadingTimeResponse, string> callback = null)
    {
        var url = $"{baseUrl}/api/game/loading-time/";
        using (var req = UnityWebRequest.Get(url))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetLoadingTimeCoroutine(callback))))
                yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                var result = JsonConvert.DeserializeObject<LoadingTimeResponse>(req.downloadHandler.text);
                callback?.Invoke(true, result, null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message); }
        }
    }

    private IEnumerator RefreshTokenCoroutine(Action<bool, string> callback = null)
    {
        if (string.IsNullOrEmpty(refreshToken))
        {
            callback?.Invoke(false, "No refresh token available");
            yield break;
        }

        var url  = $"{baseUrl}/api/auth/token/refresh/";
        var body = JsonConvert.SerializeObject(new Dictionary<string, string> { { "refresh", refreshToken } });

        using (var req = new UnityWebRequest(url, UnityWebRequest.kHttpVerbPOST))
        {
            req.uploadHandler   = new UploadHandlerRaw(Encoding.UTF8.GetBytes(body));
            req.downloadHandler = new DownloadHandlerBuffer();
            req.SetRequestHeader("Content-Type", JSON_CONTENT);
            yield return req.SendWebRequest();

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                var auth = JsonConvert.DeserializeObject<AuthResponse>(req.downloadHandler.text);
                accessToken  = auth?.access;
                refreshToken = auth?.refresh;
                callback?.Invoke(true, null);
            }
            catch (Exception ex) { callback?.Invoke(false, ex.Message); }
        }
    }

    private IEnumerator GetPaymentMethodsCoroutine(Action<bool, List<PaymentMethod>, string> callback)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/auth/payment-methods/"))
        {
            req.SetRequestHeader("Authorization", "Bearer " + accessToken);
            req.SetRequestHeader("Accept", "application/json");
            yield return req.SendWebRequest();

            if (req.result != UnityWebRequest.Result.Success)
            {
                callback?.Invoke(false, null, req.error);
                yield break;
            }

            var responseText = req.downloadHandler.text;
            if (!responseText.Trim().StartsWith("["))
            {
                callback?.Invoke(false, null, "Invalid JSON response");
                yield break;
            }

            try
            {
                var methods = JsonConvert.DeserializeObject<List<PaymentMethod>>(responseText);
                callback?.Invoke(true, methods, null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator DeleteLastBetCoroutine(Action<bool, string> callback = null)
    {
        var url = $"{baseUrl}/api/game/bet/last/";
        using (var req = new UnityWebRequest(url, "DELETE"))
        {
            req.downloadHandler = new DownloadHandlerBuffer();
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(DeleteLastBetCoroutine(callback))))
                yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            callback?.Invoke(true, req.downloadHandler.text);
        }
    }

    private IEnumerator UploadDepositProofCoroutine(string amount, byte[] screenshotBytes,
        string screenshotFileName, string paymentLink = null, string paymentReference = null,
        Action<bool, DepositProofResponse, string> callback = null)
    {
        var form = new WWWForm();
        form.AddField("amount", amount);
        if (!string.IsNullOrEmpty(paymentLink))      form.AddField("payment_link",      paymentLink);
        if (!string.IsNullOrEmpty(paymentReference)) form.AddField("payment_reference", paymentReference);
        form.AddBinaryData("screenshot", screenshotBytes, screenshotFileName, "image/jpeg");

        using (var req = UnityWebRequest.Post($"{baseUrl}/api/auth/deposits/upload-proof/", form))
        {
            AddAuthHeader(req);
            req.timeout = 15;
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(UploadDepositProofCoroutine(
                    amount, screenshotBytes, screenshotFileName, paymentLink, paymentReference, callback))))
                yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                var resp = JsonConvert.DeserializeObject<DepositProofResponse>(req.downloadHandler.text);
                callback?.Invoke(true, resp, null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message); }
        }
    }

    private IEnumerator GetWalletCoroutine(Action<bool, WalletResponse, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/auth/wallet/"))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetWalletCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<WalletResponse>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator GetBetAmountSummaryCoroutine(Action<bool, List<BetAmountSummary>, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/user-bets-summary/"))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetBetAmountSummaryCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<List<BetAmountSummary>>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator GetMaxBetAmountCoroutine(Action<bool, MaxBetResponse, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/max-bet/"))
        {
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetMaxBetAmountCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<MaxBetResponse>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator GetGameSettingCoroutine(Action<bool, GameSettings, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/settings/"))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetGameSettingCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                var settings = JsonConvert.DeserializeObject<GameSettings>(req.downloadHandler.text);
                callback?.Invoke(true, settings, null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator GetCurrentRoundResultCoroutine(Action<bool, RoundResultResponse, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/winning-results/"))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetCurrentRoundResultCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            var json = req.downloadHandler?.text;
            if (string.IsNullOrEmpty(json)) { callback?.Invoke(false, null, "Empty response"); yield break; }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<RoundResultResponse>(json), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator GetLastRoundResultsCoroutine(Action<bool, LastRoundResult, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/last-round-results/"))
        {
            req.SetRequestHeader("Content-Type", "application/json");
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetLastRoundResultsCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, $"Error: {req.error}\nResponse: {req.downloadHandler.text}");
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<LastRoundResult>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator GetRoundCoroutine(Action<bool, RoundData, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/round/"))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetRoundCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<RoundData>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator PlaceBetCoroutine(int number, float amount, Action<bool, BetResponse, string> callback = null)
    {
        var url  = $"{baseUrl}/api/game/bet/";
        var body = JsonConvert.SerializeObject(new BetRequest { number = number, chip_amount = amount.ToString("F2") });

        using (var req = new UnityWebRequest(url, UnityWebRequest.kHttpVerbPOST))
        {
            req.uploadHandler   = new UploadHandlerRaw(Encoding.UTF8.GetBytes(body));
            req.downloadHandler = new DownloadHandlerBuffer();
            req.SetRequestHeader("Content-Type", JSON_CONTENT);
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(PlaceBetCoroutine(number, amount, callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<BetResponse>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, ex.Message); }
        }
    }

    private IEnumerator DeleteBetCoroutine(int number, Action<bool, string> callback = null)
    {
        var url = $"{baseUrl}/api/game/bets/{number}/";
        using (var req = new UnityWebRequest(url, "DELETE"))
        {
            req.downloadHandler = new DownloadHandlerBuffer();
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(DeleteBetCoroutine(number, callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            callback?.Invoke(true, null);
        }
    }

    private IEnumerator GetWinningFrequencyCoroutine(Action<bool, WinningFrequenceResponse, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/frequency/"))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetWinningFrequencyCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<WinningFrequenceResponse>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message); }
        }
    }

    private IEnumerator SubmitPredictionCoroutine(int number, Action<bool, PredictionResponse, string> callback = null)
    {
        var url  = $"{baseUrl}/api/game/predictions/";
        var body = JsonConvert.SerializeObject(new { number });

        using (var req = new UnityWebRequest(url, UnityWebRequest.kHttpVerbPOST))
        {
            req.uploadHandler   = new UploadHandlerRaw(Encoding.UTF8.GetBytes(body));
            req.downloadHandler = new DownloadHandlerBuffer();
            req.SetRequestHeader("Content-Type", JSON_CONTENT);
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(SubmitPredictionCoroutine(number, callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler?.text);
                yield break;
            }

            var text = req.downloadHandler?.text;
            if (string.IsNullOrEmpty(text)) { callback?.Invoke(false, null, "Empty response"); yield break; }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<PredictionResponse>(text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message); }
        }
    }

    private IEnumerator GetRoundPredictionsCoroutine(string roundId, Action<bool, RoundPredictionsResponse, string> callback = null)
    {
        string url = string.IsNullOrEmpty(roundId)
            ? $"{baseUrl}/api/game/round/predictions/"
            : $"{baseUrl}/api/game/round/{UnityWebRequest.EscapeURL(roundId)}/predictions/";

        using (var req = UnityWebRequest.Get(url))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetRoundPredictionsCoroutine(roundId, callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<RoundPredictionsResponse>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message); }
        }
    }

    private IEnumerator GetRoundBetsCoroutine(string roundId, Action<bool, RoundBetsResponse, string> callback = null)
    {
        string url = $"{baseUrl}/api/game/round/bets/";
        if (!string.IsNullOrEmpty(roundId)) url += $"?round_id={UnityWebRequest.EscapeURL(roundId)}";

        using (var req = UnityWebRequest.Get(url))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetRoundBetsCoroutine(roundId, callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler?.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<RoundBetsResponse>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message); }
        }
    }

    private IEnumerator GetRoundExposureCoroutine(string roundId, int? userId, Action<bool, RoundExposureResponse, string> callback = null)
    {
        string url = string.IsNullOrEmpty(roundId)
            ? $"{baseUrl}/api/game/round/exposure/"
            : $"{baseUrl}/api/game/round/{UnityWebRequest.EscapeURL(roundId)}/exposure/";
        if (userId.HasValue) url += (url.Contains("?") ? "&" : "?") + "user_id=" + userId.Value;

        using (var req = UnityWebRequest.Get(url))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetRoundExposureCoroutine(roundId, userId, callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler?.text);
                yield break;
            }

            var text = req.downloadHandler?.text;
            if (string.IsNullOrEmpty(text)) { callback?.Invoke(false, null, "Empty response"); yield break; }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<RoundExposureResponse>(text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message); }
        }
    }

    private IEnumerator GetSoundSettingsCoroutine(Action<bool, SoundSettings, string> callback = null)
    {
        using (var req = UnityWebRequest.Get($"{baseUrl}/api/game/settings/sound/"))
        {
            AddAuthHeader(req);
            yield return req.SendWebRequest();

            if (HandleAuthErrors(req, () => StartCoroutine(GetSoundSettingsCoroutine(callback)))) yield break;

#if UNITY_2020_1_OR_NEWER
            if (req.result == UnityWebRequest.Result.ConnectionError ||
                req.result == UnityWebRequest.Result.ProtocolError)
#else
            if (req.isNetworkError || req.isHttpError)
#endif
            {
                callback?.Invoke(false, null, req.error + " : " + req.downloadHandler?.text);
                yield break;
            }

            try
            {
                callback?.Invoke(true, JsonConvert.DeserializeObject<SoundSettings>(req.downloadHandler.text), null);
            }
            catch (Exception ex) { callback?.Invoke(false, null, "JSON Parse Error: " + ex.Message); }
        }
    }

    #endregion

    #region ──── Helpers ────────────────────────────────────────────────────────

    private void AddAuthHeader(UnityWebRequest req)
    {
        if (!string.IsNullOrEmpty(accessToken))
            req.SetRequestHeader("Authorization", "Bearer " + accessToken);
    }

    /// <summary>
    /// On 401, attempts token refresh then retries. Returns true if the caller should abort
    /// (because the refresh coroutine is handling the retry).
    /// </summary>
    private bool HandleAuthErrors(UnityWebRequest req, Action retryAction)
    {
#if UNITY_2020_1_OR_NEWER
        bool isError = req.result == UnityWebRequest.Result.ConnectionError ||
                       req.result == UnityWebRequest.Result.ProtocolError;
#else
        bool isError = req.isNetworkError || req.isHttpError;
#endif
        if (!isError) return false;
        if (req.responseCode != 401) return false;

        StartCoroutine(RefreshTokenCoroutine((ok, msg) =>
        {
            if (ok) retryAction?.Invoke();
            else    mainThreadQueue.Enqueue(() => OnError?.Invoke("Session expired. Please login again."));
        }));
        return true;
    }

    #endregion

    #region ──── Convenience Wrappers ──────────────────────────────────────────

    public void Register(string username, string password, string cnfPassword,
        Action<bool, RegisterationErrorResponse, string> callback = null) =>
        StartCoroutine(RegisterCoroutine(username, password, cnfPassword, callback));

    public void Login(string username, string password, Action<bool, string> callback = null) =>
        StartCoroutine(LoginCoroutine(username, password, callback));

    public void GetLoadingTime(Action<bool, LoadingTimeResponse, string> callback = null) =>
        StartCoroutine(GetLoadingTimeCoroutine(callback));

    public void RefreshToken(Action<bool, string> callback = null) =>
        StartCoroutine(RefreshTokenCoroutine(callback));

    public void GetPaymentMethods(Action<bool, List<PaymentMethod>, string> callback = null) =>
        StartCoroutine(GetPaymentMethodsCoroutine(callback));

    public void DeleteLastBet(Action<bool, string> callback = null) =>
        StartCoroutine(DeleteLastBetCoroutine(callback));

    public void UploadDepositProof(string amount, byte[] screenshotBytes, string screenshotFileName,
        string paymentLink = null, string paymentReference = null,
        Action<bool, DepositProofResponse, string> callback = null) =>
        StartCoroutine(UploadDepositProofCoroutine(amount, screenshotBytes, screenshotFileName,
            paymentLink, paymentReference, callback));

    public void GetGameSettings(Action<bool, GameSettings, string> callback = null) =>
        StartCoroutine(GetGameSettingCoroutine(callback));

    public void GetCurrentRoundResult(Action<bool, RoundResultResponse, string> callback = null) =>
        StartCoroutine(GetCurrentRoundResultCoroutine(callback));

    public void GetLastRoundResult(Action<bool, LastRoundResult, string> callback = null) =>
        StartCoroutine(GetLastRoundResultsCoroutine(callback));

    public void GetWallet(Action<bool, WalletResponse, string> callback = null) =>
        StartCoroutine(GetWalletCoroutine(callback));

    public void GetBetAmountSummary(Action<bool, List<BetAmountSummary>, string> callback = null) =>
        StartCoroutine(GetBetAmountSummaryCoroutine(callback));

    public void GetMaxBetAmountSummary(Action<bool, MaxBetResponse, string> callback = null) =>
        StartCoroutine(GetMaxBetAmountCoroutine(callback));

    public void GetCurrentRound(Action<bool, RoundData, string> callback = null) =>
        StartCoroutine(GetRoundCoroutine(callback));

    public void PlaceBet(int number, float amount, Action<bool, BetResponse, string> callback = null) =>
        StartCoroutine(PlaceBetCoroutine(number, amount, callback));

    public void DeleteBet(int number, Action<bool, string> callback = null) =>
        StartCoroutine(DeleteBetCoroutine(number, callback));

    public void GetWinningFrequency(Action<bool, WinningFrequenceResponse, string> callback = null) =>
        StartCoroutine(GetWinningFrequencyCoroutine(callback));

    public void SubmitPrediction(int number, Action<bool, PredictionResponse, string> callback = null) =>
        StartCoroutine(SubmitPredictionCoroutine(number, callback));

    public void GetRoundPredictions(string roundId, Action<bool, RoundPredictionsResponse, string> callback = null) =>
        StartCoroutine(GetRoundPredictionsCoroutine(roundId, callback));

    public void GetRoundBets(string roundId, Action<bool, RoundBetsResponse, string> callback = null) =>
        StartCoroutine(GetRoundBetsCoroutine(roundId, callback));

    public void GetRoundExposure(string roundId, int? userId, Action<bool, RoundExposureResponse, string> callback = null) =>
        StartCoroutine(GetRoundExposureCoroutine(roundId, userId, callback));

    public void GetSoundSettings(Action<bool, SoundSettings, string> callback = null) =>
        StartCoroutine(GetSoundSettingsCoroutine(callback));

    public void SetAccessAndRefreshToken(string access, string refresh)
    {
        accessToken      = access;
        refreshToken     = refresh;
        isPlayerLoggedIn = true;
        OnLoginSuccess?.Invoke();
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    public void GetCurrentRoundCached(Action<bool, RoundData, string> callback = null)
    {
        if (cachedRoundData != null && (Time.time - cachedRoundTimestamp) < ROUND_CACHE_TTL)
        {
            callback?.Invoke(true, cachedRoundData, null);
            return;
        }
        StartCoroutine(GetRoundCoroutine((ok, data, err) =>
        {
            if (ok && data != null) { cachedRoundData = data; cachedRoundTimestamp = Time.time; }
            callback?.Invoke(ok, data, err);
        }));
    }

    public void GetGameSettingsCached(Action<bool, GameSettings, string> callback = null)
    {
        if (cachedGameSettings != null && (Time.time - cachedSettingsTimestamp) < SETTINGS_CACHE_TTL)
        {
            callback?.Invoke(true, cachedGameSettings, null);
            return;
        }
        StartCoroutine(GetGameSettingCoroutine((ok, settings, err) =>
        {
            if (ok && settings != null) { cachedGameSettings = settings; cachedSettingsTimestamp = Time.time; }
            callback?.Invoke(ok, settings, err);
        }));
    }

    public void InvalidateCaches()
    {
        cachedRoundData          = null;
        cachedRoundTimestamp     = 0f;
        cachedGameSettings       = null;
        cachedSettingsTimestamp  = 0f;
    }

    // ── Event relay (called by RoundManager to preserve existing subscriber API) ──

    /// <summary>Called by RoundManager to relay game_state event to all subscribers.</summary>
    public void FireGameState(GameState msg)         => OnGameState?.Invoke(msg);
    /// <summary>Called by RoundManager every second to relay timer ticks.</summary>
    public void FireTimerUpdate(TimerUpdate msg)     => OnTimerUpdate?.Invoke(msg);
    /// <summary>Called by RoundManager when a new round starts.</summary>
    public void FireGameStart(GameStart msg)         => OnGameStart?.Invoke(msg);
    /// <summary>Called by RoundManager at the betting close moment.</summary>
    public void FireBettingClose(BettingClosed msg) => OnBettingClose?.Invoke(msg);
    /// <summary>Called by RoundManager at the dice-roll-warning moment.</summary>
    public void FireDiceRollWarn(DiceRollWarning msg)=> OnDiceRollWarn?.Invoke(msg);
    /// <summary>Called by RoundManager when the dice result is fetched.</summary>
    public void FireDiceResult(DiceResult msg)       => OnDiceResult?.Invoke(msg);
    /// <summary>Called by RoundManager when the result is announced.</summary>
    public void FireResultAnn(ResultAnnouncement msg)=> OnResultAnn?.Invoke(msg);
    /// <summary>Called by RoundManager when the round ends.</summary>
    public void FireGameEnd(GameEnd msg)             => OnGameEnd?.Invoke(msg);
    /// <summary>Called by RoundManager on round transitions.</summary>
    public void FireRoundUpdate(RoundUpdate msg)     => OnRoundUpdate?.Invoke(msg);

    #endregion
}
