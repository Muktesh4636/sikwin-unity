@file:OptIn(ExperimentalMaterial3Api::class)

package com.sikwin.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.sikwin.app.R
import com.sikwin.app.ui.theme.BlackBackground
import com.sikwin.app.ui.theme.BorderColor
import com.sikwin.app.ui.theme.CardBackground
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.SurfaceColor
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.ui.theme.TextWhite
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Looks “busy” — not round tens (no 110, 200…). */
private fun randomLivePlayerCount(): Int {
    val pool = (100..400).filter { it % 10 != 0 }
    return pool.random()
}

private enum class CoinSide { HEADS, TAILS }

private data class BetRowUi(val name: String, val bet: Int, val win: Int)

private val nameStarts = listOf(
    "Ra", "Vi", "Ku", "Sa", "Ne", "Ar", "Di", "Ma", "Sh", "An", "Pr", "Ro", "Ka", "De", "Su", "Ta",
    "Ha", "Ji", "Mo", "Li", "Ab", "Ir", "Om", "Ri", "Ya", "Ni", "Si", "Te", "Pa", "Gu", "Bh", "Kr"
)
private val nameEnds = listOf(
    "vi", "nu", "ja", "esh", "ika", "ant", "ita", "raj", "ini", "ya", "han", "dev", "lak", "shi",
    "mit", "pal", "sen", "kar", "dul", "preet", "deep", "lata", "kant", "vant", "sri", "esh", "oj",
    "may", "nil", "tan", "run", "vek", "sin", "moy", "jit", "roy", "das"
)

/** Procedural display names; optional numeric suffix so rows look distinct. */
private fun randomLiveDisplayName(): String {
    val raw = nameStarts.random() + nameEnds.random()
    val cap = raw.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecaseChar() else c }
    return if (Random.nextFloat() < 0.42f) "$cap ${Random.nextInt(1, 100)}" else cap
}

/** Win column in ₹50..₹15,000 when the row shows a win; bet is plausible vs win. */
private fun generateLiveBetRows(count: Int): List<BetRowUi> = List(count) {
    val won = Random.nextFloat() < 0.52f
    val win = if (won) Random.nextInt(50, 15_001) else 0
    val bet = when {
        won && win > 0 -> (win / 2).coerceIn(25, 12_000)
        else -> Random.nextInt(40, 8001)
    }
    BetRowUi(randomLiveDisplayName(), bet, win)
}

/**
 * Total rotation so the coin lands with the correct face after at least [minSpins] full turns.
 * Heads ⇔ 0° (mod 360), tails ⇔ 180° (mod 360) for the two-layer Y rotation setup.
 */
private fun computeCoinFlipEndRotation(fromDegrees: Float, result: CoinSide, minSpins: Int): Float {
    val spins = minSpins + Random.nextInt(2)
    val minTotal = 360f * spins
    val rawEnd = fromDegrees + minTotal
    val rawRem = ((rawEnd % 360f) + 360f) % 360f
    val targetRem = if (result == CoinSide.HEADS) 0f else 180f
    val adjust = (targetRem - rawRem + 360f) % 360f
    return rawEnd + adjust
}

@Composable
private fun CoinFlipVisual(rotationY: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val cameraDistance = 14f * density.density
    val origin = TransformOrigin(0.5f, 0.5f)
    Box(
        modifier = modifier
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.heads_coin),
            contentDescription = stringResource(R.string.heads_tails_heads),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.rotationY = rotationY
                    this.cameraDistance = cameraDistance
                    transformOrigin = origin
                },
            contentScale = ContentScale.Crop
        )
        Image(
            painter = painterResource(R.drawable.tails_coin),
            contentDescription = stringResource(R.string.heads_tails_tails),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.rotationY = rotationY + 180f
                    this.cameraDistance = cameraDistance
                    transformOrigin = origin
                },
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Heads & Tails — layout aligned with https://gunduata.club/coin/ (dark theme, bet stepper,
 * Heads ♚ / Tails ♛, Flip, Live Bets tabs, winner dialog).
 * Balance from GET /api/auth/wallet/; POST /api/coin/ — win/lose is decided on the server.
 */
@Composable
fun HeadsTailsScreen(
    viewModel: GunduAtaViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val username = viewModel.userProfile?.username ?: "You"

    LaunchedEffect(Unit) {
        viewModel.fetchWallet()
    }

    var amountText by remember { mutableStateOf("50") }
    fun betForPlay(): Int {
        val n = amountText.toIntOrNull()
        return when {
            n == null -> 10
            n < 10 -> 10
            n > 50_000 -> 50_000
            else -> n
        }
    }
    fun adjustBet(delta: Int) {
        val base = amountText.toIntOrNull() ?: 10
        val next = (base + delta).coerceIn(10, 50_000)
        amountText = next.toString()
    }
    var selectedSide by remember { mutableStateOf<CoinSide?>(null) }
    /** Coin outcome (heads/tails) from the last completed round — highlights that side’s card. */
    var lastRoundResultSide by remember { mutableStateOf<CoinSide?>(null) }
    var lastRoundSummary by remember { mutableStateOf("No round played yet.") }
    var flipping by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    /** Win amount (rupees) shown on celebration — e.g. “You win ₹100”. */
    var celebrationWinRupees by remember { mutableIntStateOf(0) }
    var flipError by remember { mutableStateOf<String?>(null) }
    /** Total Y rotation (deg); two-face coin lands on API result after each flip animation. */
    val coinRotation = remember { Animatable(0f) }

    var liveTab by remember { mutableIntStateOf(0) } // 0 = Live, 1 = My
    var liveFakePlayerCount by remember { mutableIntStateOf(randomLivePlayerCount()) }
    var livePoolRows by remember { mutableStateOf(generateLiveBetRows(80)) }
    var livePoolEpoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay((650L..2100L).random())
            liveFakePlayerCount = randomLivePlayerCount()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay((2000L..4200L).random())
            livePoolRows = generateLiveBetRows(80)
            livePoolEpoch++
        }
    }

    val myBets = remember { mutableStateListOf<BetRowUi>() }

    LaunchedEffect(showCelebration) {
        if (showCelebration) {
            delay(9000)
            showCelebration = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = BlackBackground,
        bottomBar = {
            HomeBottomNavigation(currentRoute = "coin", viewModel = viewModel, onNavigate = onNavigate)
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.heads_tails_title),
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = PrimaryYellow)
                    }
                },
                actions = {
                    // Coin bets are checked against wallet.balance on the server.
                    val bal = viewModel.wallet?.balance ?: viewModel.wallet?.withdrawable_balance ?: "0"
                    Column(
                        modifier = Modifier.padding(end = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            stringResource(R.string.heads_tails_balance_label),
                            color = TextGrey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "₹$bal",
                            color = PrimaryYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BlackBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(
                stringResource(R.string.heads_tails_last_round),
                color = TextGrey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                lastRoundSummary,
                color = TextWhite,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Coin — 3D Y rotation; two layers (heads at θ, tails at θ+180°) land on API result.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                CoinFlipVisual(
                    rotationY = coinRotation.value,
                    modifier = Modifier.size(176.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Enter amount: manual field + stepper (below coin)
            Text(
                stringResource(R.string.heads_tails_enter_amount),
                color = TextGrey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentVal = amountText.toIntOrNull() ?: 0
                StepButton(text = "−", enabled = !flipping && currentVal > 10) {
                    adjustBet(-10)
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        if (flipping) return@OutlinedTextField
                        val digits = raw.filter { it.isDigit() }.take(6)
                        amountText = digits
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    enabled = !flipping,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Text("₹", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    textStyle = TextStyle(
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        disabledTextColor = TextGrey,
                        focusedBorderColor = PrimaryYellow,
                        unfocusedBorderColor = BorderColor,
                        disabledBorderColor = BorderColor.copy(alpha = 0.5f),
                        cursorColor = PrimaryYellow,
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        disabledContainerColor = SurfaceColor.copy(alpha = 0.5f)
                    )
                )
                StepButton(text = "+", enabled = !flipping && currentVal < 50_000) {
                    adjustBet(10)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SideCard(
                    label = stringResource(R.string.heads_tails_heads),
                    symbol = "♚",
                    selected = selectedSide == CoinSide.HEADS,
                    isResultHighlight = lastRoundResultSide == CoinSide.HEADS,
                    enabled = !flipping,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedSide = CoinSide.HEADS }
                )
                SideCard(
                    label = stringResource(R.string.heads_tails_tails),
                    symbol = "♛",
                    selected = selectedSide == CoinSide.TAILS,
                    isResultHighlight = lastRoundResultSide == CoinSide.TAILS,
                    enabled = !flipping,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedSide = CoinSide.TAILS }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val pick = selectedSide ?: return@Button
                    if (flipping) return@Button
                    val stake = betForPlay()
                    flipping = true
                    flipError = null
                    lastRoundResultSide = null
                    scope.launch {
                        // Spin immediately while the network runs so the UI never feels idle for 3–5s.
                        val spinJob = launch {
                            while (isActive) {
                                coinRotation.animateTo(
                                    coinRotation.value + 360f,
                                    animationSpec = tween(
                                        durationMillis = 320,
                                        easing = LinearEasing
                                    )
                                )
                            }
                        }
                        val apiResult = viewModel.postCoinFlip(pick == CoinSide.HEADS, stake)
                        spinJob.cancelAndJoin()
                        apiResult.fold(
                            onSuccess = { body ->
                                val resultSide = when (body.result?.lowercase()?.trim()) {
                                    "heads" -> CoinSide.HEADS
                                    else -> CoinSide.TAILS
                                }
                                val won = body.won == true
                                viewModel.fetchWallet()
                                val endRotation = computeCoinFlipEndRotation(
                                    coinRotation.value,
                                    resultSide,
                                    minSpins = 2
                                )
                                coinRotation.animateTo(
                                    endRotation,
                                    animationSpec = tween(
                                        durationMillis = 3000,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                lastRoundResultSide = resultSide
                                lastRoundSummary = resources.getString(
                                    if (won) R.string.heads_tails_round_summary_win else R.string.heads_tails_round_summary_lose,
                                    coinSideLabel(pick),
                                    coinSideLabel(resultSide),
                                    stake
                                )
                                val winCol = parseRupeeInt(body.payout)
                                myBets.add(0, BetRowUi(username, stake, winCol))
                                if (won) {
                                    val showAmt = winCol.takeIf { it > 0 } ?: (stake * 2)
                                    celebrationWinRupees = showAmt
                                    delay(1000)
                                    showCelebration = true
                                }
                                flipping = false
                            },
                            onFailure = { e ->
                                flipError = e.message
                                flipping = false
                            }
                        )
                    }
                },
                enabled = !flipping && selectedSide != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryYellow,
                    contentColor = Color.Black,
                    disabledContainerColor = SurfaceColor,
                    disabledContentColor = TextGrey
                )
            ) {
                Text(stringResource(R.string.heads_tails_flip), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }

            flipError?.let { err ->
                Text(
                    text = err,
                    color = Color(0xFFE57373),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.heads_tails_live_bets),
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LiveOnlineBadge(playerCount = liveFakePlayerCount)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                TabChip(
                    text = stringResource(R.string.heads_tails_tab_live100),
                    selected = liveTab == 0,
                    modifier = Modifier.weight(1f)
                ) { liveTab = 0 }
                TabChip(
                    text = stringResource(R.string.heads_tails_tab_my),
                    selected = liveTab == 1,
                    modifier = Modifier.weight(1f)
                ) { liveTab = 1 }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BetsTableHeader()
            HorizontalDivider(color = BorderColor, thickness = 1.dp)

            val rows = if (liveTab == 0) livePoolRows else myBets
            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.heads_tails_no_wins_yet),
                    color = TextGrey,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Column(modifier = Modifier.height(280.dp)) {
                    rows.take(40).forEachIndexed { idx, row ->
                        key(
                            if (liveTab == 0) "live_${livePoolEpoch}_${row.name}_${row.bet}_${row.win}_$idx"
                            else "my_${row.name}_${row.bet}_${row.win}_$idx"
                        ) {
                            BetTableRow(row)
                            HorizontalDivider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

        if (showCelebration) {
            WinCelebrationOverlay(
                winRupees = celebrationWinRupees,
                onDismiss = { showCelebration = false }
            )
        }
    }
}

@Composable
private fun LiveOnlineBadge(playerCount: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_dot_blink")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot_alpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(Color(0xFF43A047))
        )
        Text(
            text = stringResource(R.string.heads_tails_live_online_count, playerCount),
            color = TextGrey,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun WinCelebrationOverlay(
    winRupees: Int,
    onDismiss: () -> Unit
) {
    val cardScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "celebration_card_scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2B2210),
                            Color.Black.copy(alpha = 0.94f)
                        )
                    )
                )
                .clickable(onClick = onDismiss)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp)
                .widthIn(max = 360.dp)
                .scale(cardScale)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = CardBackground,
                shadowElevation = 16.dp,
                border = BorderStroke(2.dp, PrimaryYellow.copy(alpha = 0.9f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🏆", fontSize = 52.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.heads_tails_winner_title),
                        color = PrimaryYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.heads_tails_you_win_rupees, winRupees),
                        color = PrimaryYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.heads_tails_you_won_exclaim),
                        color = TextWhite.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryYellow,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.heads_tails_continue),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.heads_tails_tap_to_close),
                        color = TextGrey.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) CardBackground else SurfaceColor.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (enabled) PrimaryYellow else TextGrey, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SideCard(
    label: String,
    symbol: String,
    selected: Boolean,
    isResultHighlight: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val resultGreen = Color(0xFF43A047)
    val borderWidth = when {
        isResultHighlight -> 3.dp
        selected -> 2.dp
        else -> 2.dp
    }
    val border = when {
        isResultHighlight -> resultGreen
        selected -> PrimaryYellow
        else -> BorderColor
    }
    val bg = when {
        isResultHighlight -> resultGreen.copy(alpha = 0.22f)
        selected -> PrimaryYellow.copy(alpha = 0.12f)
        else -> CardBackground
    }
    val symbolColor = when {
        isResultHighlight -> resultGreen
        selected -> PrimaryYellow
        else -> TextGrey
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(borderWidth, border, RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, fontSize = 28.sp, color = symbolColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (isResultHighlight) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = resultGreen.copy(alpha = 0.35f)
            ) {
                Text(
                    text = stringResource(R.string.heads_tails_round_result_badge),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color(0xFFC8E6C9),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) PrimaryYellow else TextGrey,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PrimaryYellow.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun BetsTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.heads_tails_col_name_bet),
            color = TextGrey,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.heads_tails_col_win),
            color = TextGrey,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.45f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun BetTableRow(row: BetRowUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name,
                color = TextWhite,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "₹ ${row.bet}",
                color = TextGrey,
                fontSize = 12.sp
            )
        }
        Text(
            "₹ ${row.win}",
            color = if (row.win > 0) PrimaryYellow else TextGrey,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.45f),
            textAlign = TextAlign.End
        )
    }
}

private fun coinSideLabel(side: CoinSide): String = when (side) {
    CoinSide.HEADS -> "Heads"
    CoinSide.TAILS -> "Tails"
}

private fun parseRupeeInt(s: String?): Int {
    if (s.isNullOrBlank()) return 0
    return s.replace(",", "").trim().toDoubleOrNull()?.toInt() ?: 0
}
