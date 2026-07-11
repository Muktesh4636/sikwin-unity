package com.sikwin.app.ui.screens

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

// Number tile background gradient (gold felt)
private val TileBg = Brush.verticalGradient(listOf(Color(0xFFD4AF37), Color(0xFFC8960A)))
private val TileBgSelected = Brush.verticalGradient(listOf(Color(0xFFFFF176), Color(0xFFFFCA28)))
private val TableGreen = Color(0xFF1B5E20)
private val DarkGreen = Color(0xFF0D3B0D)
private val ResultRed = Color(0xFF6B0000)

@Composable
fun GunduAtaLiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // ── Game state ────────────────────────────────────────────────────────────
    var selectedNumber by remember { mutableStateOf<Int?>(null) }
    var selectedChip by remember { mutableStateOf(50) }
    var phase by remember { mutableStateOf("BETTING") }     // BETTING | ROLLING | RESULT
    var diceResult by remember { mutableStateOf(0) }
    var diceCount by remember { mutableStateOf(0) }
    var recentResults by remember { mutableStateOf(listOf(4, 6, 5, 1, 6, 4)) }
    var winMessage by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf(5000) }

    // Pulse for LIVE badge
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val liveDot by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "liveDot"
    )

    // Auto-roll simulation when phase == ROLLING
    LaunchedEffect(phase) {
        if (phase == "ROLLING") {
            delay(2500)
            val r = (1..6).random()
            val c = (1..6).random()
            diceResult = r
            diceCount = c
            recentResults = (listOf(r) + recentResults).take(10)
            winMessage = if (selectedNumber == r) "🎉 YOU WIN! +₹${selectedChip * c}" else "Try again!"
            phase = "RESULT"
            delay(3000)
            if (selectedNumber == r) balance += selectedChip * c
            winMessage = ""
            phase = "BETTING"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGreen)
    ) {
        // ── Video player area ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            // Live dealer video (looping)
            LiveVideoPlayer(
                videoResId = R.raw.gundu_ata_live_dealer,
                modifier = Modifier.fillMaxSize()
            )

            // Top bar overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryYellow,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "GUNDU ATA",
                    color = PrimaryYellow,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹$balance", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("EXP: ₹0.00", color = TextGrey, fontSize = 10.sp)
                }
            }

            // LIVE badge + current dice (bottom of video)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(color = Color(0xFFEF5350), shape = RoundedCornerShape(4.dp)) {
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
            }

            if (diceResult > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$diceResult",
                        color = Color(0xFF3E1A00),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }
            }

            // Win/lose banner
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

        // ── Betting area ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1B5E20), Color(0xFF0D3B0D)))
                )
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // "Place your bets" header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PLACE YOUR BETS",
                    color = PrimaryYellow,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { selectedNumber = null },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Clear bets", tint = PrimaryYellow, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Number grid — row 1: 1 2 3 4
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFF5D4037), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                listOf(1, 2, 3, 4).forEachIndexed { index, num ->
                    NumberTile(
                        number = num,
                        selected = selectedNumber == num,
                        enabled = phase == "BETTING",
                        modifier = Modifier.weight(1f),
                        showDivider = index < 3,
                        onClick = { selectedNumber = if (selectedNumber == num) null else num }
                    )
                }
            }

            // Number grid — row 2: 5  [CHIP ZONE]  6
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFF5D4037))
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            ) {
                NumberTile(
                    number = 5,
                    selected = selectedNumber == 5,
                    enabled = phase == "BETTING",
                    modifier = Modifier.weight(1f),
                    showDivider = true,
                    onClick = { selectedNumber = if (selectedNumber == 5) null else 5 }
                )

                // Centre chip display zone
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(80.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF2E7D32), Color(0xFF1B5E20)))),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🦚", fontSize = 18.sp)
                        ChipBadge(amount = selectedChip)
                        Text("🦚", fontSize = 18.sp)
                    }
                }

                NumberTile(
                    number = 6,
                    selected = selectedNumber == 6,
                    enabled = phase == "BETTING",
                    modifier = Modifier.weight(1f),
                    showDivider = false,
                    showLeftDivider = true,
                    onClick = { selectedNumber = if (selectedNumber == 6) null else 6 }
                )
            }

            Spacer(Modifier.height(10.dp))

            // Chip selector row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CHIPS.forEach { chip ->
                    ChipButton(
                        amount = chip,
                        selected = selectedChip == chip,
                        onClick = { selectedChip = chip },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // BET / ROLL button
            Button(
                onClick = {
                    if (phase == "BETTING" && selectedNumber != null && balance >= selectedChip) {
                        balance -= selectedChip
                        phase = "ROLLING"
                    }
                },
                enabled = phase == "BETTING" && selectedNumber != null && balance >= selectedChip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryYellow,
                    disabledContainerColor = Color(0xFF5D4037)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = when (phase) {
                        "ROLLING" -> "Rolling..."
                        "RESULT" -> "Waiting..."
                        else -> if (selectedNumber == null) "Select a Number" else "BET ₹$selectedChip on $selectedNumber"
                    },
                    color = if (phase == "BETTING" && selectedNumber != null) Color.Black else TextGrey,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Dice result bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ResultRed, RoundedCornerShape(6.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (diceResult > 0) "Dice Rolled: $diceResult($diceCount)" else "Place your bet and roll!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Recent results ticker
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(recentResults) { num ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF5D4037), RoundedCornerShape(4.dp))
                            .border(1.dp, PrimaryYellow.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$num",
                            color = PrimaryYellow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─── Number tile ──────────────────────────────────────────────────────────────
@Composable
private fun NumberTile(
    number: Int,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    showDivider: Boolean,
    showLeftDivider: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(80.dp)
            .background(if (selected) TileBgSelected else TileBg)
            .clickable(enabled = enabled) { onClick() }
            .then(if (showDivider) Modifier.border(
                BorderStroke(width = 1.5.dp, color = Color(0xFF5D4037))
            ) else Modifier)
            .then(if (showLeftDivider) Modifier.border(
                BorderStroke(width = 1.5.dp, color = Color(0xFF5D4037))
            ) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            color = if (selected) Color(0xFF3E1A00) else Color(0xFF3E1A00),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            fontFamily = FontFamily.Serif
        )
    }
}

// ─── Chip badge (the circular chip shown in the centre zone) ──────────────────
@Composable
private fun ChipBadge(amount: Int, size: Dp = 52.dp) {
    val (bg, ring) = when {
        amount >= 1000 -> Color(0xFF7B1FA2) to Color(0xFFE040FB)
        amount >= 500  -> Color(0xFF1565C0) to Color(0xFF42A5F5)
        amount >= 100  -> Color(0xFF2E7D32) to Color(0xFF66BB6A)
        amount >= 50   -> Color(0xFFC62828) to Color(0xFFEF9A9A)
        else           -> Color(0xFF4E342E) to Color(0xFFBCAAA4)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ring)
            .padding(4.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (amount >= 1000) "${amount / 1000}K" else "$amount",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (size >= 48.dp) 13.sp else 10.sp
        )
    }
}

// ─── Chip selector button ─────────────────────────────────────────────────────
@Composable
private fun ChipButton(amount: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clickable { onClick() }
            .then(
                if (selected) Modifier.shadow(4.dp, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        ChipBadge(amount = amount, size = if (selected) 44.dp else 36.dp)
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
