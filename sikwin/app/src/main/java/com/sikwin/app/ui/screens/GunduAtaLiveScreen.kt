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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
import com.sikwin.app.R
import com.sikwin.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
// ─── Chip amounts ────────────────────────────────────────────────────────────
private val CHIPS = listOf(10, 50, 100, 500, 1000)
private const val BET_WINDOW_SECONDS = 30

private val DarkGreen = Color(0xFF062006)
private val HeaderGreen = Color(0xFF0A2A0A)
private val GoldBorder = Color(0xFFD4A017)
private val ResultRed = Color(0xFF5A0802)
private val HistoryTan = Color(0xFFD2A653)
private val HistoryNum = Color(0xFF2A2A2A)

@Composable
fun GunduAtaLiveScreen(onBack: () -> Unit) {
    var selectedChip by remember { mutableStateOf(50) }
    var bets by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var phase by remember { mutableStateOf("BETTING") } // BETTING | ROLLING | RESULT
    var bettingOpen by remember { mutableStateOf(true) }
    var secondsLeft by remember { mutableStateOf(BET_WINDOW_SECONDS) }
    var diceResult by remember { mutableStateOf(0) }
    var diceCount by remember { mutableStateOf(0) }
    var recentResults by remember { mutableStateOf(listOf(4, 6, 5, 1, 6, 4)) }
    var winMessage by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf(5000) }
    var statusText by remember { mutableStateOf("Tap a number to place your bet") }
    var leaving by remember { mutableStateOf(false) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

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

    val bannerText = if (bettingOpen) "PLACE YOUR BETS" else "BETS CLOSED"

    fun clearBets() {
        if (!bettingOpen || phase != "BETTING") return
        val refund = bets.values.sum()
        balance += refund
        bets = emptyMap()
        statusText = "Bets cleared — tap a number to place bet"
    }

    fun placeBetOn(number: Int) {
        if (leaving || !bettingOpen || phase != "BETTING") {
            if (!bettingOpen) statusText = "Bets are closed"
            return
        }
        if (number !in 1..6) return
        if (balance < selectedChip) {
            statusText = "Insufficient balance"
            return
        }
        balance -= selectedChip
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

    fun hitTest(offset: Offset, size: IntSize): Int? {
        if (size.width <= 0 || size.height <= 0) return null
        val x = offset.x
        val y = offset.y
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val row = if (y < h * 0.5f) 0 else 1
        val col = ((x / w) * 4f).toInt().coerceIn(0, 3)
        return when {
            row == 0 -> col + 1
            col == 0 -> 5
            col == 3 -> 6
            else -> 0 // peacock / chip zone
        }
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
                balance += win
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGreen)
            .statusBarsPadding()
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderGreen)
                .border(BorderStroke(1.dp, GoldBorder))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { goBack() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryYellow,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                "GUNDU ATA",
                color = PrimaryYellow,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                textAlign = TextAlign.Center
            )
            Column(horizontalAlignment = Alignment.End) {
                Text("₹$balance", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("EXP: ₹0.00", color = PrimaryYellow.copy(alpha = 0.85f), fontSize = 10.sp)
            }
        }

        // ── Live video (slightly larger) ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f)
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

        // ── PLACE YOUR BETS / BETS CLOSED card (larger, covers full width) ────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.gundu_header_bar),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HeaderGreen.copy(alpha = 0.55f))
                    .border(BorderStroke(1.5.dp, GoldBorder))
            )
            Text(
                text = bannerText,
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                fontFamily = FontFamily.Serif,
                letterSpacing = 1.8.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .size(48.dp)
                    .clickable(enabled = bettingOpen && phase == "BETTING") { clearBets() },
                contentAlignment = Alignment.Center
            ) {
                Text("↻", color = Color(0xFFFFD700), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Number grid only (image has no PLACE YOUR BETS text) ──────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.44f)
                .onSizeChanged { boardSize = it }
        ) {
            val rowH = maxHeight / 2f
            val colW = maxWidth / 4f

            Image(
                painter = painterResource(R.drawable.gundu_live_board),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // Chip overlays on numbers that have bets (visual only)
            bets.forEach { (number, amount) ->
                val (ox, oy) = when (number) {
                    1 -> 0 to 0
                    2 -> 1 to 0
                    3 -> 2 to 0
                    4 -> 3 to 0
                    5 -> 0 to 1
                    else -> 3 to 1
                }
                val chipSize = if (colW < rowH) colW * 0.42f else rowH * 0.42f
                Box(
                    modifier = Modifier
                        .offset(x = colW * ox, y = rowH * oy)
                        .size(width = colW, height = rowH),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.gundu_chip_red),
                        contentDescription = null,
                        modifier = Modifier.size(chipSize),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        if (amount >= 1000) "${amount / 1000}K" else "$amount",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }

            // Center selected chip label
            Box(
                modifier = Modifier
                    .offset(x = colW, y = rowH)
                    .size(width = colW * 2f, height = rowH),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedChip >= 1000) "${selectedChip / 1000}K" else "$selectedChip",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Full-size tap layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(phase, bettingOpen, selectedChip, balance, bets) {
                        detectTapGestures { tap ->
                            if (phase != "BETTING" || !bettingOpen) return@detectTapGestures
                            when (val hit = hitTest(tap, boardSize)) {
                                null -> Unit
                                0 -> cycleChip()
                                else -> placeBetOn(hit)
                            }
                        }
                    }
            )
        }

        // ── Chip selector ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A280A))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CHIPS.forEach { chip ->
                val selected = selectedChip == chip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Color(0xFFC62828) else Color(0xFF3E2723))
                        .border(
                            1.5.dp,
                            if (selected) Color(0xFFFFD700) else Color(0xFF6D4C41),
                            RoundedCornerShape(18.dp)
                        )
                        .clickable(enabled = bettingOpen && phase == "BETTING") {
                            selectedChip = chip
                            statusText = "Chip ₹$chip — tap a number to place bet"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (chip >= 1000) "1K" else "$chip",
                        color = if (bettingOpen) Color.White else Color.White.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Text(
            text = if (bettingOpen) "${secondsLeft}s left to bet" else "Bets closed — ${secondsLeft}s",
            color = Color(0xFFFFD700),
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A280A))
                .padding(bottom = 4.dp)
        )

        // ── Dice result ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(ResultRed)
                .border(BorderStroke(1.dp, GoldBorder.copy(alpha = 0.55f))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (diceResult > 0) "Dice Rolled: $diceResult($diceCount)" else "Dice Rolled: —",
                color = Color(0xFFFFD700),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif
            )
        }

        // ── History strip ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(HistoryTan)
                .border(1.dp, Color(0xFF8B6914))
                .navigationBarsPadding()
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
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif
                    )
                }
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
