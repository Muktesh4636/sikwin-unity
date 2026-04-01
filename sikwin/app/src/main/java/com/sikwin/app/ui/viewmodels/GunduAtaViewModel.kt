package com.sikwin.app.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sikwin.app.data.api.RetrofitClient
import com.sikwin.app.data.auth.SessionManager
import com.sikwin.app.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.unity3d.player.UnityTokenHolder
import android.content.SharedPreferences

class GunduAtaViewModel(private val sessionManager: SessionManager) : ViewModel() {

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    var maintenanceActive by mutableStateOf(false)
    var maintenanceMessage by mutableStateOf<String?>(null)
    private var maintenanceCheckInFlight = false

    fun checkMaintenanceStatus() {
        if (maintenanceCheckInFlight) return
        maintenanceCheckInFlight = true
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.apiService.getMaintenanceStatus()
                if (resp.isSuccessful) {
                    val body = resp.body()
                    val active = (body?.get("maintenance") as? Boolean) == true
                    val remainingHours = (body?.get("remaining_hours") as? Number)?.toInt()
                        ?: (body?.get("remaining_hours") as? String)?.toIntOrNull()
                    val remainingMinutes = (body?.get("remaining_minutes") as? Number)?.toInt()
                        ?: (body?.get("remaining_minutes") as? String)?.toIntOrNull()
                    val legacyUntilMinutes = (body?.get("maintenance_until") as? Number)?.toInt()
                        ?: (body?.get("maintenance_until") as? String)?.toIntOrNull()

                    android.util.Log.d(
                        "Maintenance",
                        "status active=$active hours=$remainingHours minutes=$remainingMinutes legacyUntil=$legacyUntilMinutes"
                    )
                    maintenanceActive = active
                    maintenanceMessage = if (active) {
                        formatMaintenanceMessage(remainingHours, remainingMinutes, legacyUntilMinutes)
                    } else null
                }
            } catch (_: Exception) {
                // Network errors should not block app usage.
            } finally {
                maintenanceCheckInFlight = false
            }
        }
    }

    private fun formatMaintenanceMessage(untilMinutes: Int?): String {
        val suffix = when {
            untilMinutes == null -> "soon."
            untilMinutes < 60 -> "after $untilMinutes minutes."
            untilMinutes % 60 == 0 -> {
                val h = untilMinutes / 60
                if (h == 1) "after 1 hour." else "after $h hours."
            }
            else -> {
                val h = untilMinutes / 60
                val m = untilMinutes % 60
                val hoursPart = if (h == 1) "1 hour" else "$h hours"
                val minsPart = "$m minutes"
                "after $hoursPart $minsPart."
            }
        }
        return "App under maintenance. Please come back $suffix"
    }

    private fun formatMaintenanceMessage(remainingHours: Int?, remainingMinutes: Int?, legacyUntilMinutes: Int?): String {
        val h = remainingHours
        val m = remainingMinutes
        val suffix = when {
            h == null && m == null -> {
                // Fallback to legacy minutes-only field
                return formatMaintenanceMessage(legacyUntilMinutes)
            }
            (h ?: 0) <= 0 && (m ?: 0) <= 0 -> "soon."
            (h ?: 0) <= 0 -> "after ${m ?: 0} minutes."
            (m ?: 0) <= 0 -> {
                if (h == 1) "after 1 hour." else "after $h hours."
            }
            else -> {
                val hoursPart = if (h == 1) "1 hour" else "$h hours"
                "after $hoursPart ${m} minutes."
            }
        }
        return "App under maintenance. Please come back $suffix"
    }

    // Last raw credentials user entered (kept in-memory only; not persisted unless caller saves).
    @Volatile private var lastEnteredUsername: String? = null
    @Volatile private var lastEnteredPassword: String? = null

    fun getLastEnteredCredentials(): Pair<String?, String?> = Pair(lastEnteredUsername, lastEnteredPassword)

    fun clearError() {
        errorMessage = null
    }

    private fun parseError(errorBody: String?): String {
        if (errorBody.isNullOrEmpty()) return "Something went wrong. Please try again."
        return try {
            val raw = try {
                val json = JSONObject(errorBody)
                when {
                    json.has("error") -> json.getString("error")
                    json.has("message") -> json.getString("message")
                    json.has("detail") -> json.getString("detail")
                    else -> {
                        val keys = json.keys()
                        if (keys.hasNext()) {
                            val firstKey = keys.next()
                            val value = json.get(firstKey)
                            if (value is org.json.JSONArray && value.length() > 0) {
                                value.getString(0)
                            } else if (value is org.json.JSONObject) {
                                val nestedKeys = value.keys()
                                if (nestedKeys.hasNext()) {
                                    val firstNestedKey = nestedKeys.next()
                                    value.get(firstNestedKey).toString()
                                } else {
                                    "Invalid input. Please try again."
                                }
                            } else {
                                value.toString()
                            }
                        } else {
                            "An unexpected error occurred."
                        }
                    }
                }
            } catch (e: Exception) {
                if (errorBody.length < 200 && !errorBody.trim().startsWith("{")) errorBody.trim()
                else "Something went wrong. Please try again."
            }
            sanitizeErrorMessage(raw)
        } catch (e: Exception) {
            "Something went wrong. Please try again."
        }
    }

    private fun sanitizeErrorMessage(raw: String): String {
        if (raw.isBlank()) return "Something went wrong. Please try again."
        
        // Catch HTML responses
        if (raw.trim().startsWith("<!doctype", ignoreCase = true) || 
            raw.trim().startsWith("<html", ignoreCase = true)) {
            val lower = raw.lowercase()
            return when {
                lower.contains("413") || lower.contains("too large") -> "The file you are trying to upload is too large. Please use a smaller file (max 10MB)."
                lower.contains("502") || lower.contains("bad gateway") -> "Server is busy. Please try again later."
                lower.contains("504") || lower.contains("gateway timeout") -> "Server timeout. Please try again."
                else -> "An unexpected server error occurred. Please try again."
            }
        }

        val lower = raw.lowercase()
        return when {
            lower.contains("already has a pending request") || 
            lower.contains("pending withdraw request") -> "Withdrawal already in processing"
            lower.contains("500") || lower.contains("internal server error") -> "Server error. Please try again later."
            lower.contains("502") || lower.contains("bad gateway") -> "Server is busy. Please try again later."
            lower.contains("503") || lower.contains("service unavailable") -> "Service temporarily unavailable. Please try again."
            lower.contains("404") || lower.contains("not found") -> "Request could not be completed. Please try again."
            lower.contains("403") || lower.contains("forbidden") -> "Access denied. Please try again."
            lower.contains("401") || lower.contains("unauthorized") || lower.contains("authentication") -> "Please sign in again."
            lower.contains("413") || lower.contains("too large") -> "The file you are trying to upload is too large. Please use a smaller file."
            lower.contains("connection refused") || lower.contains("failed to connect") -> "Unable to connect. Please check your network."
            lower.contains("timeout") || lower.contains("timed out") -> "Request timed out. Please try again."
            else -> raw
        }
    }

    private fun handleException(e: Exception): String {
        android.util.Log.e("GunduAtaViewModel", "Exception: ${e.message}", e)
        return when (e) {
            is java.net.UnknownHostException -> "No internet connection. Please check your network."
            is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
            is java.net.ConnectException -> "Unable to connect to server. Please try again later."
            is retrofit2.HttpException -> "Server error. Please try again later."
            else -> "An unexpected error occurred. Please try again."
        }
    }

    private fun logoutIfUnauthorized(code: Int) {
        if (code == 401 || code == 403) {
            // Force a full logout if backend says session is invalid.
            logout()
        }
    }
    
    var userProfile by mutableStateOf<User?>(null)

    /** Referral code persisted locally — available immediately without waiting for API. */
    val savedReferralCode: String? get() = sessionManager.fetchReferralCode()

    var wallet by mutableStateOf<Wallet?>(null)
    var transactions by mutableStateOf<List<Transaction>>(emptyList())
    var depositRequests by mutableStateOf<List<DepositRequest>>(emptyList())
    var withdrawRequests by mutableStateOf<List<WithdrawRequest>>(emptyList())
    var paymentMethods by mutableStateOf<List<PaymentMethod>>(emptyList())
    var bettingHistory by mutableStateOf<List<Bet>>(emptyList())
    /** Cricket/IPL bets only — from [getCricketBettingHistory], not dice [Betting] history. */
    var cricketBettingHistory by mutableStateOf<List<CricketBetHistoryItem>>(emptyList())
    var cricketBetsLoading by mutableStateOf(false)
    var cricketBetsError by mutableStateOf<String?>(null)
    var referralData by mutableStateOf<ReferralData?>(null)

    /** Cricket / IPL tab — GET /api/cricket/live/ */
    var cricketLive by mutableStateOf<CricketLiveEventData?>(null)
    /** Bumps on each successful odds payload so list keys refresh even when [CricketLiveEventData] equals previous. */
    var cricketLiveEpoch by mutableStateOf(0L)
    var cricketFetchedAt by mutableStateOf<String?>(null)
    var cricketLoading by mutableStateOf(false)
    var cricketError by mutableStateOf<String?>(null)
    var cricketBetPlacing by mutableStateOf(false)
    /** True only after several consecutive failed polls (legacy blur; odds are cleared on failure so no stale odds). */
    var cricketPollStopped by mutableStateOf(false)

    /** Colour game — GET /api/colour/round/ */
    var colourRound by mutableStateOf<ColourRoundResponse?>(null)
    /**
     * Seconds remaining for the colour round UI — ticks down every second locally and is
     * re-synced from the server on each [refreshColourRound].
     */
    var colourDisplayTimerSeconds by mutableIntStateOf(0)
    /** GET /api/colour/bets/ — sorted newest first in [fetchColourBetsHistory]. */
    var colourBetsHistory by mutableStateOf<List<ColourBetHistoryItem>>(emptyList())
    /** GET /api/colour/results/ — global recent results for Colour Game table. */
    var colourPublicResults by mutableStateOf<List<ColourPublicResultItem>>(emptyList())

    private var colourLocalTickJob: Job? = null
    private var colourRoundPollJob: Job? = null
    private var colourResultPollJob: Job? = null
    private var colourPublicResultsPollJob: Job? = null
    /** Avoid duplicate “timer hit zero” handling for the same [ColourRoundResponse.round_id]. */
    private var colourTimerZeroHandledRoundId: String? = null
    /** Max [ColourRoundResponse.timer] seen for the current round — used to tell 60s vs ~30s countdown. */
    private var colourPeakTimerForCurrentRound: Int = 0

    companion object {
        /** Sync round/timer often enough that local countdown stays aligned with the server. */
        private const val COLOUR_ROUND_POLL_INTERVAL_MS = 8_000L
        /** Public results refresh while Colour Game is open (faster than round poll). */
        private const val COLOUR_PUBLIC_RESULTS_POLL_MS = 4_000L
    }

    /** Call when entering Colour Game; [stopColourGameSession] when leaving. */
    fun startColourGameSession() {
        colourRoundPollJob?.cancel()
        colourResultPollJob?.cancel()
        colourPublicResultsPollJob?.cancel()
        startColourLocalTimer()
        colourRoundPollJob = viewModelScope.launch {
            refreshColourRound()
            while (isActive) {
                delay(COLOUR_ROUND_POLL_INTERVAL_MS)
                refreshColourRound()
            }
        }
        colourPublicResultsPollJob = viewModelScope.launch {
            fetchColourPublicResults()
            while (isActive) {
                delay(COLOUR_PUBLIC_RESULTS_POLL_MS)
                fetchColourPublicResults()
            }
        }
    }

    fun stopColourGameSession() {
        colourRoundPollJob?.cancel()
        colourRoundPollJob = null
        colourResultPollJob?.cancel()
        colourResultPollJob = null
        colourPublicResultsPollJob?.cancel()
        colourPublicResultsPollJob = null
        colourLocalTickJob?.cancel()
        colourLocalTickJob = null
    }

    /**
     * When the server has reported a timer above ~40s for this round, treat the round as a ~60s
     * cycle and lock betting for the last 30s ([timerSec] 30…0). Otherwise assume a short (~30s)
     * betting countdown and follow [ColourRoundResponse.betting_open] with [timerSec] > 0.
     */
    fun colourRoundUsesSixtySecondCountdown(): Boolean = colourPeakTimerForCurrentRound > 40

    private fun startColourLocalTimer() {
        if (colourLocalTickJob?.isActive == true) return
        colourLocalTickJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (colourDisplayTimerSeconds > 0) {
                    colourDisplayTimerSeconds--
                    if (colourDisplayTimerSeconds == 0) {
                        onColourDisplayTimerReachedZero()
                    }
                }
            }
        }
    }

    private fun onColourDisplayTimerReachedZero() {
        val rid = colourRound?.round_id ?: return
        if (colourTimerZeroHandledRoundId == rid) return
        colourTimerZeroHandledRoundId = rid
        colourResultPollJob?.cancel()
        colourResultPollJob = viewModelScope.launch {
            refreshColourRound()
            repeat(36) {
                delay(2000)
                val res = fetchColourRoundResult(rid)
                val body = res.getOrNull()
                refreshColourRound()
                if (loginSuccess) fetchColourBetsHistory()
                val done = body?.status?.equals("COMPLETED", ignoreCase = true) == true ||
                    !body?.result.isNullOrBlank()
                if (done) {
                    fetchColourPublicResults()
                    return@launch
                }
            }
        }
    }

    /**
     * Single fetch for GET /api/cricket/live/. Returns true only when [response has data] and updates [cricketLive].
     * On any failure, empty body, or missing `data`, clears live odds so the UI shows nothing until a good response.
     */
    suspend fun cricketFetchOnce(): Boolean {
        val showSpinner = cricketLive == null && cricketError == null
        if (showSpinner) cricketLoading = true
        return try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getCricketLive()
            }
            if (resp.isSuccessful) {
                val body = resp.body()
                val data = body?.data
                cricketFetchedAt = body?.fetched_at
                if (data != null) {
                    cricketLive = data
                    cricketLiveEpoch++
                    cricketError = null
                    true
                } else {
                    android.util.Log.w("CricketLive", "Empty data in response")
                    clearCricketLive("No live data")
                    false
                }
            } else {
                logoutIfUnauthorized(resp.code())
                val err = resp.errorBody()?.string()
                val msg = parseError(err)
                android.util.Log.w("CricketLive", "HTTP ${resp.code()} $err")
                clearCricketLive(msg)
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("CricketLive", "Failed", e)
            clearCricketLive(e.message ?: "Could not load cricket odds.")
            false
        } finally {
            if (showSpinner) cricketLoading = false
        }
    }

    private fun clearCricketLive(message: String?) {
        cricketLive = null
        cricketFetchedAt = null
        cricketLiveEpoch = 0L
        cricketError = message?.takeIf { it.isNotBlank() } ?: "Could not load cricket odds."
    }

    fun refreshCricketFeed() {
        viewModelScope.launch {
            cricketFetchOnce()
        }
    }

    /**
     * POST /api/cricket/bet/. [onDone] is invoked on the main thread with null on success, or an error message.
     */
    fun placeCricketBet(
        eventId: Long,
        marketId: Long,
        outcomeId: Long,
        stake: Int,
        onDone: (error: String?) -> Unit
    ) {
        viewModelScope.launch {
            cricketBetPlacing = true
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.postCricketBet(
                        CricketBetRequest(
                            event_id = eventId,
                            market_id = marketId,
                            outcome_id = outcomeId,
                            stake = stake
                        )
                    )
                }
                if (resp.isSuccessful) {
                    fetchWallet()
                    onDone(null)
                } else {
                    logoutIfUnauthorized(resp.code())
                    val err = resp.errorBody()?.string()
                    onDone(parseError(err))
                }
            } catch (e: Exception) {
                onDone(e.message ?: "Could not place bet.")
            } finally {
                cricketBetPlacing = false
            }
        }
    }

    /** Show customer support popup only when app is opened fresh (cold start or resumed from background), not when navigating within app (e.g. profile -> home). */
    var showSupportPopupOnNextHomeVisit by mutableStateOf(true)

    fun markSupportPopupShown() {
        showSupportPopupOnNextHomeVisit = false
    }
    
    var otpSent by mutableStateOf(false)
    var isVerifyingOtp by mutableStateOf(false)
    
    var bankDetails by mutableStateOf<List<UserBankDetail>>(emptyList())
    var isLoadingBankDetails by mutableStateOf(false)

    var loginSuccess by mutableStateOf(false)

    private val sessionPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == "logout_requested" ||
                key == "logout_timestamp" ||
                key == "is_logged_in" ||
                key == "user_token" ||
                key == "auth_token" ||
                key == "access_token" ||
                key == "access" ||
                key == "refresh_token" ||
                key == "refresh" ||
                key == "refreshToken"
            ) {
                // Authenticator may logout on a background thread; bring state updates to Main.
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    val isLoggedInNow = sessionManager.fetchAuthToken() != null
                    if (!isLoggedInNow) {
                        loginSuccess = false
                        userProfile = null
                        wallet = null
                        userRank = 0
                        userRotationMoney = 0.0
                        leaderboardPlayers = emptyList()
                    } else {
                        loginSuccess = true
                    }
                }
            }
        }
    
    // Logo click tracking
    var logoClickCount by mutableIntStateOf(0)
    
    fun incrementLogoClickCount() {
        logoClickCount++
    }
    
    // App Update state
    var showUpdateDialog by mutableStateOf(false)
    var updateUrl by mutableStateOf<String?>(null)
    var isForceUpdate by mutableStateOf(false)
    var latestVersionName by mutableStateOf<String?>(null)
    
    var recentResults by mutableStateOf<List<RecentRoundResult>>(emptyList())
    
    // Timer pre-loading state
    var preLoadedTimer by mutableStateOf<Int?>(null)
    var preLoadedStatus by mutableStateOf<String?>(null)
    var preLoadedRoundId by mutableStateOf<String?>(null)
    private var timerJob: kotlinx.coroutines.Job? = null

    fun startTimerPreloading() {
        if (timerJob != null && timerJob?.isActive == true) return
        
        timerJob = viewModelScope.launch {
            while (true) {
                try {
                    val response = RetrofitClient.apiService.getCurrentRound()
                    if (response.isSuccessful) {
                        val data = response.body()
                        preLoadedTimer = (data?.get("timer") as? Double)?.toInt() ?: (data?.get("timer") as? Int)
                        preLoadedStatus = data?.get("status") as? String
                        preLoadedRoundId = data?.get("round_id") as? String
                        
                        // Sync to Unity immediately so it's ready
                        preLoadedTimer?.let { t ->
                            preLoadedStatus?.let { s ->
                                preLoadedRoundId?.let { r ->
                                    syncTimerToUnity(t, s, r)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GunduAtaViewModel", "Timer pre-load failed: ${e.message}")
                }
                kotlinx.coroutines.delay(500) // Update every 500ms for ultra-fresh 0-lag sync
            }
        }
    }

    fun stopTimerPreloading() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun syncTimerToUnity(timer: Int, status: String, roundId: String) {
        try {
            // We use PlayerPrefs via a helper or direct SharedPreferences
            // This ensures Unity sees the timer the moment it starts
            val sessionManager = com.sikwin.app.data.api.RetrofitClient.getSessionManager()
            if (sessionManager != null) {
                // Now add the timer specific fields
                val context = sessionManager.getContext()
                val standalonePackageName = "com.company.dicegame"
                val unityPrefsName = "$standalonePackageName.v2.playerprefs"
                val unityPrefs = context.getSharedPreferences(unityPrefsName, android.content.Context.MODE_PRIVATE)
                unityPrefs.edit()
                    .putInt("preloaded_timer", timer)
                    .putString("preloaded_status", status)
                    .putString("preloaded_round_id", roundId)
                    .putString("preloaded_timestamp", System.currentTimeMillis().toString())
                    .apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("GunduAtaViewModel", "Failed to sync timer to Unity: ${e.message}")
        }
    }

    // Check if session is still valid
    fun checkSession() {
        if (sessionManager.fetchAuthToken() == null) {
            loginSuccess = false
            userProfile = null
            wallet = null
            userRank = 0
            userRotationMoney = 0.0
            leaderboardPlayers = emptyList()
        }
    }
    
    init {
        // Initialize RetrofitClient with session manager
        RetrofitClient.init(sessionManager)

        // Ensure UI reacts when session ends (e.g., refresh token expired in OkHttp authenticator).
        try {
            sessionManager.registerSessionListener(sessionPrefListener)
        } catch (_: Exception) {}

        // Sync auth to Unity PlayerPrefs on init to ensure consistency
        sessionManager.syncAuthToUnity()
        
        loginSuccess = sessionManager.fetchAuthToken() != null
    }

    override fun onCleared() {
        stopColourGameSession()
        try {
            sessionManager.unregisterSessionListener(sessionPrefListener)
        } catch (_: Exception) {}
        super.onCleared()
    }

    fun syncAuthToUnity() {
        sessionManager.syncAuthToUnity()
    }

    /** Returns saved (username/phone, password) for quick login. null if not saved. */
    fun getSavedCredentials(): Pair<String?, String?> {
        val user = sessionManager.fetchUsername()
        val pass = sessionManager.fetchPassword()
        return if (user != null && pass != null && pass.isNotEmpty()) Pair(user, pass) else Pair(null, null)
    }

    fun login(username: String, password: String, savePassword: Boolean = true) {
        // Keep exactly what user entered (raw) for Unity handoff.
        lastEnteredUsername = username
        lastEnteredPassword = password

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.login(mapOf("username" to username, "password" to password))
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    authResponse?.let {
                        sessionManager.saveTokens(it.access, it.refresh)
                        sessionManager.saveUsername(username)  // Save login input (phone/username) for quick login display
                        sessionManager.saveUserId(it.user.id)
                        if (savePassword) sessionManager.savePassword(password) else sessionManager.clearSavedPassword()
                        sessionManager.saveReferralCode(it.user.referral_code)
                        
                        // CRITICAL: Push tokens to Unity immediately (before any navigation)
                        try {
                            com.unity3d.player.UnityTokenHolder.setTokens(it.access, it.refresh ?: "", "", "")
                            android.util.Log.d("GunduAtaViewModel", "Login: Set UnityTokenHolder (accessLen=${it.access.length})")
                        } catch (e: Exception) {
                            android.util.Log.e("GunduAtaViewModel", "Login: UnityTokenHolder failed", e)
                        }
                        sessionManager.syncAuthToUnity()
                        
                        // Send broadcast for Unity if already running
                        try {
                            com.sikwin.app.utils.UnityTokenHelper.sendTokensToUnity(
                                sessionManager.getContext(),
                                it.access,
                                it.refresh
                            )
                        } catch (e: Exception) {
                            android.util.Log.d("GunduAtaViewModel", "Unity broadcast: ${e.message}")
                        }
                        
                        userProfile = it.user
                        loginSuccess = true
                        registerFcmTokenIfNeeded()
                        fetchWallet()
                        fetchProfile()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    /** Notifications disabled: no FCM registration or permission request. */
    fun registerFcmTokenIfNeeded() {
        // No-op: notifications are disabled in this build
    }

    fun sendOtp(phoneNumber: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.sendOtp(mapOf("phone_number" to phoneNumber))
                if (response.isSuccessful) {
                    otpSent = true
                    errorMessage = null
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun verifyOtpLogin(phoneNumber: String, otpCode: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.verifyOtpLogin(mapOf(
                    "phone_number" to phoneNumber,
                    "otp_code" to otpCode
                ))
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    authResponse?.let {
                        sessionManager.saveTokens(it.access, it.refresh)
                        sessionManager.saveUsername(it.user.username)
                        sessionManager.saveUserId(it.user.id)
                        sessionManager.saveReferralCode(it.user.referral_code)
                        // Note: We don't save password for OTP login
                        
                        // CRITICAL: Push tokens to Unity immediately
                        try {
                            com.unity3d.player.UnityTokenHolder.setTokens(it.access, it.refresh ?: "", "", "")
                            android.util.Log.d("GunduAtaViewModel", "OTP Login: Set UnityTokenHolder (accessLen=${it.access.length})")
                        } catch (e: Exception) {
                            android.util.Log.e("GunduAtaViewModel", "OTP Login: UnityTokenHolder failed", e)
                        }
                        sessionManager.syncAuthToUnity()
                        
                        try {
                            com.sikwin.app.utils.UnityTokenHelper.sendTokensToUnity(
                                sessionManager.getContext(),
                                it.access,
                                it.refresh
                            )
                        } catch (e: Exception) {
                            android.util.Log.d("GunduAtaViewModel", "Unity broadcast: ${e.message}")
                        }
                        
                        userProfile = it.user
                        sessionManager.saveReferralCode(it.user.referral_code)
                        loginSuccess = true
                        otpSent = false // Reset OTP state
                        registerFcmTokenIfNeeded()
                        fetchWallet()
                        fetchProfile()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun clearOtpState() {
        otpSent = false
        errorMessage = null
    }

    fun resetPassword(phoneNumber: String, otpCode: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val data = mapOf(
                    "phone_number" to phoneNumber,
                    "otp_code" to otpCode,
                    "new_password" to newPassword
                )
                val response = RetrofitClient.apiService.resetPassword(data)
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun register(data: Map<String, String>) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.register(data)
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    authResponse?.let {
                        sessionManager.saveTokens(it.access, it.refresh)
                        sessionManager.saveUsername(it.user.username)
                        sessionManager.saveUserId(it.user.id)
                        data["password"]?.let { pass -> sessionManager.savePassword(pass) }
                        
                        // Sync auth to Unity PlayerPrefs
                        sessionManager.syncAuthToUnity()
                        
                        // Send tokens to Unity if Unity is already running (token-only)
                        try {
                            com.sikwin.app.utils.UnityTokenHelper.sendTokensToUnity(
                                sessionManager.getContext(),
                                it.access,
                                it.refresh
                            )
                        } catch (e: Exception) {
                            // Unity might not be running yet, that's okay
                            android.util.Log.d("GunduAtaViewModel", "Unity not running, tokens will be sent when Unity starts: ${e.message}")
                        }
                        
                        userProfile = it.user
                        sessionManager.saveReferralCode(it.user.referral_code)
                        loginSuccess = true
                        fetchWallet()
                        fetchProfile()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchProfile() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getProfile()
                if (response.isSuccessful) {
                    val profile = response.body()
                    userProfile = profile
                    sessionManager.saveReferralCode(profile?.referral_code)
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            }
        }
    }

    fun fetchWallet() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getWallet()
                if (response.isSuccessful) {
                    wallet = response.body()
                    // Re-fetch betting history to update ranking whenever wallet is refreshed
                    fetchBettingHistory()
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            }
        }
    }

    /**
     * POST /api/coin/ — server decides outcome. Does not update [wallet] here so the UI can
     * refresh balance only after the coin animation ([fetchWallet] from the screen).
     */
    suspend fun postCoinFlip(tossHeads: Boolean, betAmount: Int): Result<CoinFlipResponse> {
        return try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.postCoinFlip(
                    mapOf(
                        "toss" to if (tossHeads) "heads" else "tails",
                        "bet_amount" to betAmount
                    )
                )
            }
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                logoutIfUnauthorized(response.code())
                Result.failure(Exception(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            Result.failure(Exception(handleException(e)))
        }
    }

    /** GET /api/colour/round/ — no auth. Updates [colourDisplayTimerSeconds] from server [ColourRoundResponse.timer]. */
    suspend fun refreshColourRound() {
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getColourRound()
            }
            if (response.isSuccessful) {
                val body = response.body()
                val prevId = colourRound?.round_id
                colourRound = body
                val newId = body?.round_id
                if (newId != null && newId != prevId) {
                    colourTimerZeroHandledRoundId = null
                    colourPeakTimerForCurrentRound = 0
                }
                val t = body?.timer
                if (t != null) {
                    colourPeakTimerForCurrentRound =
                        maxOf(colourPeakTimerForCurrentRound, t.coerceAtLeast(0))
                    colourDisplayTimerSeconds = t.coerceAtLeast(0)
                    if (t == 0) {
                        onColourDisplayTimerReachedZero()
                    }
                } else if (body?.status?.equals("no_round", ignoreCase = true) == true) {
                    colourDisplayTimerSeconds = 0
                    colourPeakTimerForCurrentRound = 0
                }
            }
        } catch (_: Exception) {
            // Keep last known round; avoid spamming errors while polling.
        }
    }

    /** GET /api/colour/results/ — public recent round outcomes (no auth). */
    suspend fun fetchColourPublicResults() {
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getColourPublicResults()
            }
            if (response.isSuccessful) {
                colourPublicResults = response.body()?.results.orEmpty()
            }
        } catch (_: Exception) {
            // Keep previous list on failure.
        }
    }

    /** GET /api/colour/bets/ — auth required. */
    fun fetchColourBetsHistory() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getColourBets()
                if (response.isSuccessful) {
                    val list = response.body()?.bets.orEmpty()
                    colourBetsHistory = list.sortedByDescending { it.settled_at ?: it.created_at ?: "" }
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            }
        }
    }

    /**
     * POST /api/colour/bet/ — auth required.
     * @param side `"red"`, `"green"`, or `"violet"`.
     */
    suspend fun postColourBetSide(side: String, amount: Int): Result<ColourBetPlaceResponse> {
        return postColourBet(
            mapOf(
                "bet_on" to side.lowercase(),
                "amount" to amount
            )
        )
    }

    /** POST number 0–9 with [postColourBet]. */
    suspend fun postColourBetNumber(number: Int, amount: Int): Result<ColourBetPlaceResponse> {
        return postColourBet(
            mapOf(
                "bet_on" to "number",
                "number" to number,
                "amount" to amount
            )
        )
    }

    /** POST /api/colour/bet/ — single payload or `{ "bets": [...] }`. */
    suspend fun postColourBet(body: Map<String, @JvmSuppressWildcards Any>): Result<ColourBetPlaceResponse> {
        return try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.postColourBet(body)
            }
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                logoutIfUnauthorized(response.code())
                Result.failure(Exception(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            Result.failure(Exception(handleException(e)))
        }
    }

    /** GET /api/colour/round/{roundId}/result/ — no auth. */
    suspend fun fetchColourRoundResult(roundId: String): Result<ColourRoundResultResponse> {
        return try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getColourRoundResult(roundId)
            }
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string())))
            }
        } catch (e: Exception) {
            Result.failure(Exception(handleException(e)))
        }
    }

    fun fetchTransactions() {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.apiService.getTransactions()
                if (response.isSuccessful) {
                    transactions = response.body() ?: emptyList()
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchDeposits() {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.apiService.getMyDeposits()
                if (response.isSuccessful) {
                    depositRequests = response.body() ?: emptyList()
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchWithdrawals() {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.apiService.getMyWithdrawals()
                if (response.isSuccessful) {
                    withdrawRequests = response.body() ?: emptyList()
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchPaymentMethods() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getPaymentMethods()
                if (response.isSuccessful) {
                    paymentMethods = response.body() ?: emptyList()
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                // Log and ignore background fetch errors to prevent technical jargon in UI
                android.util.Log.e("GunduAtaViewModel", "Fetch payment methods failed: ${e.message}")
            }
        }
    }

    fun fetchBankDetails() {
        // Set loading immediately to avoid a brief empty-state flicker
        // on first open of Withdraw screen.
        isLoadingBankDetails = true
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getBankDetails()
                if (response.isSuccessful) {
                    bankDetails = response.body() ?: emptyList()
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                android.util.Log.e("GunduAtaViewModel", "Fetch bank details failed: ${e.message}")
            } finally {
                isLoadingBankDetails = false
            }
        }
    }

    fun fetchBettingHistory() {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.apiService.getBettingHistory()
                if (response.isSuccessful) {
                    val history = response.body() ?: emptyList()
                    bettingHistory = history
                } else {
                    logoutIfUnauthorized(response.code())
                }
            } catch (e: Exception) {
                android.util.Log.e("GunduAtaViewModel", "Fetch betting history failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchCricketBettingHistory() {
        viewModelScope.launch {
            cricketBetsLoading = true
            cricketBetsError = null
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getCricketBettingHistory()
                }
                if (response.isSuccessful) {
                    val body = response.body()
                    val list = body?.bets ?: body?.data ?: body?.results ?: emptyList()
                    cricketBettingHistory = list
                } else {
                    logoutIfUnauthorized(response.code())
                    cricketBetsError = parseError(response.errorBody()?.string())
                    cricketBettingHistory = emptyList()
                }
            } catch (e: Exception) {
                cricketBetsError = e.message ?: "Could not load cricket bets."
                android.util.Log.e("GunduAtaViewModel", "Fetch cricket betting history failed", e)
            } finally {
                cricketBetsLoading = false
            }
        }
    }

    fun fetchReferralData() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.getReferralData()
                if (response.isSuccessful) {
                    referralData = response.body()
                } else {
                    logoutIfUnauthorized(response.code())
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun addBankDetail(data: Map<String, Any>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.addBankDetail(data)
                if (response.isSuccessful) {
                    fetchBankDetails()
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody).ifEmpty { "Could not add bank account. Please try again." }
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun submitWhitelabelLead(name: String, phone: String, message: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val data = buildMap<String, String> {
                    put("name", name.trim())
                    put("phone_number", phone.trim())
                    if (message.isNotBlank()) put("message", message.trim())
                }
                val response = RetrofitClient.apiService.submitWhitelabelLead(data)
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody).ifEmpty { "Could not submit. Please try again." }
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteBankDetail(id: Int) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.deleteBankDetail(id)
                if (response.isSuccessful) {
                    fetchBankDetails()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody).ifEmpty { "Could not remove bank account. Please try again." }
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun submitUtr(amount: String, utr: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.submitUtr(mapOf("amount" to amount, "utr" to utr))
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun uploadDepositProof(amount: String, uri: android.net.Uri, context: android.content.Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Failed to read image")
                inputStream.close()

                val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, bytes.size)
                val body = MultipartBody.Part.createFormData("screenshot", "screenshot.jpg", requestFile)

                val response = RetrofitClient.apiService.uploadDepositProof(amount, body)
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun updateUsername(newUsername: String) {
        updateProfile(mapOf("username" to newUsername))
    }

    fun updatePassword(currentPassword: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val data = mapOf(
                    "current_password" to currentPassword,
                    "new_password" to newPassword
                )
                val response = RetrofitClient.apiService.updateProfile(data)
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun updateProfile(data: Map<String, String>) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                // Ensure gender is uppercase if present
                val processedData = data.toMutableMap()
                if (processedData.containsKey("gender")) {
                    processedData["gender"] = processedData["gender"]?.uppercase() ?: ""
                }
                
                // Map "Name" to "username" if it comes from the UI as "Name"
                if (processedData.containsKey("Name")) {
                    processedData["username"] = processedData.remove("Name") ?: ""
                }
                
                val response = RetrofitClient.apiService.updateProfile(processedData)
                if (response.isSuccessful) {
                    userProfile = response.body()
                    processedData["username"]?.let { sessionManager.saveUsername(it) }
                    // Re-fetch profile to ensure UI is in sync with server state
                    fetchProfile()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody).ifEmpty { "Could not update profile. Please try again." }
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun updateProfilePhoto(photo: okhttp3.MultipartBody.Part) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.updateProfilePhoto(photo)
                if (response.isSuccessful) {
                    userProfile = response.body()
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody).ifEmpty { "Could not update photo. Please try again." }
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun initiateWithdraw(amount: String, bankAccount: UserBankDetail, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val details = "Bank: ${bankAccount.bank_name}, Acc: ${bankAccount.account_number}, IFSC: ${bankAccount.ifsc_code}"
                val data = mapOf(
                    "amount" to amount,
                    "withdrawal_method" to "Bank Account",
                    "withdrawal_details" to details
                )
                val response = RetrofitClient.apiService.initiateWithdraw(data)
                if (response.isSuccessful) {
                    onSuccess()
                    fetchWallet() // Refresh balance
                } else {
                    val errorBody = response.errorBody()?.string()
                    errorMessage = parseError(errorBody)
                }
            } catch (e: Exception) {
                errorMessage = handleException(e)
            } finally {
                isLoading = false
            }
        }
    }
    
    fun checkDailyRewardStatus(onResult: (Boolean, String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.checkDailyRewardStatus()
                if (response.isSuccessful) {
                    val body = response.body()
                    val claimed = body?.get("claimed") as? Boolean ?: false
                    val message = body?.get("message") as? String
                    val reward = body?.get("reward") as? Map<*, *>
                    val amount = reward?.get("amount")?.toString()
                    onResult(claimed, message, amount)
                } else {
                    onResult(false, "Unable to check reward status. Please try again.", null)
                }
            } catch (e: Exception) {
                onResult(false, handleException(e), null)
            }
        }
    }

    fun claimDailyReward(onResult: (Boolean, Int?, String, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.claimDailyReward()
                if (response.isSuccessful) {
                    val body = response.body()
                    val reward = body?.get("daily_reward") as? Map<*, *> ?: body?.get("reward") as? Map<*, *>
                    
                    if (reward != null) {
                        val amountStr = reward["amount"]?.toString() ?: "0"
                        val amount = amountStr.toDoubleOrNull()?.toInt() ?: 0
                        val type = reward["type"]?.toString() ?: "MONEY"
                        val message = body?.get("message") as? String ?: "Reward claimed"
                        
                        // Refresh wallet balance after claiming
                        fetchWallet()
                        
                        onResult(true, amount, type, message)
                    } else {
                        onResult(false, null, "TRY_AGAIN", "Something went wrong. Please try again.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    onResult(false, null, "TRY_AGAIN", parseError(errorBody))
                }
            } catch (e: Exception) {
                onResult(false, null, "TRY_AGAIN", handleException(e))
            }
        }
    }

    fun checkLuckyDrawStatus(onResult: (Boolean, String?, String?, Double?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.checkLuckyDrawStatus()
                if (response.isSuccessful) {
                    val body = response.body()
                    val claimed = body?.get("claimed") as? Boolean ?: false
                    val message = body?.get("message") as? String
                    val amount = resolveMegaSpinRupeeAmountOrNull(body)?.toString()
                    val depositAmount = body?.get("deposit_amount")?.toString()?.toDoubleOrNull()
                    onResult(claimed, message, amount, depositAmount)
                } else {
                    onResult(false, "Unable to check lucky draw status. Please try again.", null, null)
                }
            } catch (e: Exception) {
                onResult(false, handleException(e), null, null)
            }
        }
    }

    fun claimLuckyDraw(onResult: (Boolean, Int?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.claimLuckyDraw()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null) {
                        onResult(false, null, "Something went wrong. Please try again.")
                        return@launch
                    }
                    val hasLuckyDraw = body["lucky_draw"] is Map<*, *>
                    val hasReward = body["reward"] is Map<*, *>
                    val hasCredited = body["credited_amount"] != null
                    if (!hasLuckyDraw && !hasReward && !hasCredited) {
                        onResult(false, null, "Something went wrong. Please try again.")
                        return@launch
                    }
                    val amount = resolveMegaSpinRupeeAmount(body)
                    val message = body["message"] as? String ?: "Reward claimed"
                    fetchWallet()
                    onResult(true, amount, message)
                } else {
                    val errorBody = response.errorBody()?.string()
                    onResult(false, null, parseError(errorBody))
                }
            } catch (e: Exception) {
                onResult(false, null, handleException(e))
            }
        }
    }

    private fun parseAmountToInt(value: Any?): Int? {
        return when (value) {
            is Number -> kotlin.math.round(value.toDouble()).toInt()
            is String -> value.toDoubleOrNull()?.let { kotlin.math.round(it).toInt() }
            else -> null
        }
    }

    /**
     * Mega spin APIs sometimes return both lucky_draw.amount and reward.amount with different values.
     * Prefer credited_amount. If they disagree, lucky_draw matches the credited prize; reward can be lower.
     */
    private fun resolveMegaSpinRupeeAmount(body: Map<String, Any>?): Int {
        if (body == null) return 0
        body["credited_amount"]?.let { parseAmountToInt(it) }?.let { return it }
        val ld = (body["lucky_draw"] as? Map<*, *>)?.get("amount")?.let { parseAmountToInt(it) }
        val rw = (body["reward"] as? Map<*, *>)?.get("amount")?.let { parseAmountToInt(it) }
        if (ld != null && rw != null && ld != rw) return ld
        return ld ?: rw ?: 0
    }

    private fun resolveMegaSpinRupeeAmountOrNull(body: Map<String, Any>?): Int? {
        if (body == null) return null
        body["credited_amount"]?.let { parseAmountToInt(it) }?.let { return it }
        val ld = (body["lucky_draw"] as? Map<*, *>)?.get("amount")?.let { parseAmountToInt(it) }
        val rw = (body["reward"] as? Map<*, *>)?.get("amount")?.let { parseAmountToInt(it) }
        if (ld != null && rw != null && ld != rw) return ld
        return ld ?: rw
    }

    // Optional: Sync contacts to backend
    // Uncomment this function and the API endpoint if you want to send contacts to server
    /*
    fun syncContacts(contactsJson: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = RetrofitClient.apiService.syncContacts(mapOf("contacts" to contactsJson))
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val error = parseError(errorBody)
                    errorMessage = error
                    onError(error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Failed to sync contacts"
                errorMessage = error
                onError(error)
            } finally {
                isLoading = false
            }
        }
    }
    */

    fun logout() {
        // 1. Clear UI state IMMEDIATELY (Main Thread)
        userProfile = null
        wallet = null
        transactions = emptyList()
        depositRequests = emptyList()
        withdrawRequests = emptyList()
        errorMessage = null
        loginSuccess = false
        userRank = 0
        userRotationMoney = 0.0
        leaderboardPlayers = emptyList()

        // 2. Perform heavy cleanup in background with extreme safety
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Use a local copy of context to avoid ViewModel lifecycle issues
                val context = sessionManager.getContext().applicationContext
                
                // Clear app session
                sessionManager.forceLogout("GunduAtaViewModel.logout")
                
                // Clear Unity specific stuff
                clearUnityAuthentication(context)
            } catch (e: Exception) {
                android.util.Log.e("GunduAtaViewModel", "Background session logout failed: ${e.message}")
            }
        }
    }

    fun clearUnityAuthentication(context: android.content.Context) {
        try {
            // Clear Unity PlayerPrefs for standalone app
            val standalonePackageName = "com.company.dicegame"
            val unityPrefsName = "$standalonePackageName.v2.playerprefs"
            
            // Check if context is valid
            val appContext = context.applicationContext ?: context
            
            val unityPrefs = appContext.getSharedPreferences(unityPrefsName, android.content.Context.MODE_PRIVATE)
            
            try {
                unityPrefs.edit().clear().apply()
            } catch (e: Exception) {
                android.util.Log.e("GunduAtaViewModel", "Failed to clear unityPrefs: ${e.message}")
            }

            // Also set logout flag for Unity
            try {
                unityPrefs.edit()
                    .putString("is_logged_in", "false")
                    .putString("logout_requested", "true")
                    .putLong("logout_timestamp", System.currentTimeMillis())
                    .apply()
            } catch (e: Exception) {
                android.util.Log.e("GunduAtaViewModel", "Failed to set logout flags: ${e.message}")
            }
        } catch (e: Exception) {
            // Ignore errors when clearing Unity prefs
            android.util.Log.e("GunduAtaViewModel", "clearUnityAuthentication failed: ${e.message}")
        }
    }

    fun isNewUser(): Boolean {
        return sessionManager.isNewUser()
    }

    fun markUserAsNew() {
        sessionManager.setNewUser(true)
    }

    fun markUserAsNotNew() {
        sessionManager.setNewUser(false)
    }

    fun checkForUpdates(currentVersionCode: Int) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getAppVersion()
                if (response.isSuccessful) {
                    val data = response.body()
                    val latestVersionCode = (data?.get("version_code") as? Double)?.toInt() ?: (data?.get("version_code") as? Int) ?: 0
                    
                    if (latestVersionCode > currentVersionCode) {
                        latestVersionName = data?.get("version_name") as? String
                        updateUrl = data?.get("download_url") as? String
                        isForceUpdate = data?.get("force_update") as? Boolean ?: false
                        showUpdateDialog = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GunduAtaViewModel", "Update check failed: ${e.message}")
            }
        }
    }

    fun fetchRecentRoundResults(count: Int = 20) {
        // Set loading synchronously to avoid a brief "No results" flicker
        // when the screen first opens (before the coroutine starts).
        isLoading = true
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getRecentRoundResults(count)
                if (response.isSuccessful) {
                    recentResults = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("GunduAtaViewModel", "Fetch recent results failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // Leaderboard and Ranking logic
    var userRank by mutableIntStateOf(0)
    var userRotationMoney by mutableStateOf(0.0)
    var leaderboardPlayers by mutableStateOf<List<Map<String, Any>>>(emptyList())
    var leaderboardPrizes by mutableStateOf<Map<String, String>>(mapOf("1st" to "₹1,000", "2nd" to "₹500", "3rd" to "₹100"))

    fun fetchLeaderboard() {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.apiService.getLeaderboard()
                if (response.isSuccessful) {
                    val data = response.body()
                    val leaderboard = data?.get("leaderboard") as? List<Map<String, Any>> ?: emptyList()
                    leaderboardPlayers = leaderboard
                    
                    val userStats = data?.get("user_stats") as? Map<String, Any>
                    userRank = (userStats?.get("rank") as? Double)?.toInt() ?: (userStats?.get("rank") as? Int) ?: 0
                    userRotationMoney = (userStats?.get("turnover") as? Double) ?: (userStats?.get("turnover") as? Int)?.toDouble() ?: 0.0

                    val prizes = data?.get("prizes") as? Map<String, String>
                    if (prizes != null) {
                        leaderboardPrizes = prizes
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GunduAtaViewModel", "Fetch leaderboard failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun updateUserRotation(amount: Double) {
        // No local turnover updates, wait for API refresh
    }
}
