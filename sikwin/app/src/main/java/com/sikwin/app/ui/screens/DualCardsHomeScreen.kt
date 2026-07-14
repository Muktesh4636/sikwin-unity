package com.sikwin.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sikwin.app.R
import com.sikwin.app.ui.theme.*
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel

private val GoldLight = Color(0xFFFFE082)
private val GoldMid = Color(0xFFFFD54F)
private val GoldDeep = Color(0xFFC9A227)
private val GoldBrush = Brush.verticalGradient(listOf(GoldLight, GoldMid, GoldDeep))
private val ScreenBlack = Color(0xFF000000)
private val CardDark = Color(0xFF0D0D0D)

/**
 * Dual Cards home theme — coded Compose UI matching PHOTO dual-cards design.
 * Selectable from Profile → Themes.
 */
@Composable
fun DualCardsHomeScreen(
    viewModel: GunduAtaViewModel,
    onGameClick: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    var showLoginPopup by remember { mutableStateOf(false) }
    var showGunduAtaChoiceDialog by remember { mutableStateOf(false) }

    if (showGunduAtaChoiceDialog) {
        GunduAtaChoiceDialog(
            onDismiss = { showGunduAtaChoiceDialog = false },
            onPlayLive = { onNavigate("gundu_ata_live") },
            onPlayNormal = { onGameClick("gundu_ata") }
        )
    }

    if (showLoginPopup) {
        AlertDialog(
            onDismissRequest = { showLoginPopup = false },
            title = { Text(stringResource(R.string.login_required), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.login_required_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginPopup = false
                        onNavigate("signup")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
                ) {
                    Text(stringResource(R.string.sign_up), color = BlackBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginPopup = false }) {
                    Text(stringResource(R.string.cancel), color = TextGrey)
                }
            },
            containerColor = SurfaceColor,
            titleContentColor = TextWhite,
            textContentColor = TextGrey
        )
    }

    fun requireLoginOr(action: () -> Unit) {
        if (!viewModel.loginSuccess) showLoginPopup = true else action()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkSession()
                if (viewModel.loginSuccess) {
                    viewModel.fetchWallet()
                    viewModel.fetchProfile()
                    viewModel.startTimerPreloading()
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopTimerPreloading()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopTimerPreloading()
        }
    }

    Scaffold(
        containerColor = ScreenBlack,
        bottomBar = {
            DualCardsBottomBar(
                onHome = { },
                onPromo = { onNavigate("affiliate") },
                onVip = { onNavigate("leaderboard") },
                onWallet = { requireLoginOr { onNavigate("wallet") } },
                onProfile = { onNavigate("me") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            DualCardsTopBar(
                balance = viewModel.wallet?.balance ?: "0.00",
                isLoggedIn = viewModel.loginSuccess,
                onMenu = { onNavigate("me") },
                onDeposit = { requireLoginOr { onNavigate("deposit") } },
                onLogin = { onNavigate("login") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // LIVE CASINO hero banner (artwork includes title + PLAY NOW)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { requireLoginOr { showGunduAtaChoiceDialog = true } }
            ) {
                LiveCasinoBannerImage(
                    defaultResId = R.drawable.live_casino_banner,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Circular category icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DualCategoryCircle("HOT", Icons.Default.LocalFireDepartment) {
                    requireLoginOr { showGunduAtaChoiceDialog = true }
                }
                DualCategoryCircle("CRICKET", Icons.Default.SportsCricket) {
                    requireLoginOr { onNavigate("ipl") }
                }
                DualCategoryCircle("SLOTS", Icons.Default.Casino) {
                    requireLoginOr { onNavigate("colour_game") }
                }
                DualCategoryCircle("LIVE", Icons.Default.Videocam) {
                    requireLoginOr { onNavigate("gundu_ata_live") }
                }
                DualCategoryCircle("MORE", Icons.Default.MoreHoriz) {
                    onNavigate("me")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dual game cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gundu Ata card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardDark)
                        .border(1.dp, GoldDeep.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { requireLoginOr { showGunduAtaChoiceDialog = true } }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.money_decoration),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1200))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "GA",
                                color = GoldMid,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                "GUNDU ATA",
                                color = GoldMid,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "PLAY & WIN BIG",
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        GoldPlayButton(text = "PLAY", fullWidth = true) {
                            requireLoginOr { showGunduAtaChoiceDialog = true }
                        }
                    }
                }

                // Andar Bahar style card → Colour Game
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0B3D2E))
                        .border(1.dp, GoldDeep.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { requireLoginOr { onNavigate("colour_game") } }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "ANDAR\nBAHAR",
                                color = GoldMid,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                lineHeight = 26.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                                PlayingCardFace("A♠", Color.Black)
                                PlayingCardFace("K♥", Color(0xFFB71C1C))
                            }
                        }
                        GoldPlayButton(text = "PLAY", fullWidth = true) {
                            requireLoginOr { onNavigate("colour_game") }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DualCardsTopBar(
    balance: String,
    isLoggedIn: Boolean,
    onMenu: () -> Unit,
    onDeposit: () -> Unit,
    onLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = GoldMid, modifier = Modifier.size(26.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "GA",
                color = GoldMid,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                lineHeight = 22.sp
            )
            Text(
                "GUNDU ATA",
                color = GoldMid,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        if (isLoggedIn) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, GoldDeep.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    .clickable(onClick = onDeposit),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = GoldMid, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("₹$balance", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(GoldBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            TextButton(onClick = onLogin) {
                Text(stringResource(R.string.login), color = GoldMid, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DualCategoryCircle(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF121212))
                .border(1.5.dp, GoldMid, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = GoldMid, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = GoldMid, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GoldPlayButton(text: String, fullWidth: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(GoldBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.Black,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = if (fullWidth) 0.dp else 18.dp)
        )
    }
}

@Composable
private fun PlayingCardFace(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 58.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun DualCardsBottomBar(
    onHome: () -> Unit,
    onPromo: () -> Unit,
    onVip: () -> Unit,
    onWallet: () -> Unit,
    onProfile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .navigationBarsPadding()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DualNavItem("HOME", Icons.Default.Home, true, onHome)
        DualNavItem("PROMO", Icons.Default.CardGiftcard, false, onPromo)
        DualNavItem("VIP", Icons.Default.WorkspacePremium, false, onVip)
        DualNavItem("WALLET", Icons.Default.AccountBalanceWallet, false, onWallet)
        DualNavItem("PROFILE", Icons.Default.Person, false, onProfile)
    }
}

@Composable
private fun DualNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) GoldMid else GoldDeep.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            color = if (selected) GoldMid else GoldDeep.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
