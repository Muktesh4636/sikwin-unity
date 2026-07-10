package com.sikwin.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GunduAtaLiveScreen(onBack: () -> Unit) {

    // Pulsing glow animation for the LIVE badge
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Rotating ring animation around the icon
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gundu Ata Live", color = PrimaryYellow, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryYellow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBackground)
            )
        },
        containerColor = BlackBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A0000),
                            BlackBackground
                        ),
                        radius = 900f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
            ) {

                // Animated icon with rotating ring
                Box(contentAlignment = Alignment.Center) {
                    // Rotating outer ring
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .graphicsLayer { rotationZ = rotation }
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color(0xFFEF5350).copy(alpha = 0.6f),
                                        Color.Transparent,
                                        Color.Transparent,
                                    )
                                )
                            )
                    )
                    // Static inner circle
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF3A0000), Color(0xFF1A0000))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎲", fontSize = 52.sp)
                    }
                }

                // LIVE badge (pulsing)
                Surface(
                    modifier = Modifier
                        .graphicsLayer(
                            alpha = pulseAlpha,
                            scaleX = pulseScale,
                            scaleY = pulseScale
                        ),
                    color = Color(0xFFEF5350),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            "LIVE",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Title
                Text(
                    "Gundu Ata Live",
                    color = TextWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )

                // Coming soon card
                Surface(
                    color = SurfaceColor,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("⏳", fontSize = 36.sp)
                        Text(
                            "Coming Soon!",
                            color = PrimaryYellow,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Gundu Ata Live is under development.\nScheduled live rounds with real-time betting will be available very soon. Stay tuned!",
                            color = TextGrey,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Notify strip
                Surface(
                    color = PrimaryYellow.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🔔", fontSize = 20.sp)
                        Text(
                            "We'll notify you the moment live rounds go live.",
                            color = PrimaryYellow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
