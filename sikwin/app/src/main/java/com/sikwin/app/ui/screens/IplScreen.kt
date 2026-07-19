@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.sikwin.app.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SportsCricket
import java.util.Calendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.data.models.CricketLiveMarket
import com.sikwin.app.data.models.CricketLiveOutcome
import com.sikwin.app.data.models.CricketMatchSummary
import com.sikwin.app.data.models.CricketUpcomingMatch
import com.sikwin.app.data.models.CricketBatsmanRow
import com.sikwin.app.data.models.CricketBowlerRow
import com.sikwin.app.data.models.CricketInningsScore
import com.sikwin.app.data.models.CricketScorePayload
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import java.text.SimpleDateFormat
import java.util.TimeZone
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.CardGiftcard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import com.sikwin.app.ui.theme.CricketAccentGold
import com.sikwin.app.ui.theme.CricketChipBorder
import com.sikwin.app.ui.theme.CricketHeaderBg
import com.sikwin.app.ui.theme.CricketMarketBg
import com.sikwin.app.ui.theme.CricketOutcomeBlue
import com.sikwin.app.ui.theme.CricketOutcomeRed
import com.sikwin.app.ui.theme.CricketOutcomeText
import com.sikwin.app.ui.theme.CricketScreenBg
import com.sikwin.app.ui.theme.CricketTextMuted
import com.sikwin.app.ui.theme.TextWhite
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import com.sikwin.app.R
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private const val CRICKET_CHANGES_POLL_MS = 3000L
/** Full match-detail refresh when changes upstream is down. */
private const val CRICKET_DETAIL_FALLBACK_POLL_MS = 8000L
/** Match list refresh while browsing. */
private const val CRICKET_MATCHES_POLL_MS = 15000L
/** Upcoming list refresh. */
private const val CRICKET_UPCOMING_POLL_MS = 30000L
/** Live score ticker from GET /api/cricket/scores/ */
private const val CRICKET_SCORE_POLL_MS = 5000L
/** Blur only after this many failed polls in a row (transient errors won't stop updates). */
private const val CRICKET_POLL_FAILURES_BEFORE_BLUR = 5

private enum class CricketListTab {
    Live,
    Upcoming
}

/** Slight blur on odds when live polling has stopped after a failed fetch (API 31+); softer alpha on older APIs. */
private fun Modifier.oddsBlurIf(blur: Boolean): Modifier = when {
    !blur -> this
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> this.blur(5.dp)
    else -> this.graphicsLayer { alpha = 0.55f }
}

private enum class IplMatchTab {
    Live,
    Scoreboard
}

@Composable
private fun IplMatchNameTitle(text: String) {
    Text(
        text = text,
        color = TextWhite,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
}

private data class CricketBetPick(
    val eventId: Long,
    val marketId: Long,
    val marketName: String,
    val outcomeId: Long,
    val outcomeLabel: String,
    val oddsDisplay: String
)

@Composable
fun IplScreen(
    viewModel: GunduAtaViewModel,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var betPick by remember { mutableStateOf<CricketBetPick?>(null) }
    var stakeText by remember { mutableStateOf("100") }
    // Default to All; filter indices match CricketMarketFilter (0=All, 1=Main, …)
    var filterIndex by remember { mutableIntStateOf(0) }
    var pollSession by remember { mutableIntStateOf(0) }
    /** null = both panels collapsed; tap a tab to open, tap again to close. Default: Scoreboard open. */
    var iplMatchTab by remember { mutableStateOf<IplMatchTab?>(IplMatchTab.Scoreboard) }
    var listTab by remember { mutableStateOf(CricketListTab.Live) }
    var autoSwitchedToUpcoming by remember { mutableStateOf(false) }
    val selectedMatchId = viewModel.cricketSelectedMatchId
    val fromUpcoming = viewModel.cricketSelectedFromUpcoming

    fun restartCricketPolling() {
        pollSession++
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearCricketMatchSelection() }
    }

    BackHandler(enabled = selectedMatchId != null) {
        viewModel.clearCricketMatchSelection()
        iplMatchTab = IplMatchTab.Scoreboard
        filterIndex = 0
    }

    // If live list loads empty, default to Upcoming once.
    LaunchedEffect(viewModel.cricketMatches, viewModel.cricketMatchesLoading, viewModel.cricketUpcoming) {
        if (
            !autoSwitchedToUpcoming &&
            !viewModel.cricketMatchesLoading &&
            viewModel.cricketMatches.isEmpty() &&
            viewModel.cricketUpcoming.isNotEmpty() &&
            listTab == CricketListTab.Live
        ) {
            listTab = CricketListTab.Upcoming
            autoSwitchedToUpcoming = true
        }
    }

    // Live match list polling
    LaunchedEffect(pollSession, selectedMatchId == null) {
        if (selectedMatchId != null) return@LaunchedEffect
        viewModel.fetchWallet()
        viewModel.cricketMatchesError = null
        while (true) {
            val start = SystemClock.elapsedRealtime()
            viewModel.cricketFetchMatchesOnce()
            val elapsed = SystemClock.elapsedRealtime() - start
            delay((CRICKET_MATCHES_POLL_MS - elapsed).coerceAtLeast(0L))
        }
    }

    // Upcoming list polling
    LaunchedEffect(pollSession, selectedMatchId == null) {
        if (selectedMatchId != null && !fromUpcoming) return@LaunchedEffect
        while (true) {
            val start = SystemClock.elapsedRealtime()
            viewModel.cricketFetchUpcomingOnce()
            val elapsed = SystemClock.elapsedRealtime() - start
            delay((CRICKET_UPCOMING_POLL_MS - elapsed).coerceAtLeast(0L))
        }
    }

    // Scores ticker (list + live detail)
    LaunchedEffect(pollSession) {
        while (true) {
            val start = SystemClock.elapsedRealtime()
            viewModel.cricketFetchScoresOnce()
            val elapsed = SystemClock.elapsedRealtime() - start
            delay((CRICKET_SCORE_POLL_MS - elapsed).coerceAtLeast(0L))
        }
    }

    // Live detail: changes every 3s, fall back to full detail refresh when changes fails
    LaunchedEffect(pollSession, selectedMatchId, fromUpcoming) {
        val matchId = selectedMatchId ?: return@LaunchedEffect
        if (fromUpcoming) return@LaunchedEffect
        viewModel.fetchWallet()
        viewModel.cricketPollStopped = false
        viewModel.cricketError = null
        viewModel.cricketFetchMatchDetailOnce(matchId)
        var consecutiveFailures = 0
        var lastDetailRefresh = 0L
        while (true) {
            val start = SystemClock.elapsedRealtime()
            val changesOk = viewModel.cricketFetchChangesOnce()
            if (changesOk) {
                consecutiveFailures = 0
                viewModel.cricketPollStopped = false
            } else {
                consecutiveFailures++
                viewModel.cricketPollStopped =
                    consecutiveFailures >= CRICKET_POLL_FAILURES_BEFORE_BLUR &&
                    viewModel.cricketLive != null
                val now = SystemClock.elapsedRealtime()
                if (now - lastDetailRefresh >= CRICKET_DETAIL_FALLBACK_POLL_MS) {
                    if (viewModel.cricketFetchMatchDetailOnce(matchId)) {
                        consecutiveFailures = 0
                        viewModel.cricketPollStopped = false
                    }
                    lastDetailRefresh = now
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            delay((CRICKET_CHANGES_POLL_MS - elapsed).coerceAtLeast(0L))
        }
    }

    // Upcoming detail: refresh inline odds from upcoming list
    LaunchedEffect(pollSession, selectedMatchId, fromUpcoming) {
        if (selectedMatchId == null || !fromUpcoming) return@LaunchedEffect
        while (true) {
            val start = SystemClock.elapsedRealtime()
            viewModel.cricketFetchUpcomingOnce()
            val elapsed = SystemClock.elapsedRealtime() - start
            delay((CRICKET_UPCOMING_POLL_MS - elapsed).coerceAtLeast(0L))
        }
    }

    betPick?.let { pick ->
        AlertDialog(
            onDismissRequest = { betPick = null },
            title = { Text("Place bet", color = TextWhite) },
            text = {
                Column {
                    Text(
                        "${pick.outcomeLabel} @ ${pick.oddsDisplay}",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(pick.marketName, color = CricketTextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = stakeText,
                        onValueChange = { stakeText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Stake", color = CricketTextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = CricketAccentGold,
                            unfocusedBorderColor = CricketChipBorder,
                            cursorColor = CricketAccentGold
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!viewModel.loginSuccess) {
                            Toast.makeText(context, "Sign in to place a bet.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val stake = stakeText.toIntOrNull() ?: 0
                        if (stake <= 0) {
                            Toast.makeText(context, "Enter a valid stake.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        viewModel.placeCricketBet(
                            eventId = pick.eventId,
                            marketId = pick.marketId,
                            outcomeId = pick.outcomeId,
                            stake = stake
                        ) { err ->
                            if (err == null) {
                                Toast.makeText(context, "Bet placed.", Toast.LENGTH_SHORT).show()
                                betPick = null
                            } else {
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !viewModel.cricketBetPlacing
                ) {
                    Text(
                        if (viewModel.cricketBetPlacing) "Placing…" else "Place bet",
                        color = CricketAccentGold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { betPick = null }, enabled = !viewModel.cricketBetPlacing) {
                    Text("Cancel", color = CricketTextMuted)
                }
            },
            containerColor = CricketMarketBg
        )
    }

    Scaffold(
        topBar = {
            CricketTopBar(
                balance = viewModel.wallet?.balance ?: "0.00",
                isLoggedIn = viewModel.loginSuccess,
                onWalletOrDeposit = { onNavigate("deposit") },
                onLogin = { onNavigate("login") },
                onBettingHistory = {
                    if (!viewModel.loginSuccess) onNavigate("login")
                    else onNavigate("cricket_betting_record")
                }
            )
        },
        bottomBar = {
            CricketBottomNavBar(
                onHome = { onNavigate("home") },
                onPromotions = { onNavigate("affiliate") },
                onCasino = {
                    if (!viewModel.loginSuccess) onNavigate("login")
                    else onNavigate("casino_games")
                },
                onProfile = {
                    if (!viewModel.loginSuccess) onNavigate("login")
                    else onNavigate("me")
                }
            )
        },
        containerColor = CricketScreenBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CricketScreenBg)
        ) {
            if (selectedMatchId == null) {
                CricketMatchListContent(
                    viewModel = viewModel,
                    listTab = listTab,
                    onListTab = { listTab = it },
                    onRetry = { restartCricketPolling() },
                    onSelectLiveMatch = { id ->
                        filterIndex = 0
                        iplMatchTab = IplMatchTab.Scoreboard
                        viewModel.selectCricketMatch(id, fromUpcoming = false)
                    },
                    onSelectUpcomingMatch = { id ->
                        filterIndex = 0
                        iplMatchTab = null
                        viewModel.selectCricketMatch(id, fromUpcoming = true)
                    }
                )
            } else {
                CricketMatchDetailContent(
                    viewModel = viewModel,
                    filterIndex = filterIndex,
                    onFilterIndex = { filterIndex = it },
                    iplMatchTab = iplMatchTab,
                    onIplMatchTab = { iplMatchTab = it },
                    onRetry = { restartCricketPolling() },
                    onBetPick = { pick ->
                        betPick = pick
                        stakeText = "100"
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CricketMatchListContent(
    viewModel: GunduAtaViewModel,
    listTab: CricketListTab,
    onListTab: (CricketListTab) -> Unit,
    onRetry: () -> Unit,
    onSelectLiveMatch: (Long) -> Unit,
    onSelectUpcomingMatch: (Long) -> Unit
) {
    val liveMatches = viewModel.cricketMatches
    val upcomingMatches = viewModel.cricketUpcoming
    /** Leagues start open; keys here are collapsed. Multiple can stay open. */
    var collapsedLeagues by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = if (listTab == CricketListTab.Upcoming) 1 else 0,
        pageCount = { 2 }
    )
    // Drive tab highlight from pager so swipe in either direction stays in sync
    val selectedTab =
        if (pagerState.currentPage == 0) CricketListTab.Live else CricketListTab.Upcoming
    val liveListState = rememberLazyListState()
    val upcomingListState = rememberLazyListState()
    val listScrolling by remember {
        derivedStateOf {
            liveListState.isScrollInProgress || upcomingListState.isScrollInProgress
        }
    }
    // Freeze live score overlays while the user is scrolling to avoid mid-fling recomposition jumps
    var frozenScoreById by remember { mutableStateOf<Map<Long, CricketMatchSummary>?>(null) }
    LaunchedEffect(listScrolling, viewModel.cricketScoreById) {
        if (listScrolling) {
            if (frozenScoreById == null) frozenScoreById = viewModel.cricketScoreById
        } else {
            frozenScoreById = null
        }
    }
    val scoreById = frozenScoreById ?: viewModel.cricketScoreById

    fun toggleLeague(key: String) {
        collapsedLeagues = if (key in collapsedLeagues) {
            collapsedLeagues - key
        } else {
            collapsedLeagues + key
        }
    }

    // Swipe → keep parent listTab in sync (do not compare against stale listTab)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onListTab(if (page == 0) CricketListTab.Live else CricketListTab.Upcoming)
            }
    }

    // Tab tap / auto-switch → animate pager (skip while user is actively swiping)
    LaunchedEffect(listTab) {
        val target = if (listTab == CricketListTab.Live) 0 else 1
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    val liveGrouped = remember(liveMatches) {
        liveMatches
            .groupBy { it.leagueLabel() }
            .mapValues { (_, matches) -> matches.sortedBy { it.marketCount() <= 0 } }
            .entries
            .sortedBy { (_, matches) -> matches.none { it.marketCount() > 0 } }
    }
    val upcomingGrouped = remember(upcomingMatches) {
        upcomingMatches.groupBy { it.leagueLabel() }.entries.toList()
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
    ) {
        CricketLiveUpcomingTabs(
            selected = selectedTab,
            liveCount = liveMatches.size,
            upcomingCount = upcomingMatches.size,
            onSelect = { tab ->
                onListTab(tab)
                scope.launch {
                    pagerState.animateScrollToPage(
                        if (tab == CricketListTab.Live) 0 else 1
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            // Avoid starting adjacent-page composition mid-gesture (helps vertical scroll feel)
            beyondBoundsPageCount = 0,
            userScrollEnabled = !listScrolling
        ) { page ->
            when (page) {
                0 -> CricketMatchListPage(
                    loading = viewModel.cricketMatchesLoading && liveMatches.isEmpty(),
                    loadingLabel = "Loading live matches…",
                    error = viewModel.cricketMatchesError.takeIf { liveMatches.isEmpty() },
                    onRetry = onRetry,
                    listState = liveListState
                ) {
                    if (liveMatches.isEmpty()) {
                        item(key = "live_empty") {
                            Text(
                                text = "No live matches right now. Check Upcoming.",
                                color = CricketTextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                            )
                        }
                    } else {
                        liveGrouped.forEach { (league, matches) ->
                            val leagueKey = "live:$league"
                            val open = leagueKey !in collapsedLeagues
                            item(key = "hdr_$leagueKey") {
                                CricketLeagueHeader(
                                    title = league,
                                    matchCount = matches.size,
                                    expanded = open,
                                    onClick = { toggleLeague(leagueKey) }
                                )
                            }
                            if (open) {
                                itemsIndexed(
                                    items = matches,
                                    key = { _, m -> "live_match_${m.id}" }
                                ) { index, match ->
                                    val row = scoreById[match.id] ?: match
                                    Column(modifier = Modifier.background(BetstrikeMatchPanel)) {
                                        CricketLiveScoreMatchRow(
                                            match = row,
                                            onClick = { onSelectLiveMatch(match.id) }
                                        )
                                        if (index < matches.lastIndex) {
                                            HorizontalDivider(
                                                color = BetstrikeDivider,
                                                thickness = 1.dp
                                            )
                                        }
                                    }
                                }
                            }
                            item(key = "gap_$leagueKey") {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }

                else -> CricketMatchListPage(
                    loading = viewModel.cricketUpcomingLoading && upcomingMatches.isEmpty(),
                    loadingLabel = "Loading upcoming…",
                    error = viewModel.cricketUpcomingError.takeIf { upcomingMatches.isEmpty() },
                    onRetry = onRetry,
                    listState = upcomingListState
                ) {
                    if (upcomingMatches.isEmpty()) {
                        item(key = "up_empty") {
                            Text(
                                text = "No upcoming matches right now.",
                                color = CricketTextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                            )
                        }
                    } else {
                        upcomingGrouped.forEach { (league, matches) ->
                            val leagueKey = "up:$league"
                            val open = leagueKey !in collapsedLeagues
                            item(key = "hdr_$leagueKey") {
                                CricketLeagueHeader(
                                    title = league,
                                    matchCount = matches.size,
                                    expanded = open,
                                    onClick = { toggleLeague(leagueKey) }
                                )
                            }
                            if (open) {
                                itemsIndexed(
                                    items = matches,
                                    key = { _, m -> "up_match_${m.id}" }
                                ) { index, match ->
                                    val title = match.match?.trim()?.takeIf { it.isNotEmpty() } ?: "Match"
                                    val (teamA, teamB) = splitCricketMatchTeams(title)
                                    Column(modifier = Modifier.background(BetstrikeMatchPanel)) {
                                        CricketBetstrikeMatchRow(
                                            teamA = teamA,
                                            teamB = teamB,
                                            centerTop = "VS",
                                            centerBottom = formatCricketUpcomingDate(match.date) ?: "TBD",
                                            countryHint = listOfNotNull(match.country, match.competition)
                                                .joinToString(" ")
                                                .ifBlank { null },
                                            onClick = { onSelectUpcomingMatch(match.id) }
                                        )
                                        if (index < matches.lastIndex) {
                                            HorizontalDivider(
                                                color = BetstrikeDivider,
                                                thickness = 1.dp
                                            )
                                        }
                                    }
                                }
                            }
                            item(key = "gap_$leagueKey") {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CricketMatchListPage(
    loading: Boolean,
    loadingLabel: String,
    error: String?,
    onRetry: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    listContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    when {
        loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CricketAccentGold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        loadingLabel,
                        color = CricketTextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }

        error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = error,
                    color = CricketTextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CricketChipBorder)
                ) {
                    Text("Retry", color = CricketAccentGold)
                }
            }
        }

        else -> {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize(),
                content = listContent
            )
        }
    }
}

private val BetstrikeLeagueBar = Color(0xFF1C1C1C)
private val BetstrikeMatchPanel = Color(0xFF0A0A0A)
private val BetstrikeDivider = Color(0xFF2A2A2A)
private val BetstrikeMuted = Color(0xFF8E8E8E)
/** Soft steel — not bright yellow — for league headers. */
private val BetstrikeLeagueAccent = Color(0xFF8FB4C9)
private val BetstrikeLeagueBadge = Color(0xFF2A3A45)
private val BetstrikeLeagueTitle = Color(0xFFE8EEF2)

@Composable
private fun CricketBottomNavBar(
    onHome: () -> Unit,
    onPromotions: () -> Unit,
    onCasino: () -> Unit,
    onProfile: () -> Unit
) {
    // Same gold yellow as DualCards home bottom nav
    val activeGold = DualGoldMid
    val idleGold = DualGoldDeep.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CricketBottomNavItem("HOME", Icons.Filled.Home, idleGold, onClick = onHome)
            CricketBottomNavItem("PROMOTIONS", Icons.Filled.CardGiftcard, idleGold, onClick = onPromotions)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .offset(y = (-8).dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DualGoldDeep.copy(alpha = 0.25f))
                        .border(BorderStroke(1.5.dp, activeGold), CircleShape)
                        .clickable(onClick = { /* already on cricket */ }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsCricket,
                        contentDescription = "Cricket",
                        tint = activeGold,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = "CRICKET",
                    color = activeGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-4).dp)
                )
            }
            CricketBottomNavItem("CASINO", Icons.Filled.Casino, idleGold, onClick = onCasino)
            CricketBottomNavItem("PROFILE", Icons.Filled.Person, idleGold, onClick = onProfile)
        }
    }
}

@Composable
private fun CricketBottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun CricketLeagueHeader(
    title: String,
    matchCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = BetstrikeLeagueBar,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(BetstrikeLeagueAccent)
            )
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BetstrikeLeagueBadge),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SportsCricket,
                    contentDescription = null,
                    tint = BetstrikeLeagueAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = title.uppercase(Locale.US),
                color = BetstrikeLeagueTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            Text(
                text = if (matchCount == 1) "1 MATCH" else "$matchCount MATCHES",
                color = BetstrikeMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = BetstrikeLeagueAccent,
                modifier = Modifier
                    .padding(start = 4.dp, end = 10.dp)
                    .size(22.dp)
            )
        }
    }
}

@Composable
private fun CricketLeagueMatchPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BetstrikeMatchPanel),
        content = content
    )
}

@Composable
private fun CricketBetstrikeMatchRow(
    teamA: String,
    teamB: String,
    centerTop: String,
    centerBottom: String,
    countryHint: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            CricketTeamFlag(teamName = teamA, countryHint = countryHint, size = 24.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = teamA,
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(0.85f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = centerTop,
                color = BetstrikeMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = centerBottom,
                color = BetstrikeMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = teamB,
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            CricketTeamFlag(teamName = teamB, countryHint = countryHint, size = 24.dp)
        }
    }
}

/** Live list row: team names + scores visible without opening the match. */
@Composable
private fun CricketLiveScoreMatchRow(
    match: CricketMatchSummary,
    onClick: () -> Unit
) {
    val scores = match.scores.orEmpty()
    val teamA = scores.getOrNull(0)
    val teamB = scores.getOrNull(1)
    val (fallbackA, fallbackB) = liveMatchTeams(match)
    val nameA = teamA?.team?.takeIf { it.isNotBlank() } ?: fallbackA
    val nameB = teamB?.team?.takeIf { it.isNotBlank() } ?: fallbackB
    val scoreA = teamA?.score?.takeIf { it.isNotBlank() } ?: "—"
    val scoreB = teamB?.score?.takeIf { it.isNotBlank() } ?: "—"
    val battingA = teamA?.batting == true
    val battingB = teamB?.batting == true
    val period = match.period?.takeIf { it.isNotBlank() } ?: "Live"
    val overs = match.live?.oversLabel()?.let { "$it Ov" }
    val countryHint = match.country ?: match.competition

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CricketTeamFlag(teamName = nameA, countryHint = countryHint, size = 22.dp)
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = nameA,
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (battingA) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(BetstrikeLeagueAccent)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scoreA,
                color = BetstrikeLeagueTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 29.dp)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(0.9f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = "LIVE",
                color = Color(0xFFE53935),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = period,
                color = BetstrikeMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (overs != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = overs,
                    color = BetstrikeMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (battingB) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(BetstrikeLeagueAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = nameB,
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(7.dp))
                CricketTeamFlag(teamName = nameB, countryHint = countryHint, size = 22.dp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scoreB,
                color = BetstrikeLeagueTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(end = 29.dp)
            )
        }
    }
}

@Composable
private fun CricketLiveUpcomingTabs(
    selected: CricketListTab,
    liveCount: Int,
    upcomingCount: Int,
    onSelect: (CricketListTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            Triple(CricketListTab.Live, "LIVE ($liveCount)", Icons.Filled.LiveTv),
            Triple(CricketListTab.Upcoming, "UPCOMING ($upcomingCount)", Icons.Filled.CalendarMonth)
        ).forEach { (tab, label, icon) ->
            val isSelected = selected == tab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) CricketAccentGold else BetstrikeMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = if (isSelected) CricketAccentGold else BetstrikeMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(2.dp)
                        .background(if (isSelected) CricketAccentGold else Color.Transparent)
                )
            }
        }
    }
}

private fun CricketMatchSummary.leagueLabel(): String =
    competition?.takeIf { it.isNotBlank() }
        ?: country?.takeIf { it.isNotBlank() }
        ?: "Other matches"

private fun CricketUpcomingMatch.leagueLabel(): String =
    competition?.takeIf { it.isNotBlank() }
        ?: country?.takeIf { it.isNotBlank() }
        ?: "Upcoming fixtures"

private fun liveMatchTeams(row: CricketMatchSummary): Pair<String, String> {
    val scores = row.scores.orEmpty()
    if (scores.size >= 2) {
        return (scores[0].team ?: "Team A") to (scores[1].team ?: "Team B")
    }
    val title = row.match?.trim()?.takeIf { it.isNotEmpty() } ?: "Match"
    return splitCricketMatchTeams(title)
}

/** Split "A vs B" / "A v B" into two team names. */
private fun splitCricketMatchTeams(title: String): Pair<String, String> {
    val parts = title.split(Regex("\\s+vs\\.?\\s+|\\s+v\\.?\\s+", RegexOption.IGNORE_CASE), limit = 2)
    return if (parts.size == 2) {
        parts[0].trim() to parts[1].trim()
    } else {
        title to "TBD"
    }
}

private fun formatCricketUpcomingDate(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    return try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val normalized = s.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        val date = inFmt.parse(normalized) ?: return s
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
        val dayFmt = SimpleDateFormat("dd MMM", Locale.US)
        val timePart = timeFmt.format(date)
        val calMatch = Calendar.getInstance().apply { time = date }
        val calNow = Calendar.getInstance()
        val dayLabel = when {
            isSameDay(calMatch, calNow) -> "Today"
            run {
                calNow.add(Calendar.DAY_OF_YEAR, 1)
                val tomorrow = isSameDay(calMatch, calNow)
                calNow.add(Calendar.DAY_OF_YEAR, -1)
                tomorrow
            } -> "Tomorrow"
            else -> dayFmt.format(date)
        }
        "$dayLabel, $timePart"
    } catch (_: Exception) {
        s.take(16)
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

@Composable
private fun ColumnScope.CricketMatchDetailContent(
    viewModel: GunduAtaViewModel,
    filterIndex: Int,
    onFilterIndex: (Int) -> Unit,
    iplMatchTab: IplMatchTab?,
    onIplMatchTab: (IplMatchTab?) -> Unit,
    onRetry: () -> Unit,
    onBetPick: (CricketBetPick) -> Unit
) {
    val live = viewModel.cricketLive
    when {
        viewModel.cricketLoading && live == null -> {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CricketAccentGold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading odds…", color = CricketTextMuted, fontSize = 14.sp)
                }
            }
        }

        viewModel.cricketError != null && live == null -> {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = viewModel.cricketError ?: "Could not load.",
                    color = CricketTextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CricketChipBorder)
                ) {
                    Text("Retry", color = CricketAccentGold)
                }
            }
        }

        else -> {
            val blurOdds = viewModel.cricketPollStopped && viewModel.cricketLive != null
            val allMarkets = live?.markets.orEmpty()
            val filteredMarkets = remember(allMarkets, filterIndex) {
                CricketMarketFilter.filter(allMarkets, filterIndex)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (viewModel.cricketError != null) {
                    Text(
                        text = viewModel.cricketError!!,
                        color = CricketTextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (live == null) {
                        viewModel.cricketScore?.matchTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                            item {
                                IplMatchNameTitle(cleanMatchTitle(raw).uppercase(Locale.US))
                            }
                        }
                        item {
                            IplMatchStreamOrScoreSection(
                                selectedTab = iplMatchTab,
                                onTabSelect = { tab ->
                                    onIplMatchTab(if (iplMatchTab == tab) null else tab)
                                },
                                score = viewModel.cricketScore,
                                scoreError = viewModel.cricketScoreError
                            )
                        }
                        item {
                            Text(
                                "No odds for this match right now.",
                                color = CricketTextMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    } else {
                        item {
                            val crumb = listOfNotNull(
                                "Cricket",
                                live.competition?.takeIf { it.isNotBlank() },
                                live.period?.takeIf { it.isNotBlank() }
                            ).joinToString("  ›  ")
                            Text(
                                text = crumb,
                                color = CricketTextMuted,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        item {
                            IplMatchNameTitle(
                                live.description?.trim()?.uppercase()
                                    ?.takeIf { it.isNotEmpty() } ?: "MATCH"
                            )
                        }
                        item {
                            IplMatchStreamOrScoreSection(
                                selectedTab = iplMatchTab,
                                onTabSelect = { tab ->
                                    onIplMatchTab(if (iplMatchTab == tab) null else tab)
                                },
                                score = viewModel.cricketScore,
                                scoreError = viewModel.cricketScoreError
                            )
                        }
                        item {
                            Text(
                                text = "Markets & Odds",
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                        }
                        item {
                            CricketFilterChips(
                                markets = allMarkets,
                                selectedIndex = filterIndex,
                                onSelect = onFilterIndex
                            )
                        }
                        item {
                            viewModel.cricketFetchedAt?.takeIf { it.isNotBlank() }?.let { ts ->
                                Text(
                                    text = "Updated $ts",
                                    color = CricketTextMuted.copy(alpha = 0.9f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        if (filteredMarkets.isEmpty()) {
                            item {
                                Text(
                                    text = "No markets in this category.",
                                    color = CricketTextMuted,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            items(
                                items = filteredMarkets,
                                key = { m ->
                                    "${m.id}_${viewModel.cricketLiveEpoch}"
                                }
                            ) { market ->
                                CricketMarketBlock(
                                    market = market,
                                    blurOdds = blurOdds,
                                    onOutcomePick = { m, o ->
                                        onBetPick(
                                            CricketBetPick(
                                                eventId = live.id,
                                                marketId = m.id,
                                                marketName = m.description?.trim()
                                                    ?.takeIf { it.isNotEmpty() } ?: "Market",
                                                outcomeId = o.id,
                                                outcomeLabel = o.displayLabel(),
                                                oddsDisplay = o.displayOdds()
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ScoreCardNavy = Color(0xFF0D1B2A)
private val ScoreCardHeaderBar = Color(0xFFD8E4F0)
private val ScoreCardHeaderTitle = Color(0xFF0D2147)
private val BallFourBg = Color(0xFF2E7D32)
private val BallSixBg = Color(0xFFFF9800)
private val BallNeutralBg = Color(0xFFECEFF1)
private val BallNeutralFg = Color(0xFF263238)

private fun formatOversDisplay(o: Double?): String {
    if (o == null) return ""
    val t = String.format(Locale.US, "%.1f", o).trimEnd('0').trimEnd('.')
    return t.ifEmpty { "0" }
}

/** Cricket overs like 16.2 → balls played (16*6+2). */
private fun oversToBalls(o: Double): Int {
    val whole = floor(o).toInt().coerceAtLeast(0)
    val frac = o - whole
    val ballsInPartial = (frac * 10).roundToInt().coerceIn(0, 5)
    return whole * 6 + ballsInPartial
}

private fun teamAbbrev(teamName: String): String {
    val known = mapOf(
        "Chennai Super Kings" to "CSK",
        "Punjab Kings" to "PK",
        "Mumbai Indians" to "MI",
        "Kolkata Knight Riders" to "KKR",
        "Royal Challengers Bengaluru" to "RCB",
        "Sunrisers Hyderabad" to "SRH",
        "Rajasthan Royals" to "RR",
        "Delhi Capitals" to "DC",
        "Lucknow Super Giants" to "LSG",
        "Gujarat Titans" to "GT"
    )
    val t = teamName.trim()
    known[t]?.let { return it }
    val parts = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size >= 2) {
        return parts.take(2).joinToString("") { "${it.first().uppercaseChar()}" }
    }
    return t.take(4).uppercase(Locale.US)
}

private fun jerseyColor(abbrev: String): Color = when (abbrev) {
    "CSK" -> Color(0xFFFFD600)
    "PK", "PBKS" -> Color(0xFFE53935)
    "MI" -> Color(0xFF004BA0)
    "KKR" -> Color(0xFF3D0B69)
    "RCB" -> Color(0xFFE30314)
    "SRH" -> Color(0xFFFF6F00)
    "RR" -> Color(0xFF254AA5)
    "DC" -> Color(0xFF2563EB)
    "LSG" -> Color(0xFF0A7DE8)
    "GT" -> Color(0xFF1B2A52)
    else -> Color(0xFF546E7A)
}

/** Rough win% for the chasing side (2nd innings); used when API has no explicit model. */
private fun estimateChasingWinPercent(first: CricketInningsScore, second: CricketInningsScore): Float? {
    if (second.conclusion?.equals("In Progress", true) != true) return null
    val goal = (first.runs ?: 0) + 1
    val scored = second.runs ?: 0
    val need = goal - scored
    if (need <= 0) return 0.98f
    val oversCap = second.oversAvailable ?: 20
    val ballsTotal = oversCap * 6
    val ballsPlayed = oversToBalls(second.overs ?: 0.0)
    val ballsLeft = (ballsTotal - ballsPlayed).coerceAtLeast(0)
    if (ballsLeft <= 0) return 0.5f
    val ease = ballsLeft.toFloat() / (need * 1.25f + 0.01f)
    return (0.08f + 0.84f * (ease / (ease + 1.1f))).coerceIn(0.05f, 0.95f)
}

private fun ballChipStyle(raw: String): Pair<Color, Color> {
    val s = raw.trim().lowercase(Locale.US)
    if (s == "4" || s == "4b") return BallFourBg to Color.White
    if (s == "6") return BallSixBg to Color.White
    return BallNeutralBg to BallNeutralFg
}

private fun pickHighlightBowler(bowlers: List<CricketBowlerRow>?): CricketBowlerRow? {
    val b = bowlers.orEmpty()
    b.firstOrNull { it.isActiveBowler == true }?.let { return it }
    b.firstOrNull { it.isOtherBowler == true }?.let { return it }
    return b.filter { (it.overs ?: 0.0) > 0.0 }.maxByOrNull { it.overs ?: 0.0 }
}

private fun cleanMatchTitle(title: String): String =
    title.replace(Regex("\\s*\\([^)]+\\)\\s*$"), "").trim()

@Composable
private fun IplLiveScoreTabRow(
    selected: IplMatchTab?,
    onSelect: (IplMatchTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val liveSel = selected == IplMatchTab.Live
        Surface(
            onClick = { onSelect(IplMatchTab.Live) },
            shape = RoundedCornerShape(10.dp),
            color = CricketMarketBg,
            border = BorderStroke(
                1.dp,
                if (liveSel) CricketOutcomeBlue else CricketChipBorder
            )
        ) {
            Icon(
                imageVector = Icons.Filled.LiveTv,
                contentDescription = "Live",
                tint = if (liveSel) CricketAccentGold else CricketTextMuted,
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        val scoreSel = selected == IplMatchTab.Scoreboard
        Surface(
            onClick = { onSelect(IplMatchTab.Scoreboard) },
            shape = RoundedCornerShape(10.dp),
            color = CricketMarketBg,
            border = BorderStroke(
                1.dp,
                if (scoreSel) CricketOutcomeBlue else CricketChipBorder
            )
        ) {
            Text(
                text = "Scoreboard",
                color = if (scoreSel) TextWhite else CricketTextMuted,
                fontSize = 11.sp,
                fontWeight = if (scoreSel) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** Black live dashboard placeholder; embed stream URL here later. */
@Composable
private fun CricketLiveDashboardPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "liveBlink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        Text(
            text = "LIVE",
            color = Color(0xFFE53935).copy(alpha = blinkAlpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 10.dp)
        )
        Text(
            text = "Coming soon",
            color = CricketTextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun IplMatchStreamOrScoreSection(
    selectedTab: IplMatchTab?,
    onTabSelect: (IplMatchTab) -> Unit,
    score: CricketScorePayload?,
    scoreError: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IplLiveScoreTabRow(selected = selectedTab, onSelect = onTabSelect)
        selectedTab?.let { tab ->
            Spacer(modifier = Modifier.height(10.dp))
            when (tab) {
                IplMatchTab.Live -> CricketLiveDashboardPlaceholder()
                IplMatchTab.Scoreboard -> CricketScoreCardSection(
                    score = score,
                    error = scoreError
                )
            }
        }
    }
}

@Composable
private fun CricketScoreCardSection(
    score: CricketScorePayload?,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ScoreCardNavy)
    ) {
        when {
            score != null -> {
                val inningsSorted = score.innings.orEmpty().sortedBy { it.inningsNumber ?: 0 }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ScoreCardHeaderBar)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = score.seriesName?.takeIf { it.isNotBlank() } ?: "Cricket",
                        color = ScoreCardHeaderTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    inningsSorted.take(2).forEach { inn ->
                        val abbrev = teamAbbrev(inn.teamName ?: "—")
                        val batting = inn.conclusion?.equals("In Progress", ignoreCase = true) == true
                        val jColor = jerseyColor(abbrev)
                        val ovAvail = inn.oversAvailable
                        val ovStr = formatOversDisplay(inn.overs)
                        val oversText = when {
                            ovStr.isNotEmpty() && ovAvail != null -> "$ovStr / $ovAvail Ovs"
                            ovStr.isNotEmpty() -> "$ovStr Ovs"
                            else -> null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(jColor)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = abbrev,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                if (batting) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(BetstrikeLeagueAccent)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = inn.summary?.trim() ?: "—",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                if (oversText != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = oversText,
                                        color = CricketTextMuted,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    score.matchCommentary?.trim()?.takeIf { it.isNotEmpty() }?.let { comm ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = comm,
                            color = CricketTextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (score.bettingSuspended == true) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Betting suspended",
                            color = CricketOutcomeRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (inningsSorted.size >= 2) {
                        val first = inningsSorted[0]
                        val second = inningsSorted[1]
                        val chasePct = estimateChasingWinPercent(first, second)
                        if (chasePct != null) {
                            val t1 = teamAbbrev(first.teamName ?: "")
                            val t2 = teamAbbrev(second.teamName ?: "")
                            val c1 = jerseyColor(t1)
                            val c2 = jerseyColor(t2)
                            val p1 = 1f - chasePct
                            val p2 = chasePct
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${first.teamName?.trim() ?: t1} ${(p1 * 100).toInt()}%",
                                    color = CricketTextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${second.teamName?.trim() ?: t2} ${(p2 * 100).toInt()}%",
                                    color = CricketTextMuted,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1B2635))
                            ) {
                                val w1 = max(p1, 0.04f)
                                val w2 = max(1f - p1, 0.04f)
                                Box(
                                    modifier = Modifier
                                        .weight(w1)
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .background(c1)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(w2)
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .background(c2)
                                )
                            }
                        }
                    }
                    // Oldest over on the left, latest over on the right (reading time left → right).
                    val recent = score.recentOvers.orEmpty()
                        .sortedBy { it.overNumber ?: 0 }
                        .takeLast(3)
                    if (recent.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Recent overs",
                            color = CricketTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.Top
                            ) {
                                recent.forEachIndexed { idx, over ->
                                    if (idx > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(44.dp)
                                                .background(Color.White.copy(alpha = 0.25f))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Column {
                                        val on = over.overNumber ?: 0
                                        val suffix = when {
                                            on % 100 in 11..13 -> "th"
                                            on % 10 == 1 -> "st"
                                            on % 10 == 2 -> "nd"
                                            on % 10 == 3 -> "rd"
                                            else -> "th"
                                        }
                                        Text(
                                            text = "${on}$suffix",
                                            color = if (over.isCurrentOver == true) TextWhite else CricketTextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (over.isCurrentOver == true) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        // Balls in delivery order: first ball left, latest ball right.
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            over.balls.orEmpty()
                                                .filter { it.isNotBlank() }
                                                .forEach { raw ->
                                                    val (bg, fg) = ballChipStyle(raw)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(bg),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = raw.trim(),
                                                            color = fg,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val activeInnings = inningsSorted.firstOrNull {
                        it.conclusion?.equals("In Progress", ignoreCase = true) == true
                    }
                    val batters = activeInnings?.batsmen.orEmpty().filter { b ->
                        b.active == true && b.didNotBat != true && b.toCome != true
                    }
                    val bowler = pickHighlightBowler(activeInnings?.bowlers)
                    if (batters.isNotEmpty() || bowler != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                        if (batters.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            StatTableHeader(
                                col1 = "Batter",
                                c2 = "R",
                                c3 = "B",
                                c4 = "4s",
                                c5 = "6s"
                            )
                            batters.take(6).forEach { b ->
                                StatTableRow5(
                                    col1 = "${b.batsmanName?.trim() ?: "—"}${if (b.onStrike == true) "*" else ""}",
                                    c2 = "${b.runs ?: 0}",
                                    c3 = "${b.balls ?: 0}",
                                    c4 = "${b.fours ?: 0}",
                                    c5 = "${b.sixes ?: 0}"
                                )
                            }
                        }
                        if (bowler != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            StatTableHeader(
                                col1 = "Bowler",
                                c2 = "O",
                                c3 = "R",
                                c4 = "W",
                                c5 = ""
                            )
                            StatTableRow5(
                                col1 = bowler.bowlerName?.trim() ?: "—",
                                c2 = formatOversDisplay(bowler.overs),
                                c3 = "${bowler.runs ?: 0}",
                                c4 = "${bowler.wickets ?: 0}",
                                c5 = ""
                            )
                        }
                    }
                }
            }
            error != null -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = error,
                        color = CricketTextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Loading live score…",
                        color = CricketTextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTableHeader(
    col1: String,
    c2: String,
    c3: String,
    c4: String,
    c5: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = col1,
            color = CricketTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.4f)
        )
        Text(text = c2, color = CricketTextMuted, fontSize = 10.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        Text(text = c3, color = CricketTextMuted, fontSize = 10.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        Text(text = c4, color = CricketTextMuted, fontSize = 10.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        Text(text = c5, color = CricketTextMuted, fontSize = 10.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun StatTableRow5(
    col1: String,
    c2: String,
    c3: String,
    c4: String,
    c5: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = col1,
            color = TextWhite,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f)
        )
        Text(text = c2, color = TextWhite, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        Text(text = c3, color = TextWhite, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        Text(text = c4, color = TextWhite, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        Text(text = c5, color = TextWhite, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun CricketTopBar(
    balance: String,
    isLoggedIn: Boolean,
    onWalletOrDeposit: () -> Unit,
    onLogin: () -> Unit,
    onBettingHistory: () -> Unit
) {
    Surface(color = CricketHeaderBg, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ipl_nav),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(CricketAccentGold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CRICKET",
                    color = CricketAccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 0.8.sp
                )
            }
            TextButton(
                onClick = onBettingHistory,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "My bets",
                    color = CricketAccentGold,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            if (isLoggedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(CricketMarketBg)
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onWalletOrDeposit() }
                    ) {
                        Text("₹", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(balance, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = onWalletOrDeposit,
                        shape = CircleShape,
                        color = CricketAccentGold,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add funds",
                                tint = CricketOutcomeText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                TextButton(onClick = onLogin) {
                    Text("Log in", color = CricketAccentGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CricketFilterChips(
    markets: List<CricketLiveMarket>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    // Show Main first (default), then All, Over by over — order is display-only; indices match filter logic
    val chips = listOf(
        1 to "Main",
        0 to "All",
        2 to "Over by over",
        3 to "Special",
        4 to "Players"
    )
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (filterIdx, title) ->
            val selected = filterIdx == selectedIndex
            val label = "$title (${CricketMarketFilter.countFor(markets, filterIdx)})"
            Surface(
                onClick = { onSelect(filterIdx) },
                shape = RoundedCornerShape(20.dp),
                color = CricketMarketBg,
                border = BorderStroke(
                    1.dp,
                    if (selected) CricketOutcomeBlue else CricketChipBorder
                )
            ) {
                Text(
                    text = label,
                    color = if (selected) TextWhite else CricketTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CricketMarketBlock(
    market: CricketLiveMarket,
    blurOdds: Boolean,
    onOutcomePick: (CricketLiveMarket, CricketLiveOutcome) -> Unit
) {
    val outcomes = market.outcomes.orEmpty()
    if (outcomes.isEmpty()) return
    val marketOpen = market.status?.equals("open", ignoreCase = true) == true
    val title = market.description?.trim()?.takeIf { it.isNotEmpty() } ?: "Market"
    val canBet = marketOpen && !blurOdds

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CricketMarketBg)
            .padding(10.dp)
    ) {
        Text(
            text = title,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = listOfNotNull(
                market.period?.takeIf { it.isNotBlank() },
                market.marketType?.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { "Match" },
            color = CricketTextMuted,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (!marketOpen) {
            Text("Closed", color = CricketTextMuted, fontSize = 12.sp)
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                CricketOutcomeGrid(
                    outcomes = outcomes,
                    market = market,
                    interactionsEnabled = canBet,
                    modifier = Modifier.oddsBlurIf(blurOdds),
                    onOutcomePick = onOutcomePick
                )
            }
        }
    }
}

@Composable
private fun CricketOutcomeGrid(
    outcomes: List<CricketLiveOutcome>,
    market: CricketLiveMarket,
    interactionsEnabled: Boolean,
    modifier: Modifier = Modifier,
    onOutcomePick: (CricketLiveMarket, CricketLiveOutcome) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (outcomes.size) {
            1 -> {
                OutcomeButton(
                    outcome = outcomes[0],
                    color = CricketOutcomeBlue,
                    enabled = interactionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOutcomePick(market, outcomes[0]) }
                )
            }
            2 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    outcomes.forEachIndexed { index, outcome ->
                        OutcomeButton(
                            outcome = outcome,
                            color = if (index % 2 == 0) CricketOutcomeBlue else CricketOutcomeRed,
                            enabled = interactionsEnabled,
                            modifier = Modifier.weight(1f),
                            onClick = { onOutcomePick(market, outcome) }
                        )
                    }
                }
            }
            3 -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutcomeButton(
                            outcome = outcomes[0],
                            color = CricketOutcomeBlue,
                            enabled = interactionsEnabled,
                            modifier = Modifier.weight(1f),
                            onClick = { onOutcomePick(market, outcomes[0]) }
                        )
                        OutcomeButton(
                            outcome = outcomes[1],
                            color = CricketOutcomeRed,
                            enabled = interactionsEnabled,
                            modifier = Modifier.weight(1f),
                            onClick = { onOutcomePick(market, outcomes[1]) }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(0.5f)) {
                        OutcomeButton(
                            outcome = outcomes[2],
                            color = CricketOutcomeBlue,
                            enabled = interactionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOutcomePick(market, outcomes[2]) }
                        )
                    }
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    outcomes.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val startIndex = rowIndex * 2
                            row.forEachIndexed { colIndex, outcome ->
                                val globalIndex = startIndex + colIndex
                                OutcomeButton(
                                    outcome = outcome,
                                    color = if (globalIndex % 2 == 0) CricketOutcomeBlue else CricketOutcomeRed,
                                    enabled = interactionsEnabled,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOutcomePick(market, outcome) }
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeButton(
    outcome: CricketLiveOutcome,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = if (enabled) 1f else 0.45f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = outcome.displayLabel(),
                color = CricketOutcomeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = outcome.displayOdds(),
                color = CricketOutcomeText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
