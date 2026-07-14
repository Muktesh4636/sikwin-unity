package com.sikwin.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sikwin.app.R
import com.sikwin.app.ui.theme.*
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel

/**
 * New "Hero" theme home — matches the casino lobby sample (LIVE banner + categories + featured card).
 * Games still launch through existing [onGameClick] / [onNavigate] routes.
 */
@Composable
fun HeroHomeScreen(
    viewModel: GunduAtaViewModel,
    onGameClick: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    var showLoginPopup by remember { mutableStateOf(false) }
    var showGunduAtaChoiceDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("hot") }

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
        containerColor = Color.Black,
        bottomBar = {
            HeroBottomBar(
                onHome = { /* already home */ },
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
            HeroTopBar(
                balance = viewModel.wallet?.balance ?: "0.00",
                isLoggedIn = viewModel.loginSuccess,
                onDeposit = { requireLoginOr { onNavigate("deposit") } },
                onLogin = { onNavigate("login") }
            )

            // LIVE CASINO hero banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        requireLoginOr { showGunduAtaChoiceDialog = true }
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.gundu_ata_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        "LIVE CASINO",
                        color = PrimaryYellow,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif
                    )
                    Text(
                        "REAL DEALERS. REAL THRILLS.",
                        color = TextWhite.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = PrimaryYellow,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "PLAY NOW  ›",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroCategoryChip("HOT", Icons.Default.LocalFireDepartment, selectedCategory == "hot") {
                    selectedCategory = "hot"
                }
                HeroCategoryChip("CRICKET", Icons.Default.SportsCricket, selectedCategory == "cricket") {
                    selectedCategory = "cricket"
                    requireLoginOr { onNavigate("ipl") }
                }
                HeroCategoryChip("COLOUR", Icons.Default.Palette, selectedCategory == "colour") {
                    selectedCategory = "colour"
                    requireLoginOr { onNavigate("colour_game") }
                }
                HeroCategoryChip("LIVE", Icons.Default.Videocam, selectedCategory == "live") {
                    selectedCategory = "live"
                    requireLoginOr { showGunduAtaChoiceDialog = true }
                }
                HeroCategoryChip("MORE", Icons.Default.Apps, selectedCategory == "more") {
                    selectedCategory = "more"
                    onNavigate("me")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Featured Gundu Ata card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A1400), Color(0xFF0D0D0D))
                        )
                    )
                    .border(1.dp, PrimaryYellow.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable { requireLoginOr { showGunduAtaChoiceDialog = true } }
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = PrimaryYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HOT GAME", color = PrimaryYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = PrimaryYellow, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "TRENDING",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "GUNDU ATA",
                        color = PrimaryYellow,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif
                    )
                    Text(
                        "The Classic. The Favorite. The Real Game.",
                        color = TextGrey,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = PrimaryYellow,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "PLAY NOW  ›",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Image(
                            painter = painterResource(id = R.drawable.ic_gundu_ata_nav),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroTopBar(
    balance: String,
    isLoggedIn: Boolean,
    onDeposit: () -> Unit,
    onLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "GUNDU ATA",
            color = PrimaryYellow,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.weight(1f)
        )
        if (isLoggedIn) {
            Surface(color = SurfaceColor, shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("₹ $balance", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryYellow)
                            .clickable(onClick = onDeposit),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Deposit", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else {
            TextButton(onClick = onLogin) {
                Text(stringResource(R.string.login), color = PrimaryYellow, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroCategoryChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) PrimaryYellow else SurfaceColor)
                .border(
                    1.dp,
                    if (selected) PrimaryYellow else PrimaryYellow.copy(alpha = 0.35f),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) Color.Black else PrimaryYellow,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) PrimaryYellow else TextGrey,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun HeroBottomBar(
    onHome: () -> Unit,
    onPromo: () -> Unit,
    onVip: () -> Unit,
    onWallet: () -> Unit,
    onProfile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroNavItem("HOME", Icons.Default.Home, true, onHome)
            HeroNavItem("PROMO", Icons.Default.CardGiftcard, false, onPromo)
            Box(
                modifier = Modifier
                    .offset(y = (-10).dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimaryYellow)
                    .clickable(onClick = onVip),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WorkspacePremium, contentDescription = "VIP", tint = Color.Black, modifier = Modifier.size(28.dp))
            }
            HeroNavItem("WALLET", Icons.Default.AccountBalanceWallet, false, onWallet)
            HeroNavItem("PROFILE", Icons.Default.Person, false, onProfile)
        }
    }
}

@Composable
private fun HeroNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) PrimaryYellow else TextGrey, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) PrimaryYellow else TextGrey, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
