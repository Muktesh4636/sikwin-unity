package com.sikwin.app.ui.screens

import android.net.Uri
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sikwin.app.R
import com.sikwin.app.ui.theme.*
import kotlinx.coroutines.delay

// ─── Chip amounts ────────────────────────────────────────────────────────────
private val CHIPS = listOf(10, 50, 100, 500, 1000)

private val DarkGreen = Color(0xFF062006)
private val HeaderGreen = Color(0xFF0A2A0A)
private val GoldBorder = Color(0xFFD4A017)
private val ResultRed = Color(0xFF5A0802)
private val HistoryTan = Color(0xFFD2A653)
private val HistoryNum = Color(0xFF2A2A2A)

@Composable
fun GunduAtaLiveScreen(onBack: () -> Unit) {
    var selectedNumber by remember { mutableStateOf<Int?>(null) }
    var selectedChip by remember { mutableStateOf(50) }
    var phase by remember { mutableStateOf("BETTING") }
    var diceResult by remember { mutableStateOf(0) }
    var diceCount by remember { mutableStateOf(0) }
    var recentResults by remember { mutableStateOf(listOf(4, 6, 5, 1, 6, 4)) }
    var winMessage by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf(5000) }

    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val liveDot by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "liveDot"
    )

    fun placeBetOn(number: Int) {
        if (phase != "BETTING" || balance < selectedChip) return
        selectedNumber = number
        balance -= selectedChip
        phase = "ROLLING"
    }

    LaunchedEffect(phase) {
        if (phase == "ROLLING") {
            delay(2500)
            val r = (1..6).random()
            val c = (1..6).random()
            diceResult = r
            diceCount = c
            recentResults = (listOf(r) + recentResults).take(6)
            winMessage = if (selectedNumber == r) "YOU WIN! +₹${selectedChip * c}" else "Try again!"
            phase = "RESULT"
            delay(3000)
            if (selectedNumber == r) balance += selectedChip * c
            winMessage = ""
            selectedNumber = null
            phase = "BETTING"
        }
    }

    // Exact reference layout:
    // [GUNDU ATA header] → [Video] → [board image: PLACE YOUR BETS + numbers] → [result] → [history]
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
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
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

        // ── Live video ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .border(BorderStroke(1.dp, GoldBorder))
                .background(Color(0xFF0A280A))
        ) {
            LiveVideoPlayer(
                videoResId = R.raw.gundu_ata_live_dealer,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                color = Color(0xFFEF5350),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .graphicsLayer(alpha = liveDot)
                    )
                    Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
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

        // ── Exact betting board (from your screenshot) ────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f)
        ) {
            Image(
                painter = painterResource(R.drawable.gundu_live_board),
                contentDescription = "Place your bets",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // Transparent hotspots matching the reference grid
            // Header band ~ top 12%; number rows below
            val headerH = maxHeight * 0.125f
            val rowH = (maxHeight - headerH) / 2f
            val colW = maxWidth / 4f

            // Refresh (top-right of PLACE YOUR BETS)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(width = maxWidth * 0.14f, height = headerH)
                    .clickable { selectedNumber = null }
            )

            // Row 1: 1 2 3 4
            listOf(1, 2, 3, 4).forEachIndexed { index, num ->
                Box(
                    modifier = Modifier
                        .offset(x = colW * index, y = headerH)
                        .size(width = colW, height = rowH)
                        .clickable(enabled = phase == "BETTING") { placeBetOn(num) }
                )
            }

            // Row 2: 5 | chip zone | 6
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = headerH + rowH)
                    .size(width = colW, height = rowH)
                    .clickable(enabled = phase == "BETTING") { placeBetOn(5) }
            )
            // Chip cycle (center peacock zone)
            Box(
                modifier = Modifier
                    .offset(x = colW, y = headerH + rowH)
                    .size(width = colW * 2f, height = rowH)
                    .clickable {
                        val idx = CHIPS.indexOf(selectedChip)
                        selectedChip = CHIPS[(idx + 1) % CHIPS.size]
                    },
                contentAlignment = Alignment.Center
            ) {
                // Live chip amount centered on the red chip in the board art
                Text(
                    text = if (selectedChip >= 1000) "${selectedChip / 1000}K" else "$selectedChip",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = colW * 3f, y = headerH + rowH)
                    .size(width = colW, height = rowH)
                    .clickable(enabled = phase == "BETTING") { placeBetOn(6) }
            )

            // Selected highlight
            selectedNumber?.let { num ->
                val (ox, oy) = when (num) {
                    1 -> 0f to 0f
                    2 -> 1f to 0f
                    3 -> 2f to 0f
                    4 -> 3f to 0f
                    5 -> 0f to 1f
                    else -> 3f to 1f
                }
                Box(
                    modifier = Modifier
                        .offset(x = colW * ox, y = headerH + rowH * oy)
                        .size(width = colW, height = rowH)
                        .border(2.5.dp, Color(0xFFFFF59D))
                )
            }
        }

        // ── Dice result (exact style from screenshot) ─────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
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
                .height(44.dp)
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

// ─── ExoPlayer-backed video player ───────────────────────────────────────────
@Composable
private fun LiveVideoPlayer(videoResId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_PAUSE  -> exoPlayer.playWhenReady = false
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier
    )
}
