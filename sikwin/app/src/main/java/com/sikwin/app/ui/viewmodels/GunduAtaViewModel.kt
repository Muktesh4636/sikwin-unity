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
import kotlinx.coroutines.coroutineScope
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
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

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
        val msg = when (e) {
            is java.net.UnknownHostException -> "No internet connection. Please check your network."
            is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
            is java.net.ConnectException -> "Unable to connect to server. Please try again later."
            is retrofit2.HttpException -> "Server error. Please try again later."
            else -> "An unexpected error occurred. Please try again."
        }
        try {
            com.sikwin.app.utils.EventLogger.error(
                name = "viewmodel_exception",
                message = msg,
                throwable = e
            )
        } catch (_: Exception) {
        }
        return msg
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
    var paybitraDeposit by mutableStateOf<PaybitraDepositResponse?>(null)
    var paybitraLoading by mutableStateOf(false)
    var bettingHistory by mutableStateOf<List<Bet>>(emptyList())
    /** Cricket/IPL bets only — from [getCricketBettingHistory], not dice [Betting] history. */
    var cricketBettingHistory by mutableStateOf<List<CricketBetHistoryItem>>(emptyList())
    var cricketBetsLoading by mutableStateOf(false)
    var cricketBetsError by mutableStateOf<String?>(null)
    var referralData by mutableStateOf<ReferralData?>(null)

    /** GET /api/cricket/matches/ */
    var cricketMatches by mutableStateOf<List<CricketMatchSummary>>(emptyList())
    var cricketMatchesLoading by mutableStateOf(false)
    var cricketMatchesError by mutableStateOf<String?>(null)
    var cricketMatchesSyncedAt by mutableStateOf<String?>(null)

    /** GET /api/cricket/upcoming/ */
    var cricketUpcoming by mutableStateOf<List<CricketUpcomingMatch>>(emptyList())
    var cricketUpcomingLoading by mutableStateOf(false)
    var cricketUpcomingError by mutableStateOf<String?>(null)
    var cricketUpcomingSyncedAt by mutableStateOf<String?>(null)

    /** Selected match id — null shows the list. */
    var cricketSelectedMatchId by mutableStateOf<Long?>(null)
    /** True when selection came from upcoming list (use inline odds; detail API may 404). */
    var cricketSelectedFromUpcoming by mutableStateOf(false)

    /** Mapped live event for the selected match (detail + changes). */
    var cricketLive by mutableStateOf<CricketLiveEventData?>(null)
    /** Bumps on each successful odds payload so list keys refresh. */
    var cricketLiveEpoch by mutableStateOf(0L)
    var cricketFetchedAt by mutableStateOf<String?>(null)
    var cricketLoading by mutableStateOf(false)
    var cricketError by mutableStateOf<String?>(null)
    var cricketBetPlacing by mutableStateOf(false)
    /** True only after several consecutive failed polls. */
    var cricketPollStopped by mutableStateOf(false)

    /** Score ticker — GET /api/cricket/scores/, keyed by match id in [cricketScoreById]. */
    var cricketScoreById by mutableStateOf<Map<Long, CricketMatchSummary>>(emptyMap())
    var cricketScore by mutableStateOf<CricketScorePayload?>(null)
    var cricketScoreFetchedAt by mutableStateOf<String?>(null)
    var cricketScoreError by mutableStateOf<String?>(null)

    /** Bookmark for GET /api/cricket/changes/?bn=N */
    var cricketChangesBn by mutableStateOf(0L)
    var cricketChangesError by mutableStateOf<String?>(null)
    /** True while initial matches/upcoming/scores are being prefetched on open. */
    var isCricketPrefetching by mutableStateOf(false)

    private var cricketPrefetchJob: Job? = null

    /** Call when entering Cricket; [stopCricketSession] when leaving. */
    fun startCricketSession() {
        cricketPrefetchJob?.cancel()
        isCricketPrefetching = true
        fetchWallet()
        cricketPrefetchJob = viewModelScope.launch {
            try {
                coroutineScope {
                    launch { cricketFetchMatchesOnce() }
                    launch { cricketFetchUpcomingOnce() }
                    launch { cricketFetchScoresOnce() }
                }
            } finally {
                isCricketPrefetching = false
            }
        }
    }

    fun stopCricketSession() {
        cricketPrefetchJob?.cancel()
        cricketPrefetchJob = null
        isCricketPrefetching = false
        clearCricketMatchSelection()
    }

    /** Fetch match list. */
    suspend fun cricketFetchMatchesOnce(): Boolean {
        val showSpinner = cricketMatches.isEmpty() && cricketMatchesError == null
        if (showSpinner) cricketMatchesLoading = true
        return try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getCricketMatches()
            }
            if (resp.isSuccessful) {
                val body = resp.body()
                val list = body?.matches.orEmpty()
                cricketMatches = list
                cricketMatchesSyncedAt = body?.last_sync
                cricketMatchesError = if (list.isEmpty()) "No live matches right now." else null
                true
            } else {
                val err = resp.errorBody()?.string()
                if (cricketMatches.isEmpty()) cricketMatchesError = parseError(err)
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("CricketMatches", "Failed", e)
            if (cricketMatches.isEmpty()) {
                cricketMatchesError = handleException(e)
            }
            false
        } finally {
            if (showSpinner) cricketMatchesLoading = false
        }
    }

    /** Fetch upcoming / pre-match list. */
    suspend fun cricketFetchUpcomingOnce(): Boolean {
        val showSpinner = cricketUpcoming.isEmpty() && cricketUpcomingError == null
        if (showSpinner) cricketUpcomingLoading = true
        return try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getCricketUpcoming()
            }
            if (resp.isSuccessful) {
                val body = resp.body()
                val list = body?.matches.orEmpty()
                cricketUpcoming = list
                cricketUpcomingSyncedAt = body?.last_sync
                cricketUpcomingError = if (list.isEmpty()) "No upcoming matches right now." else null
                // If user is viewing an upcoming match, refresh inline odds.
                val sel = cricketSelectedMatchId
                if (cricketSelectedFromUpcoming && sel != null) {
                    list.firstOrNull { it.id == sel }?.let { u ->
                        cricketLive = u.toLiveEvent()
                        cricketLiveEpoch++
                        cricketFetchedAt = body?.last_sync
                        cricketError = null
                    }
                }
                true
            } else {
                val err = resp.errorBody()?.string()
                if (cricketUpcoming.isEmpty()) cricketUpcomingError = parseError(err)
                // Public cricket reads should not log the user out on stale tokens.
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("CricketUpcoming", "Failed", e)
            if (cricketUpcoming.isEmpty()) {
                cricketUpcomingError = handleException(e)
            }
            false
        } finally {
            if (showSpinner) cricketUpcomingLoading = false
        }
    }

    /** Fetch scores ticker and refresh scorecard for the selected match. */
    suspend fun cricketFetchScoresOnce(): Boolean {
        return try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getCricketScores()
            }
            if (resp.isSuccessful) {
                val body = resp.body()
                val list = body?.matches.orEmpty()
                cricketScoreById = list.associateBy { it.id }
                cricketScoreFetchedAt = body?.last_sync
                cricketScoreError = null
                applySelectedMatchScore()
                // Keep cricketMatches identity stable — list rows read scores from cricketScoreById.
                // Rewriting the matches list every poll was causing scroll jank.
                true
            } else {
                val err = resp.errorBody()?.string()
                if (cricketScore == null) cricketScoreError = parseError(err)
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("CricketScores", "Failed", e)
            if (cricketScore == null) cricketScoreError = handleException(e)
            false
        }
    }

    private fun applySelectedMatchScore() {
        val id = cricketSelectedMatchId ?: return
        val row = cricketScoreById[id]
            ?: cricketMatches.firstOrNull { it.id == id }
        cricketScore = row?.toScorePayload()
    }

    fun selectCricketMatch(matchId: Long, fromUpcoming: Boolean = false) {
        if (cricketSelectedMatchId == matchId && cricketSelectedFromUpcoming == fromUpcoming) return
        cricketSelectedMatchId = matchId
        cricketSelectedFromUpcoming = fromUpcoming
        cricketLive = null
        cricketLiveEpoch = 0L
        cricketError = null
        cricketPollStopped = false
        cricketChangesBn = 0L
        cricketChangesError = null
        if (fromUpcoming) {
            cricketScore = null
            cricketScoreError = null
            val upcoming = cricketUpcoming.firstOrNull { it.id == matchId }
            if (upcoming != null) {
                cricketLive = upcoming.toLiveEvent()
                cricketLiveEpoch++
                cricketFetchedAt = cricketUpcomingSyncedAt
                cricketError = null
                cricketLoading = false
            } else {
                cricketError = "Could not load upcoming match."
            }
            return
        }
        applySelectedMatchScore()
        viewModelScope.launch {
            cricketFetchMatchDetailOnce(matchId)
        }
    }

    fun clearCricketMatchSelection() {
        cricketSelectedMatchId = null
        cricketSelectedFromUpcoming = false
        cricketLive = null
        cricketFetchedAt = null
        cricketLiveEpoch = 0L
        cricketError = null
        cricketPollStopped = false
        cricketScore = null
        cricketChangesBn = 0L
        cricketChangesError = null
    }

    /**
     * GET /api/cricket/matches/{id}/ — full odds.
     * Returns true when markets are loaded.
     */
    suspend fun cricketFetchMatchDetailOnce(matchId: Long = cricketSelectedMatchId ?: 0L): Boolean {
        if (matchId <= 0L) return false
        val showSpinner = cricketLive == null && cricketError == null
        if (showSpinner) cricketLoading = true
        return try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getCricketMatch(matchId)
            }
            if (resp.isSuccessful) {
                val body = resp.body()
                val detail = body?.match
                cricketFetchedAt = body?.last_sync
                if (detail != null) {
                    cricketLive = detail.toLiveEvent()
                    cricketLiveEpoch++
                    cricketError = null
                    // Prefer detail live/scores immediately; scores poll will refresh further.
                    cricketScore = detail.toScorePayload()
                    cricketScoreFetchedAt = body?.last_sync ?: cricketScoreFetchedAt
                    cricketScoreError = null
                    true
                } else {
                    clearCricketLive("No match data")
                    false
                }
            } else {
                logoutIfUnauthorized(resp.code())
                val err = resp.errorBody()?.string()
                val msg = parseError(err)
                android.util.Log.w("CricketMatch", "HTTP ${resp.code()} $err")
                clearCricketLive(msg)
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("CricketMatch", "Failed", e)
            clearCricketLive(e.message ?: "Could not load cricket odds.")
            false
        } finally {
            if (showSpinner) cricketLoading = false
        }
    }

    /**
     * GET /api/cricket/changes/?bn=N — apply price deltas when available.
     * On upstream failure returns false so the UI can fall back to full detail refresh.
     */
    suspend fun cricketFetchChangesOnce(): Boolean {
        val matchId = cricketSelectedMatchId ?: return false
        return try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getCricketChanges(cricketChangesBn)
            }
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body?.error != null) {
                    cricketChangesError = body.detail ?: body.error
                    return false
                }
                body?.bn?.let { cricketChangesBn = it }
                cricketChangesError = null
                val updatedMarkets = body?.markets
                    ?: body?.events?.firstOrNull { it.id == matchId }?.markets
                    ?: body?.events?.firstOrNull { it.id == matchId }?.odds?.markets
                    ?: body?.matches?.firstOrNull { it.id == matchId }?.odds?.markets
                if (!updatedMarkets.isNullOrEmpty()) {
                    val live = cricketLive
                    if (live != null && live.id == matchId) {
                        cricketLive = live.copy(markets = updatedMarkets.toLiveMarkets())
                        cricketLiveEpoch++
                        cricketFetchedAt = body?.last_sync ?: cricketFetchedAt
                    }
                }
                true
            } else {
                logoutIfUnauthorized(resp.code())
                val err = resp.errorBody()?.string()
                cricketChangesError = parseError(err)
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("CricketChanges", "Failed", e)
            cricketChangesError = handleException(e)
            false
        }
    }

    /** @deprecated Prefer [cricketFetchMatchDetailOnce] / [cricketFetchChangesOnce]. */
    suspend fun cricketFetchOnce(): Boolean {
        val id = cricketSelectedMatchId
        return if (id != null) cricketFetchMatchDetailOnce(id) else cricketFetchMatchesOnce()
    }

    /** @deprecated Prefer [cricketFetchScoresOnce]. */
    suspend fun cricketResultFetchOnce(): Boolean = cricketFetchScoresOnce()

    private fun clearCricketLive(message: String?) {
        cricketLive = null
        cricketFetchedAt = null
        cricketLiveEpoch = 0L
        cricketError = message?.takeIf { it.isNotBlank() } ?: "Could not load cricket odds."
    }

    fun refreshCricketFeed() {
        viewModelScope.launch {
            val id = cricketSelectedMatchId
            when {
                id != null && cricketSelectedFromUpcoming -> cricketFetchUpcomingOnce()
                id != null -> cricketFetchMatchDetailOnce(id)
                else -> {
                    cricketFetchMatchesOnce()
                    cricketFetchUpcomingOnce()
                }
            }
            cricketFetchScoresOnce()
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

    /** Colour game — GET /api/colour/round/ */
    var colourRound by mutableStateOf<ColourRoundResponse?>(null)
    /**
     * Seconds remaining — decrements locally once per second after [initializeColourLocalTimerForRound].
     * Not re-synced from the API on every poll; only seeded when a new [round_id] starts.
     */
    var colourDisplayTimerSeconds by mutableIntStateOf(0)
    /** Initial countdown length for the current local round (progress bar). */
    var colourRoundInitialSeconds by mutableIntStateOf(60)
    /** Result for the current round — fetched when the local timer hits zero. */
    var colourDisplayedResult by mutableStateOf<ColourRoundResultResponse?>(null)
    /** GET /api/colour/bets/ — sorted newest first in [fetchColourBetsHistory]. */
    var colourBetsHistory by mutableStateOf<List<ColourBetHistoryItem>>(emptyList())
    /** GET /api/colour/results/ — global recent results for Colour Game table. */
    var colourPublicResults by mutableStateOf<List<ColourPublicResultItem>>(emptyList())
    /** True while initial round/results are being prefetched on open. */
    var isColourGamePrefetching by mutableStateOf(false)

    private var colourLocalTickJob: Job? = null
    private var colourRoundPollJob: Job? = null
    private var colourResultPollJob: Job? = null
    private var colourPublicResultsPollJob: Job? = null
    /** Avoid duplicate “timer hit zero” handling for the same [ColourRoundResponse.round_id]. */
    private var colourTimerZeroHandledRoundId: String? = null
    /** Round id for which [colourDisplayTimerSeconds] is counting down locally. */
    private var colourLocalRoundId: String? = null

    companion object {
        /** Poll for new rounds and server betting_open (close bets) — not for every-second timer. */
        private const val COLOUR_ROUND_POLL_INTERVAL_MS = 4_000L
        /** Public results refresh while Colour Game is open. */
        private const val COLOUR_PUBLIC_RESULTS_POLL_MS = 4_000L
    }

    /** Call when entering Colour Game; [stopColourGameSession] when leaving. */
    fun startColourGameSession() {
        colourRoundPollJob?.cancel()
        colourResultPollJob?.cancel()
        colourPublicResultsPollJob?.cancel()
        isColourGamePrefetching = true
        startColourLocalTimer()
        // Kick off wallet/history in parallel with round prefetch (non-blocking).
        fetchWallet()
        if (loginSuccess) fetchColourBetsHistory()
        colourRoundPollJob = viewModelScope.launch {
            try {
                // Prefetch critical game data before unlocking the UI.
                coroutineScope {
                    launch { refreshColourRound() }
                    launch { fetchColourPublicResults() }
                }
            } finally {
                isColourGamePrefetching = false
            }
            while (isActive) {
                delay(COLOUR_ROUND_POLL_INTERVAL_MS)
                refreshColourRound()
            }
        }
        colourPublicResultsPollJob = viewModelScope.launch {
            // Initial results fetch is done in the prefetch above.
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
        colourLocalRoundId = null
        isColourGamePrefetching = false
    }

    /** Call when Colour Game screen resumes — updates betting_open / round id; does not reset local timer. */
    fun refreshColourRoundNow() {
        viewModelScope.launch { refreshColourRound() }
    }

    private fun startColourLocalTimer() {
        if (colourLocalTickJob?.isActive == true) return
        colourLocalTickJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val round = colourRound
                if (round?.status?.equals("no_round", ignoreCase = true) == true) continue
                if (colourLocalRoundId.isNullOrBlank()) continue
                if (colourDisplayTimerSeconds <= 0) continue
                val prev = colourDisplayTimerSeconds
                colourDisplayTimerSeconds = prev - 1
                if (colourDisplayTimerSeconds == 0) {
                    onColourDisplayTimerReachedZero()
                }
            }
        }
    }

    private fun onColourDisplayTimerReachedZero() {
        val rid = colourRound?.round_id ?: colourLocalRoundId ?: return
        if (colourTimerZeroHandledRoundId == rid) return
        colourTimerZeroHandledRoundId = rid
        colourResultPollJob?.cancel()
        colourResultPollJob = viewModelScope.launch {
            repeat(36) { attempt ->
                if (attempt > 0) delay(2000)
                val res = fetchColourRoundResult(rid)
                val body = res.getOrNull()
                if (body != null) {
                    colourDisplayedResult = body
                    if (!body.result.isNullOrBlank() || body.number != null) {
                        colourRound = colourRound?.copy(
                            result = body.result ?: colourRound?.result,
                            number = body.number ?: colourRound?.number,
                            betting_open = false
                        )
                        fetchColourPublicResults()
                        if (loginSuccess) fetchColourBetsHistory()
                        return@launch
                    }
                }
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

    /** GET /api/colour/round/ — no auth. Updates betting_open; seeds local timer only on new round_id. */
    suspend fun refreshColourRound() {
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.getColourRound()
            }
            if (response.isSuccessful) {
                val body = response.body()
                colourRound = body
                if (body?.status?.equals("no_round", ignoreCase = true) == true) {
                    colourDisplayTimerSeconds = 0
                    colourRoundInitialSeconds = 60
                    colourLocalRoundId = null
                    colourDisplayedResult = null
                    return
                }
                val newId = body?.round_id
                if (newId.isNullOrBlank()) return

                if (newId != colourLocalRoundId) {
                    initializeColourLocalTimerForRound(body)
                }
            }
        } catch (_: Exception) {
            // Keep last known round; avoid spamming errors while polling.
        }
    }

    /** Seed local countdown once when the server starts a new round. */
    private fun initializeColourLocalTimerForRound(body: ColourRoundResponse) {
        val newId = body.round_id ?: return
        colourLocalRoundId = newId
        colourTimerZeroHandledRoundId = null
        colourDisplayedResult = null
        val remaining = computeColourRoundRemainingSeconds(body)
        colourDisplayTimerSeconds = remaining
        colourRoundInitialSeconds = body.round_duration_seconds?.takeIf { it > 0 }
            ?: remaining.coerceAtLeast(1)
        if (remaining == 0) {
            onColourDisplayTimerReachedZero()
        }
    }

    /**
     * Prefer [ColourRoundResponse.timer]. If missing, derive seconds left from [start_time] and
     * optional [server_time] (else device clock) and [round_duration_seconds] (else peak or 60s).
     */
    private fun computeColourRoundRemainingSeconds(body: ColourRoundResponse?): Int {
        if (body == null) return 0
        body.timer?.let { return it.coerceAtLeast(0) }
        val startMs = parseColourIsoMillis(body.start_time) ?: return 0
        val nowWall = parseColourIsoMillis(body.server_time) ?: System.currentTimeMillis()
        val roundLenSec = body.round_duration_seconds?.takeIf { it > 0 } ?: 60
        val elapsedSec = ((nowWall - startMs) / 1000L).toInt().coerceAtLeast(0)
        return maxOf(0, roundLenSec - elapsedSec)
    }

    private fun parseColourIsoMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(iso).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
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

    fun initiateDeposit(amount: String, method: String = "UPI", onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            paybitraLoading = true
            errorMessage = null
            paybitraDeposit = null
            try {
                // Prefer automatic deposit (unique amount + callback_token) when enabled.
                var usedAuto = false
                try {
                    val modeResp = RetrofitClient.apiService.getDepositMode()
                    if (modeResp.isSuccessful && modeResp.body()?.automatic == true) {
                        val autoResp = RetrofitClient.apiService.initiateAutoDeposit(
                            mapOf("amount" to (amount.toIntOrNull() ?: amount))
                        )
                        if (autoResp.isSuccessful) {
                            paybitraDeposit = autoResp.body()
                            usedAuto = true
                            android.util.Log.d(
                                "GunduAtaViewModel",
                                "auto deposit initiate ok session=${paybitraDeposit?.sessionKey()} tokenPresent=${!paybitraDeposit?.callback_token.isNullOrBlank()}"
                            )
                        } else if (autoResp.code() != 400) {
                            logoutIfUnauthorized(autoResp.code())
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("GunduAtaViewModel", "deposit mode/auto initiate: ${e.message}")
                }

                if (!usedAuto) {
                    val response = RetrofitClient.apiService.initiateDeposit(
                        mapOf("amount" to amount, "method" to method)
                    )
                    if (response.isSuccessful) {
                        paybitraDeposit = response.body()
                    } else {
                        logoutIfUnauthorized(response.code())
                        val msg = parseError(response.errorBody()?.string())
                            .ifEmpty { "Could not start deposit. Please try again." }
                        errorMessage = msg
                        onError?.invoke(msg)
                    }
                }
            } catch (e: Exception) {
                val msg = handleException(e)
                errorMessage = msg
                onError?.invoke(msg)
            } finally {
                paybitraLoading = false
            }
        }
    }

    fun clearPaybitraDeposit() {
        paybitraDeposit = null
        depositPollingActive = false
    }

    private var depositPollingActive = false

    /** SUCCESS from UPI app — POST session_id + utr + txn_id + callback_token. */
    fun submitUpiCallback(
        sessionId: String,
        utr: String,
        txnId: String,
        status: String,
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val session = paybitraDeposit
                val payload = mutableMapOf(
                    "session_id" to sessionId,
                    "utr" to utr,
                    "txn_id" to txnId,
                    "status" to status
                )
                // Required by backend auto-deposit callback (survives JWT expiry in PhonePe)
                session?.callback_token?.takeIf { it.isNotBlank() }?.let {
                    payload["callback_token"] = it
                }
                session?.payAmount()?.takeIf { it.isNotBlank() }?.let { payload["amount"] = it }
                session?.paybitra_order_id?.takeIf { it.isNotBlank() }?.let {
                    payload["paybitra_order_id"] = it
                }
                session?.code?.let { payload["paybitra_code"] = it }

                android.util.Log.d(
                    "GunduAtaViewModel",
                    "upi-callback POST session_id=$sessionId utr=$utr txn_id=$txnId callback_token=${!payload["callback_token"].isNullOrBlank()}"
                )

                var response = RetrofitClient.apiService.upiDepositCallback(payload)
                if (response.code() == 404) {
                    response = RetrofitClient.apiService.upiDepositCallbackAlt(payload)
                }
                if (response.isSuccessful) {
                    val body = response.body()
                    clearPaybitraDeposit()
                    fetchWallet()
                    fetchDeposits()
                    onSuccess()
                    android.util.Log.d(
                        "GunduAtaViewModel",
                        "upi-callback ok credited=${body?.credited} status=${body?.status}"
                    )
                } else {
                    logoutIfUnauthorized(response.code())
                    if (!utr.isBlank()) {
                        val utrPayload = mutableMapOf(
                            "amount" to (session?.payAmount() ?: ""),
                            "utr" to utr,
                            "paybitra_order_id" to sessionId
                        )
                        session?.callback_token?.takeIf { it.isNotBlank() }?.let {
                            utrPayload["callback_token"] = it
                        }
                        session?.code?.let { utrPayload["paybitra_code"] = it }
                        val utrResp = RetrofitClient.apiService.submitUtr(utrPayload)
                        if (utrResp.isSuccessful) {
                            clearPaybitraDeposit()
                            fetchWallet()
                            fetchDeposits()
                            onSuccess()
                            return@launch
                        }
                    }
                    val msg = parseError(response.errorBody()?.string())
                        .ifEmpty { "Could not confirm payment. Please wait or upload screenshot." }
                    errorMessage = msg
                    onError?.invoke(msg)
                }
            } catch (e: Exception) {
                val msg = handleException(e)
                errorMessage = msg
                onError?.invoke(msg)
            } finally {
                isLoading = false
            }
        }
    }

    /** Poll deposit list until matching session is APPROVED (SUBMITTED / unknown UPI result). */
    fun startDepositStatusPolling(
        sessionId: String,
        amount: String,
        onApproved: () -> Unit,
        onTimeout: (() -> Unit)? = null
    ) {
        if (depositPollingActive) return
        depositPollingActive = true
        viewModelScope.launch {
            val deadline = System.currentTimeMillis() + 10 * 60_000L
            try {
                while (depositPollingActive && System.currentTimeMillis() < deadline) {
                    delay(4000)
                    try {
                        // Prefer auto-deposit session status when we have a numeric session id
                        if (sessionId.toIntOrNull() != null) {
                            val auto = RetrofitClient.apiService.getAutoDepositStatus(sessionId)
                            if (auto.isSuccessful) {
                                val st = (auto.body()?.status ?: "").uppercase()
                                if (st in setOf("CREDITED", "APPROVED", "SUCCESS", "COMPLETED")) {
                                    depositPollingActive = false
                                    clearPaybitraDeposit()
                                    fetchWallet()
                                    fetchDeposits()
                                    onApproved()
                                    return@launch
                                }
                            }
                        }
                        val response = RetrofitClient.apiService.getMyDeposits()
                        if (response.isSuccessful) {
                            val list = response.body().orEmpty()
                            depositRequests = list
                            val hit = list.firstOrNull { d ->
                                val ref = (d.payment_reference ?: "") + (d.payment_link ?: "")
                                val status = d.status.uppercase()
                                val amtOk = d.amount.replace(",", "").toDoubleOrNull()
                                    ?.let { kotlin.math.abs(it - (amount.toDoubleOrNull() ?: -1.0)) < 0.01 } == true
                                val sessionOk = sessionId.isNotBlank() && ref.contains(sessionId, ignoreCase = true)
                                status in setOf("APPROVED", "SUCCESS", "CREDITED", "COMPLETED") && (sessionOk || amtOk)
                            }
                            if (hit != null) {
                                depositPollingActive = false
                                clearPaybitraDeposit()
                                fetchWallet()
                                onApproved()
                                return@launch
                            }
                        } else {
                            logoutIfUnauthorized(response.code())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("GunduAtaViewModel", "deposit poll: ${e.message}")
                    }
                }
                if (depositPollingActive) {
                    depositPollingActive = false
                    onTimeout?.invoke()
                }
            } catch (e: Exception) {
                depositPollingActive = false
                android.util.Log.e("GunduAtaViewModel", "deposit polling failed", e)
            }
        }
    }

    fun stopDepositStatusPolling() {
        depositPollingActive = false
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
                val payload = mutableMapOf("amount" to amount, "utr" to utr)
                paybitraDeposit?.let { session ->
                    session.paybitra_order_id?.takeIf { it.isNotBlank() }?.let {
                        payload["paybitra_order_id"] = it
                    }
                    session.sessionKey().takeIf { it.isNotBlank() }?.let {
                        payload["session_id"] = it
                    }
                    session.callback_token?.takeIf { it.isNotBlank() }?.let {
                        payload["callback_token"] = it
                    }
                    session.code?.let { payload["paybitra_code"] = it }
                }
                val response = RetrofitClient.apiService.submitUtr(payload)
                if (response.isSuccessful) {
                    clearPaybitraDeposit()
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
        paybitraDeposit = null
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
    var leaderboardPrizes by mutableStateOf<Map<String, String>>(
        mapOf(
            "1st" to "₹3,000", "2nd" to "₹1,500", "3rd" to "₹1,000",
            "4th" to "₹750", "5th" to "₹500", "6th" to "₹300", "7th" to "₹100"
        )
    )

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

                    val prizesRaw = data?.get("prizes")
                    if (prizesRaw is Map<*, *>) {
                        val merged = leaderboardPrizes.toMutableMap()
                        for ((k, v) in prizesRaw) {
                            if (k is String && v != null) merged[k] = v.toString()
                        }
                        leaderboardPrizes = merged
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
