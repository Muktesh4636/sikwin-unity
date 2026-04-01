@file:OptIn(ExperimentalMaterial3Api::class)

package com.sikwin.app.ui.screens

import android.media.AudioAttributes
import android.media.SoundPool
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sikwin.app.R
import com.sikwin.app.data.models.ColourBetHistoryItem
import com.sikwin.app.data.models.ColourPublicResultItem
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Reference palette (COLOUR GAME)
private val BgBlack = Color(0xFF000000)
private val GoldTitle = Color(0xFFFFD700)
private val GoldAccent = Color(0xFFFFE033)
private val GreenJoin = Color(0xFF16A34A)
private val VioletJoin = Color(0xFF7C3AED)
private val RedJoin = Color(0xFFDC2626)
private val GreenBright = Color(0xFF10B981)
private val RedBright = Color(0xFFEF4444)
private val VioletBright = Color(0xFF8B5CF6)
private val TimerYellow = Color(0xFFFFE500)
private val TableBg = Color(0xFF1C1C1E)
private val BetCardBg = Color(0xFF2C2C2E)
private val BetCardGreyButton = Color(0xFF3A3A3C)

private enum class DotColour { GREEN, RED, VIOLET }

private sealed class ColourBetTarget {
    data class Side(val apiName: String, val subtitleLine: String) : ColourBetTarget()
    data class NumberPick(val n: Int) : ColourBetTarget()
}

private fun dotsFromColourResult(result: String?): List<DotColour> {
    return when (result?.lowercase()) {
        "green" -> listOf(DotColour.GREEN)
        "red" -> listOf(DotColour.RED)
        "green_violet" -> listOf(DotColour.GREEN, DotColour.VIOLET)
        "red_violet" -> listOf(DotColour.RED, DotColour.VIOLET)
        else -> emptyList()
    }
}

@Composable
fun ColourGameScreen(
    viewModel: GunduAtaViewModel,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    onDeposit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var placingBet by remember { mutableStateOf(false) }
    var betTarget by remember { mutableStateOf<ColourBetTarget?>(null) }
    var soundEnabled by remember { mutableStateOf(true) }

    val round = viewModel.colourRound
    val timerSec = viewModel.colourDisplayTimerSeconds.coerceAtLeast(0)
    val sixtySecondStyleRound = viewModel.colourRoundUsesSixtySecondCountdown()
    /** Lock betting for the last 30s when the round uses a ~60s timer; else follow server + timer > 0. */
    val canBet = viewModel.loginSuccess &&
        round != null &&
        round.betting_open == true &&
        !round.status.equals("no_round", ignoreCase = true) &&
        (if (sixtySecondStyleRound) timerSec > 30 else timerSec > 0)

    DisposableEffect(Unit) {
        viewModel.startColourGameSession()
        onDispose { viewModel.stopColourGameSession() }
    }

    // Soft tick sound every second while timer is running
    val tickSoundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val tickSoundId = remember(context) {
        tickSoundPool.load(context, R.raw.colour_tick, 1)
    }
    DisposableEffect(Unit) {
        onDispose { tickSoundPool.release() }
    }
    LaunchedEffect(timerSec) {
        if (timerSec > 0 && soundEnabled) {
            // Very soft tick; last 10s slightly more audible
            val vol = if (timerSec <= 10) 0.08f else 0.04f
            tickSoundPool.play(tickSoundId, vol, vol, 1, 0, 1f)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchWallet()
        if (viewModel.loginSuccess) {
            viewModel.fetchColourBetsHistory()
        }
    }

    LaunchedEffect(viewModel.loginSuccess) {
        if (viewModel.loginSuccess) {
            viewModel.fetchColourBetsHistory()
        }
    }

    val balance = viewModel.wallet?.balance ?: "0.00"
    /** Full round is treated as 60s; bar fills as time runs down. */
    val progress = (timerSec.coerceAtMost(60) / 60f).coerceIn(0f, 1f)
    val timerText = remember(timerSec) {
        val m = timerSec / 60
        val s = timerSec % 60
        "%02d:%02d".format(m, s)
    }

    val roundIdLine = when {
        round == null -> "…"
        round.status.equals("no_round", ignoreCase = true) -> round.message ?: "—"
        else -> round.round_id ?: "—"
    }

    val recentRows = remember(viewModel.colourPublicResults) {
        viewModel.colourPublicResults.take(50)
    }

    val presetAmounts = remember { listOf(10, 20, 50, 100, 200, 500, 1000) }

    fun submitBet(amount: Int, target: ColourBetTarget) {
        if (!viewModel.loginSuccess) {
            Toast.makeText(context, context.getString(R.string.colour_game_sign_in_to_bet), Toast.LENGTH_SHORT).show()
            return
        }
        if (amount < 1) {
            Toast.makeText(context, context.getString(R.string.colour_game_invalid_stake), Toast.LENGTH_SHORT).show()
            return
        }
        if (!canBet || placingBet) return
        placingBet = true
        scope.launch {
            try {
                val result = when (target) {
                    is ColourBetTarget.Side -> viewModel.postColourBetSide(target.apiName, amount)
                    is ColourBetTarget.NumberPick -> viewModel.postColourBetNumber(target.n, amount)
                }
                result.onSuccess {
                    Toast.makeText(context, context.getString(R.string.colour_game_bet_placed), Toast.LENGTH_SHORT).show()
                    viewModel.fetchWallet()
                    viewModel.fetchColourBetsHistory()
                    scope.launch { viewModel.fetchColourPublicResults() }
                    betTarget = null
                }
                result.onFailure { e ->
                    Toast.makeText(context, e.message ?: "Error", Toast.LENGTH_LONG).show()
                }
            } finally {
                placingBet = false
            }
        }
    }

    betTarget?.let { target ->
        ColourBetAmountCard(
            target = target,
            presetAmounts = presetAmounts,
            placingBet = placingBet,
            onDismiss = { if (!placingBet) betTarget = null },
            onConfirm = { amt -> submitBet(amt, target) }
        )
    }

    Scaffold(
        containerColor = BgBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.colour_game_title),
                        color = GoldTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1E))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "←",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Sound mute toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1E))
                            .clickable { soundEnabled = !soundEnabled },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (soundEnabled) "🔊" else "🔇",
                            fontSize = 16.sp
                        )
                    }
                    // Amount + deposit button in one combined box, no border on amount
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1C1C1E))
                            .clickable(onClick = onDeposit)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("₹", color = GoldTitle, fontWeight = FontWeight.Bold)
                            Text(balance, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(GoldTitle),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgBlack)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Round ID label
                    Text(
                        text = stringResource(R.string.colour_recent_col_round_id),
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Round ID value directly below the label
                    Text(
                        text = roundIdLine,
                        color = TimerYellow.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.colour_countdown_label),
                        color = TimerYellow.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        timerText,
                        color = TimerYellow,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            ColourRoundProgressBar(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(40.dp))

            val joinGreenText = stringResource(R.string.colour_join_green)
            val joinVioletText = stringResource(R.string.colour_join_violet)
            val joinRedText = stringResource(R.string.colour_join_red)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JoinChip(
                    joinGreenText,
                    GreenJoin,
                    Modifier.weight(1f),
                    enabled = canBet && !placingBet
                ) {
                    betTarget = ColourBetTarget.Side("green", "[ $joinGreenText ]")
                }
                JoinChip(
                    joinVioletText,
                    VioletJoin,
                    Modifier.weight(1f),
                    enabled = canBet && !placingBet
                ) {
                    betTarget = ColourBetTarget.Side("violet", "[ $joinVioletText ]")
                }
                JoinChip(
                    joinRedText,
                    RedJoin,
                    Modifier.weight(1f),
                    enabled = canBet && !placingBet
                ) {
                    betTarget = ColourBetTarget.Side("red", "[ $joinRedText ]")
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            val grid = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).chunked(5)
            grid.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { n ->
                        NumberCell(
                            n = n,
                            openLabel = if (canBet) "OPEN" else "—",
                            modifier = Modifier.weight(1f).padding(4.dp),
                            enabled = canBet && !placingBet
                        ) {
                            betTarget = ColourBetTarget.NumberPick(n)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tab row: RECENT RESULTS | MY BETS | TRENDS
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf("RECENT RESULTS", "MY BETS", "TRENDS")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TableBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == index) Color(0xFF2C2C2E) else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selectedTab == index) Color.White else Color(0xFF6B7280),
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Recent Results table
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TableBg)
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.colour_recent_col_round_id),
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            stringResource(R.string.colour_recent_col_number),
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(0.55f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.colour_recent_col_result),
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(0.85f),
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))
                    if (recentRows.isEmpty()) {
                        Text(
                            stringResource(R.string.colour_game_no_history),
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        recentRows.forEach { r ->
                            ColourPublicResultRow(r)
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // My Bets table
                ColourMyBetsTable(bets = viewModel.colourBetsHistory)
            } else {
                // Trends
                ColourTrendsPanel(results = viewModel.colourPublicResults)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Track is full width; gold segment always grows from the **left** (start), not from the center. */
@Composable
private fun ColourRoundProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF333333))
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(p)
                .background(GoldTitle)
        )
    }
}

@Composable
private fun ColourBetAmountCard(
    target: ColourBetTarget,
    presetAmounts: List<Int>,
    placingBet: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var amountIndex by remember(target) { mutableFloatStateOf(3f) }
    var selectedPreset by remember(target) { mutableIntStateOf(presetAmounts[3]) }
    val amount = presetAmounts[amountIndex.roundToInt().coerceIn(0, presetAmounts.lastIndex)]

    Dialog(onDismissRequest = { if (!placingBet) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GoldAccent, RoundedCornerShape(16.dp))
                .background(BetCardBg)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.colour_bet_amount_title),
                color = GoldAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = FontFamily.Serif
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (target) {
                    is ColourBetTarget.Side -> target.subtitleLine
                    is ColourBetTarget.NumberPick ->
                        stringResource(R.string.colour_bet_subtitle_number, target.n)
                },
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            Slider(
                value = amountIndex,
                onValueChange = {
                    amountIndex = it
                    selectedPreset = presetAmounts[it.roundToInt().coerceIn(0, presetAmounts.lastIndex)]
                },
                valueRange = 0f..presetAmounts.lastIndex.toFloat(),
                steps = (presetAmounts.size - 2).coerceAtLeast(0),
                enabled = !placingBet,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = GoldAccent,
                    activeTrackColor = GoldAccent,
                    inactiveTrackColor = Color(0xFF444444)
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "₹$amount",
                color = GoldAccent,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
            Spacer(Modifier.height(16.dp))
            presetAmounts.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { chip ->
                        val selected = chip == selectedPreset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) GoldAccent else Color(0xFF555555),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .background(Color(0xFF1A1A1C))
                                .clickable(enabled = !placingBet) {
                                    selectedPreset = chip
                                    val idx = presetAmounts.indexOf(chip)
                                    if (idx >= 0) amountIndex = idx.toFloat()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("₹$chip", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (row.size < 4) {
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    enabled = !placingBet,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BetCardGreyButton,
                        contentColor = Color(0xFFCCCCCC)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.colour_bet_cancel), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onConfirm(selectedPreset) },
                    enabled = !placingBet,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (placingBet) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.colour_bet_confirm), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColourPublicResultRow(r: ColourPublicResultItem) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                r.round_id.ifBlank { "—" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1.1f)
            )
            Text(
                "${r.number}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(0.55f),
                textAlign = TextAlign.Center
            )
            Row(
                Modifier.weight(0.85f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dotsFromColourResult(r.result).forEach { d ->
                    ResultDot(d)
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        HorizontalDivider(color = Color(0xFF2A2A2A))
    }
}

// ─── Trend helpers ────────────────────────────────────────────────────────────

private data class TrendPattern(
    val sequence: List<String>,   // e.g. ["red","red"]
    val nextPrediction: String,   // e.g. "green"
    val confidence: Int,          // 0-100
    val occurrences: Int
)

/** Extracts the base colour from a result string (green_violet → green, red_violet → red). */
private fun baseColour(result: String): String = when {
    result.contains("green", ignoreCase = true) -> "green"
    result.contains("red", ignoreCase = true) -> "red"
    result.contains("violet", ignoreCase = true) -> "violet"
    else -> result.lowercase()
}

/**
 * Analyses last [limit] results and finds what colour followed each 2-result sequence.
 * Returns top patterns sorted by occurrences desc.
 */
private fun analysePatterns(results: List<ColourPublicResultItem>, limit: Int = 50): List<TrendPattern> {
    val colours = results.take(limit).map { baseColour(it.result) }.reversed() // oldest first
    if (colours.size < 3) return emptyList()

    // Count: given sequence of 2, what came next?
    val followMap = mutableMapOf<Pair<String, String>, MutableMap<String, Int>>()
    for (i in 0 until colours.size - 2) {
        val key = Pair(colours[i], colours[i + 1])
        val next = colours[i + 2]
        followMap.getOrPut(key) { mutableMapOf() }.merge(next, 1, Int::plus)
    }

    val patterns = mutableListOf<TrendPattern>()
    for ((seq, nextMap) in followMap) {
        val total = nextMap.values.sum()
        val (bestNext, bestCount) = nextMap.maxByOrNull { it.value } ?: continue
        val confidence = (bestCount * 100) / total
        patterns.add(
            TrendPattern(
                sequence = listOf(seq.first, seq.second),
                nextPrediction = bestNext,
                confidence = confidence,
                occurrences = total
            )
        )
    }
    return patterns.sortedByDescending { it.occurrences }
}

/** Streak: how many times the same colour appeared consecutively at the end. */
private fun currentStreak(results: List<ColourPublicResultItem>): Pair<String, Int> {
    if (results.isEmpty()) return Pair("—", 0)
    val latest = baseColour(results.first().result)
    var count = 0
    for (r in results) {
        if (baseColour(r.result) == latest) count++ else break
    }
    return Pair(latest, count)
}

/** Colour frequency over last [limit] rounds. */
private fun colourFrequency(results: List<ColourPublicResultItem>, limit: Int = 20): Map<String, Int> {
    val freq = mutableMapOf("green" to 0, "red" to 0, "violet" to 0)
    results.take(limit).forEach { freq.merge(baseColour(it.result), 1, Int::plus) }
    return freq
}

@Composable
private fun ColourTrendsPanel(results: List<ColourPublicResultItem>) {
    val patterns = remember(results) { analysePatterns(results) }
    val (streakColour, streakCount) = remember(results) { currentStreak(results) }
    val freq = remember(results) { colourFrequency(results) }
    val total20 = freq.values.sum().coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TableBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Last 20 dot strip ──────────────────────────────────────────────
        Text("LAST 20 RESULTS", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            results.take(20).reversed().forEach { r ->
                val c = when (baseColour(r.result)) {
                    "green" -> GreenBright
                    "red" -> RedBright
                    else -> VioletBright
                }
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(c)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))

        // ── Frequency bar ──────────────────────────────────────────────────
        Text("COLOUR FREQUENCY (LAST 20)", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        listOf(
            Triple("Green", "green", GreenBright),
            Triple("Red", "red", RedBright),
            Triple("Violet", "violet", VioletBright)
        ).forEach { (label, key, colour) ->
            val count = freq[key] ?: 0
            val pct = count.toFloat() / total20
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, color = colour, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(46.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF333333))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(pct)
                            .background(colour)
                    )
                }
                Text("$count", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(20.dp), textAlign = TextAlign.End)
            }
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))

        // ── Current streak ─────────────────────────────────────────────────
        Text("CURRENT STREAK", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        val streakC = when (streakColour) {
            "green" -> GreenBright; "red" -> RedBright; "violet" -> VioletBright; else -> Color.White
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(streakCount.coerceAtMost(10)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(streakC))
            }
            if (streakCount > 0) {
                Text(
                    "${streakColour.replaceFirstChar { it.uppercase() }} × $streakCount",
                    color = streakC,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))

        // ── Pattern predictions ────────────────────────────────────────────
        Text("PATTERN PREDICTIONS", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        if (patterns.isEmpty()) {
            Text("Not enough data yet.", color = Color(0xFF6B7280), fontSize = 12.sp)
        } else {
            patterns.take(6).forEach { p ->
                val predColour = when (p.nextPrediction) {
                    "green" -> GreenBright; "red" -> RedBright; "violet" -> VioletBright; else -> Color.White
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1C1C1E))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sequence dots
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        p.sequence.forEach { col ->
                            val c = when (col) { "green" -> GreenBright; "red" -> RedBright; else -> VioletBright }
                            Box(Modifier.size(14.dp).clip(CircleShape).background(c))
                        }
                        Text(" →", color = Color(0xFF9CA3AF), fontSize = 14.sp)
                        Box(Modifier.size(14.dp).clip(CircleShape).background(predColour))
                        Text(
                            " ${p.nextPrediction.replaceFirstChar { it.uppercase() }}",
                            color = predColour,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Confidence badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(predColour.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("${p.confidence}%", color = predColour, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // ── Disclaimer ─────────────────────────────────────────────────────
        Text(
            "⚠ Patterns are based on past data only. Each round is independent — no outcome is guaranteed.",
            color = Color(0xFF4B5563),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun ColourMyBetsTable(bets: List<ColourBetHistoryItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TableBg)
            .padding(12.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth()) {
            Text("ROUND ID", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
            Text("BET ON", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
            Text("AMT", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
            Text("STATUS", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))
        if (bets.isEmpty()) {
            Text(
                "No bets placed yet.",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            bets.forEach { b ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            b.round_id?.takeLast(10) ?: "—",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1.2f)
                        )
                        val betLabel = when {
                            b.number != null -> "#${b.number}"
                            !b.bet_on.isNullOrBlank() -> b.bet_on.replaceFirstChar { it.uppercase() }
                            else -> "—"
                        }
                        Text(
                            betLabel,
                            color = when (b.bet_on?.lowercase()) {
                                "green" -> GreenBright
                                "red" -> RedBright
                                "violet" -> VioletBright
                                else -> Color.White
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.8f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "₹${b.amount ?: 0}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center
                        )
                        val statusColor = when (b.status?.lowercase()) {
                            "won" -> GreenBright
                            "lost" -> RedBright
                            "pending" -> TimerYellow
                            else -> Color(0xFF9CA3AF)
                        }
                        Text(
                            b.status?.replaceFirstChar { it.uppercase() } ?: "—",
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(0.7f),
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                }
            }
        }
    }
}

@Composable
private fun JoinChip(
    label: String,
    bg: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.75f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ResultDot(c: DotColour) {
    val color = when (c) {
        DotColour.GREEN -> GreenBright
        DotColour.RED -> RedBright
        DotColour.VIOLET -> VioletBright
    }
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun NumberCell(
    n: Int,
    openLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(openLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0xFF444444), CircleShape)
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            when (n) {
                0 -> DiagonalSplitCircle(Color(0xFF6D28D9), RedBright)
                5 -> DiagonalSplitCircle(Color(0xFF6D28D9), GreenBright)
                1, 3, 7, 9 -> SolidCircle(GreenBright)
                2, 4, 6, 8 -> SolidCircle(RedBright)
                else -> SolidCircle(GreenBright)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun SolidCircle(color: Color) {
    Box(Modifier.fillMaxSize().padding(3.dp).clip(CircleShape).background(color))
}

@Composable
private fun DiagonalSplitCircle(topLeft: Color, bottomRight: Color) {
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(3.dp)
            .clip(CircleShape)
    ) {
        val w = size.width
        val h = size.height
        val path1 = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(0f, h)
            close()
        }
        val path2 = Path().apply {
            moveTo(w, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path1, topLeft)
        drawPath(path2, bottomRight)
        drawCircle(color = Color(0xFF222222), style = Stroke(width = 1.dp.toPx()))
    }
}
