package com.sikwin.app.ui.screens

import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.filled.AddBox
import com.sikwin.app.R
import com.sikwin.app.data.prefs.ThemePreferences
import com.sikwin.app.ui.theme.*
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import com.sikwin.app.utils.MoneyFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
// ─── Chip amounts ────────────────────────────────────────────────────────────
private val CHIPS = listOf(10, 50, 100, 500, 1000)
private const val BET_WINDOW_SECONDS = 30

private val DarkGreen = Color(0xFF062006)
private val HeaderGreen = Color(0xFF0A2A0A)
private val GoldBorder = Color(0xFFD4A017)
private val HistoryTan = Color(0xFFD2A653)
private val HistoryNum = Color(0xFF2A2A2A)
private val GridGreen = Color(0xFF0A280A)
private val BetGold = Color(0xFFE8C252)

private fun parseWalletBalance(balance: String): Double =
    balance.replace(",", "").trim().toDoubleOrNull() ?: 0.0

@Composable
fun GunduAtaLiveScreen(
    viewModel: GunduAtaViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var selectedChip by remember { mutableStateOf(50) }
    var bets by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var phase by remember { mutableStateOf("BETTING") } // BETTING | ROLLING | RESULT
    var bettingOpen by remember { mutableStateOf(true) }
    var secondsLeft by remember { mutableStateOf(BET_WINDOW_SECONDS) }
    var diceResult by remember { mutableStateOf(0) }
    var diceCount by remember { mutableStateOf(0) }
    var recentResults by remember { mutableStateOf(listOf(4, 6, 5, 1, 6, 4)) }
    var winMessage by remember { mutableStateOf("") }
    var sessionBalance by remember { mutableStateOf(0.0) }
    var statusText by remember { mutableStateOf("Tap a number to place your bet") }
    var leaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkSession()
                if (viewModel.loginSuccess) viewModel.fetchWallet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.checkSession()
        if (viewModel.loginSuccess) viewModel.fetchWallet()
    }

    val walletBalance = viewModel.wallet?.balance ?: "0.00"

    LaunchedEffect(viewModel.wallet?.balance, bets.isEmpty()) {
        viewModel.wallet?.balance?.let { walletStr ->
            if (bets.isEmpty()) {
                sessionBalance = parseWalletBalance(walletStr)
            }
        }
    }

    fun goBack() {
        if (leaving) return
        leaving = true
        scope.launch {
            delay(50)
            onBack()
        }
    }

    BackHandler(enabled = !leaving) { goBack() }

    val infiniteTransition = rememberInfiniteTransition(label = "live")
    // Sharp on/off blink so the LIVE badge feels truly live
    val liveDot by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 550
                0f at 600
                0f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "liveDot"
    )

    fun clearBets() {
        if (!bettingOpen || phase != "BETTING") return
        val refund = bets.values.sum()
        sessionBalance += refund
        bets = emptyMap()
        statusText = "Bets cleared — tap a number to place bet"
    }

    fun placeBetOn(number: Int) {
        if (leaving || !bettingOpen || phase != "BETTING") {
            if (!bettingOpen) statusText = "Bets are closed"
            return
        }
        if (number !in 1..6) return
        if (sessionBalance < selectedChip) {
            statusText = "Insufficient balance"
            return
        }
        sessionBalance -= selectedChip
        val next = bets.toMutableMap()
        next[number] = (next[number] ?: 0) + selectedChip
        bets = next
        statusText = "Bet ₹${next[number]} on $number — ${secondsLeft}s left"
    }

    fun cycleChip() {
        if (!bettingOpen || phase != "BETTING") return
        val idx = CHIPS.indexOf(selectedChip)
        selectedChip = CHIPS[(idx + 1) % CHIPS.size]
        statusText = "Chip ₹$selectedChip selected — tap a number"
    }

    // Alternate PLACE YOUR BETS / BETS CLOSED every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            secondsLeft = BET_WINDOW_SECONDS
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            if (bettingOpen) {
                // Window closed → lock bets and roll if any
                bettingOpen = false
                statusText = "Bets closed"
                if (bets.isNotEmpty() && phase == "BETTING") {
                    phase = "ROLLING"
                }
            } else {
                // Open again for next round
                bettingOpen = true
                if (phase != "ROLLING" && phase != "RESULT") {
                    phase = "BETTING"
                }
                statusText = "Tap a number to place your bet"
            }
        }
    }

    LaunchedEffect(phase) {
        if (phase == "ROLLING") {
            delay(2200)
            val r = (1..6).random()
            val c = (1..6).random()
            diceResult = r
            diceCount = c
            recentResults = (listOf(r) + recentResults).take(6)
            val stake = bets[r] ?: 0
            if (stake > 0) {
                val win = stake * c
                sessionBalance += win
                winMessage = "YOU WIN! +₹$win"
                statusText = "Number $r hit — won ₹$win"
            } else {
                winMessage = "Try again!"
                statusText = "Dice $r — no bet on this number"
            }
            phase = "RESULT"
            delay(2500)
            winMessage = ""
            bets = emptyMap()
            phase = if (bettingOpen) "BETTING" else "BETTING"
            if (bettingOpen) statusText = "Tap a number to place your bet"
        }
    }

    Scaffold(
        containerColor = DualScreenBlack,
        topBar = {
            GunduAtaLiveTopBar(
                viewModel = viewModel,
                balance = walletBalance,
                onBack = { goBack() },
                onDeposit = {
                    if (viewModel.loginSuccess) onNavigate("deposit") else onNavigate("login")
                },
                onLogin = { onNavigate("login") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DarkGreen)
        ) {
        // ── Live video ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .border(BorderStroke(1.dp, GoldBorder))
                .background(Color(0xFF0A280A))
        ) {
            if (!leaving) {
                LiveVideoPlayer(
                    videoResId = R.raw.gundu_ata_live_dealer,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Surface(
                color = Color(0xFFE53935),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .graphicsLayer(alpha = liveDot)
                    )
                    Text(
                        "LIVE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            if (diceResult > 0) {
                Text(
                    "$diceResult",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 18.dp)
                )
            }

            if (winMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(winMessage, color = PrimaryYellow, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
        }

        // ── Betting grid — full phone width, shorter height only ──────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.30f)
        ) {
            GunduBettingGrid(
                bets = bets,
                selectedChip = selectedChip,
                bettingOpen = bettingOpen,
                phase = phase,
                onRefresh = { clearBets() },
                onNumberBet = { placeBetOn(it) },
                onCycleChip = { cycleChip() },
                modifier = Modifier.fillMaxSize()
            )
        }

        GunduDiceResultBar(
            diceResult = diceResult,
            diceCount = diceCount
        )

        GunduReferenceHistoryStrip(
            recentResults = recentResults,
            modifier = Modifier.navigationBarsPadding()
        )
        }
    }
}

/** Top bar on Live — uses the same theme as the home screen (Dual Cards / Hero / Classic). */
@Composable
private fun GunduAtaLiveTopBar(
    viewModel: GunduAtaViewModel,
    balance: String,
    onBack: () -> Unit,
    onDeposit: () -> Unit,
    onLogin: () -> Unit
) {
    val context = LocalContext.current
    val appTheme = remember {
        ThemePreferences(context).getAppTheme()
    }

    when (appTheme) {
        ThemePreferences.THEME_DUAL_CARDS,
        ThemePreferences.THEME_HERO -> {
            DualCardsTopBar(
                balance = balance,
                isLoggedIn = viewModel.loginSuccess,
                onLeadingClick = onBack,
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onDeposit = onDeposit,
                onLogin = onLogin
            )
        }
        else -> {
            GunduAtaClassicLiveTopBar(
                balance = balance,
                isLoggedIn = viewModel.loginSuccess,
                onBack = onBack,
                onDeposit = onDeposit,
                onLogin = onLogin
            )
        }
    }
}

/** Classic home theme top bar with back button (matches [HomeTopBar] styling). */
@Composable
private fun GunduAtaClassicLiveTopBar(
    balance: String,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onDeposit: () -> Unit,
    onLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlackBackground)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = PrimaryYellow,
                modifier = Modifier.size(22.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.gundu_ata_logo_gold),
                contentDescription = "Gundu Ata",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(44.dp)
            )
        }

        if (isLoggedIn) {
            Surface(color = SurfaceColor, shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.clickable { onDeposit() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("₹", color = PrimaryYellow, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(MoneyFormat.format(balance), color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.AddBox,
                        contentDescription = "Add money",
                        tint = PrimaryYellow,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onDeposit() }
                    )
                }
            }
        } else {
            TextButton(onClick = onLogin) {
                Text("Login", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ─── Number grid image (1–6 only from Generated_image.png) ───────────────────
@Composable
private fun GunduBettingGrid(
    bets: Map<Int, Int>,
    selectedChip: Int,
    bettingOpen: Boolean,
    phase: String,
    onRefresh: () -> Unit,
    onNumberBet: (Int) -> Unit,
    onCycleChip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = bettingOpen && phase == "BETTING"
    Box(
        modifier = modifier
            .background(GridGreen)
            .border(BorderStroke(1.5.dp, GoldBorder))
    ) {
        Image(
            painter = painterResource(R.drawable.gundu_bet_grid_only),
            contentDescription = "Betting numbers",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        if (!bettingOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .background(HeaderGreen.copy(alpha = 0.9f))
                    .border(1.dp, GoldBorder)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "BETS CLOSED",
                    color = BetGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp)
                .border(BorderStroke(1.5.dp, GoldBorder))
                .background(HeaderGreen)
                .clickable(enabled = enabled) { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            Text("↻", color = BetGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                listOf(1, 2, 3, 4).forEach { num ->
                    GunduGridHotspot(
                        betAmount = bets[num],
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onNumberBet(num) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GunduGridHotspot(
                    betAmount = bets[5],
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onNumberBet(5) }
                )
                GunduPeacockChipOverlay(
                    chipAmount = selectedChip,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                    onClick = onCycleChip
                )
                GunduGridHotspot(
                    betAmount = bets[6],
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onNumberBet(6) }
                )
            }
        }
    }
}

@Composable
private fun GunduGridHotspot(
    betAmount: Int?,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val hasBet = betAmount != null && betAmount > 0
    Box(
        modifier = modifier.clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (hasBet) {
            Box(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .size(26.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.gundu_chip_red),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = if (betAmount!! >= 1000) "${betAmount / 1000}K" else "$betAmount",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun GunduPeacockChipOverlay(
    chipAmount: Int,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Cover baked chip value when user cycles away from 50
        if (chipAmount != 50) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 38.dp)
                    .background(Color(0xFFC62828), CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (chipAmount >= 1000) "${chipAmount / 1000}K" else "$chipAmount",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Dice result — text only, no image ─────────────────────────────────────────
@Composable
private fun GunduDiceResultBar(diceResult: Int, diceCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GridGreen)
            .border(BorderStroke(1.dp, GoldBorder.copy(alpha = 0.55f)))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (diceResult > 0) "Dice Rolled: $diceResult($diceCount)" else "Dice Rolled: —",
            color = BetGold,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GunduReferenceHistoryStrip(
    recentResults: List<Int>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(HistoryTan)
            .border(BorderStroke(1.dp, Color(0xFF8B6914)))
    ) {
        recentResults.take(6).forEachIndexed { index, num ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (index < recentResults.take(6).lastIndex)
                            Modifier.border(BorderStroke(0.5.dp, Color(0xFF8B6914)))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$num",
                    color = HistoryNum,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif
                )
            }
        }
    }
}

// ─── ExoPlayer (TextureView) — safe detach on back ───────────────────────────
@Composable
private fun LiveVideoPlayer(videoResId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    val released = remember { booleanArrayOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/$videoResId")
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    fun releasePlayer() {
        if (released[0]) return
        released[0] = true
        playerViewRef.value?.player = null
        playerViewRef.value = null
        try {
            exoPlayer.playWhenReady = false
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        } catch (_: Exception) {
            // already released
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!released[0]) exoPlayer.playWhenReady = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (!released[0]) exoPlayer.playWhenReady = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releasePlayer()
        }
    }

    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.gundu_live_player_view, null, false) as PlayerView).also { view ->
                view.player = exoPlayer
                playerViewRef.value = view
            }
        },
        update = { view ->
            if (!released[0] && view.player !== exoPlayer) {
                view.player = exoPlayer
            }
            playerViewRef.value = view
        },
        onRelease = { view ->
            view.player = null
            if (playerViewRef.value === view) playerViewRef.value = null
        },
        modifier = modifier
    )
}
