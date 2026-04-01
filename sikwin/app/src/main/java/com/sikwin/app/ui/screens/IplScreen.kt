@file:OptIn(ExperimentalMaterial3Api::class)

package com.sikwin.app.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.data.models.CricketLiveEventData
import com.sikwin.app.data.models.CricketLiveMarket
import com.sikwin.app.data.models.CricketLiveOutcome
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

private const val CRICKET_POLL_MS = 2000L
/** Blur only after this many failed polls in a row (transient errors won't stop updates). */
private const val CRICKET_POLL_FAILURES_BEFORE_BLUR = 5

/** Slight blur on odds when live polling has stopped after a failed fetch (API 31+); softer alpha on older APIs. */
private fun Modifier.oddsBlurIf(blur: Boolean): Modifier = when {
    !blur -> this
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> this.blur(5.dp)
    else -> this.graphicsLayer { alpha = 0.55f }
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
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var betPick by remember { mutableStateOf<CricketBetPick?>(null) }
    var stakeText by remember { mutableStateOf("100") }
    // Default to Main; filter indices match CricketMarketFilter (0=All, 1=Main, …)
    var filterIndex by remember { mutableIntStateOf(1) }
    var pollSession by remember { mutableIntStateOf(0) }

    fun restartCricketPolling() {
        pollSession++
    }

    LaunchedEffect(pollSession) {
        viewModel.fetchWallet()
        viewModel.cricketPollStopped = false
        viewModel.cricketError = null
        var consecutiveFailures = 0
        while (true) {
            val start = SystemClock.elapsedRealtime()
            val ok = viewModel.cricketFetchOnce()
            if (ok) {
                consecutiveFailures = 0
                viewModel.cricketPollStopped = false
            } else {
                consecutiveFailures++
                viewModel.cricketPollStopped =
                    consecutiveFailures >= CRICKET_POLL_FAILURES_BEFORE_BLUR &&
                    viewModel.cricketLive != null
            }
            // Next poll ~CRICKET_POLL_MS after this one started (not after 2s + request time).
            val elapsed = SystemClock.elapsedRealtime() - start
            delay((CRICKET_POLL_MS - elapsed).coerceAtLeast(0L))
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
        bottomBar = { HomeBottomNavigation(currentRoute = "ipl", viewModel = viewModel, onNavigate = onNavigate) },
        containerColor = CricketScreenBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CricketScreenBg)
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
                            onClick = { restartCricketPolling() },
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
                                item {
                                    Text(
                                        "No live event from the API right now. Open the full site below.",
                                        color = CricketTextMuted,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                            } else {
                                item {
                                    Text(
                                        text = "Cricket  ›  Indian Premier League 2026  ›  Indian Premier League",
                                        color = CricketTextMuted,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                item {
                                    Text(
                                        text = live.description?.trim()?.uppercase()
                                            ?.takeIf { it.isNotEmpty() } ?: "MATCH",
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
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
                                        onSelect = { filterIndex = it }
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
                                                betPick = CricketBetPick(
                                                    eventId = live.id,
                                                    marketId = m.id,
                                                    marketName = m.description?.trim()
                                                        ?.takeIf { it.isNotEmpty() } ?: "Market",
                                                    outcomeId = o.id,
                                                    outcomeLabel = o.displayLabel(),
                                                    oddsDisplay = o.displayOdds()
                                                )
                                                stakeText = "100"
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
            text = "Match · TWO_OUTCOME",
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
