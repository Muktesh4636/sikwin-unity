@file:OptIn(ExperimentalMaterial3Api::class)

package com.sikwin.app.ui.screens

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import com.sikwin.app.data.models.ColourPublicResultItem
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Reference palette (COLOUR GAME)
private val BgBlack = Color(0xFF000000)
private val GoldTitle = Color(0xFFEAB308)
private val GoldAccent = Color(0xFFD4AF37)
private val GreenJoin = Color(0xFF064E3B)
private val VioletJoin = Color(0xFF4C1D95)
private val RedJoin = Color(0xFF7F1D1D)
private val GreenBright = Color(0xFF10B981)
private val RedBright = Color(0xFFEF4444)
private val VioletBright = Color(0xFF8B5CF6)
private val TimerYellow = Color(0xFFFDE047)
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.colour_game_title),
                        color = GoldTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif
                    )
                },
                navigationIcon = { },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, GoldTitle, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("₹", color = GoldTitle, fontWeight = FontWeight.Bold)
                                Text(balance, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldTitle)
                                .clickable(onClick = onDeposit),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgBlack)
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
                    Text(
                        if (round?.status.equals("no_round", ignoreCase = true)) {
                            roundIdLine
                        } else {
                            stringResource(R.string.colour_game_round_id_fmt, roundIdLine)
                        },
                        color = TimerYellow.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = stringResource(R.string.colour_round_number_label),
                            color = Color(0xFF9CA3AF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = round?.number?.let { "$it" } ?: "—",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
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
            Text(
                stringResource(R.string.colour_recent_results),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            r.round_id.ifBlank { "—" },
            color = Color.White,
            fontSize = 12.sp,
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
