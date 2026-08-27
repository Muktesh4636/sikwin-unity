package com.sikwin.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sikwin.app.R
import com.sikwin.app.ui.theme.GoldOnWhite
import com.sikwin.app.ui.theme.rememberAppScreenColors

/**
 * Slide-out side menu matching sidebar.png (4rabet-style, Gundu Ata black/gold).
 */
@Composable
fun SideMenuOverlay(
    open: Boolean,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    onPlayGunduAta: () -> Unit,
    requireLoginOr: (action: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val colors = rememberAppScreenColors()
    val accent = if (colors.isWhite) GoldOnWhite else DualGoldMid
    val panelOffset by animateFloatAsState(
        targetValue = if (open) 0f else -1f,
        animationSpec = tween(durationMillis = 280),
        label = "sideMenuOffset"
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (open) 0.6f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "sideMenuScrim"
    )

    if (!open && panelOffset <= -0.99f && scrimAlpha <= 0.01f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (colors.isWhite) {
                        Color.Black.copy(alpha = scrimAlpha * 0.35f)
                    } else {
                        Color.Black.copy(alpha = scrimAlpha)
                    }
                )
                .then(
                    if (open) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { onClose() }
                        }
                    } else Modifier
                )
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.78f)
                .graphicsLayer { translationX = panelOffset * size.width }
                .background(colors.background)
                .then(
                    if (colors.isWhite) {
                        Modifier.border(width = 1.dp, color = colors.border)
                    } else Modifier
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable(enabled = false, onClick = {})
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Menu",
                    color = colors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                        .clickable {
                            onClose()
                            requireLoginOr { onNavigate("deposit") }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "DEPOSIT",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "OUR APPLICATIONS:",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (colors.isWhite) Color(0xFFF3F4F6) else Color.White.copy(alpha = 0.1f)
                            )
                            .clickable {
                                onClose()
                                // iOS app not available yet
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhoneIphone,
                            contentDescription = "iOS",
                            tint = colors.text,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (colors.isWhite) Color(0xFFF3F4F6) else Color.White.copy(alpha = 0.1f)
                            )
                            .clickable {
                                onClose()
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://gunduata.tech/GunduAta.apk")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = "Android",
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "NEW",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SideMenuItem(
                    icon = Icons.Default.GridView,
                    label = "Main",
                    onClick = {
                        onClose()
                        onNavigate("home")
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.Album,
                    label = "Auto Roulette",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("roulette") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.ShowChart,
                    label = "Stock Market",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("trading") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.Pets,
                    label = "Chicken Road",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("chicken_road") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.Egg,
                    label = "Chicken Road 2",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("chicken_road_2") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.BlurOn,
                    label = "Vortex",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("vortex") }
                    }
                )
                SideMenuItem(
                    label = "Pride",
                    highlight = true,
                    customIcon = {
                        Image(
                            painter = painterResource(id = R.drawable.gundu_ata_logo_gold),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(1.dp, accent, CircleShape)
                        )
                    },
                    onClick = {
                        onClose()
                        requireLoginOr { onPlayGunduAta() }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.SportsCricket,
                    label = "Cricket",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("sports?sport=cricket") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.Casino,
                    label = "Rangu",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("colour_game") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.MonetizationOn,
                    label = "Chit Pat",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("coin") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.CardGiftcard,
                    label = "Bonuses",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("affiliate") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.Person,
                    label = "Profile",
                    onClick = {
                        onClose()
                        onNavigate("me")
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "Deposit",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("deposit") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.Payments,
                    label = "Withdrawal",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("withdraw") }
                    }
                )
                SideMenuItem(
                    icon = Icons.Default.History,
                    label = "Payments history",
                    onClick = {
                        onClose()
                        requireLoginOr { onNavigate("transactions") }
                    }
                )
            }
        }
    }
}

@Composable
private fun SideMenuItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    highlight: Boolean = false,
    customIcon: (@Composable () -> Unit)? = null
) {
    val colors = rememberAppScreenColors()
    val accent = if (colors.isWhite) GoldOnWhite else DualGoldMid
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (customIcon != null) {
                customIcon()
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            label,
            color = if (highlight) accent else colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
