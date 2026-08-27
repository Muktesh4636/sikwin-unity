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
import com.sikwin.app.utils.MoneyFormat

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
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLoginPopup by remember { mutableStateOf(false) }
    var showGunduAtaChoiceDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("hot") }
    var searchQuery by remember { mutableStateOf("") }

    if (showGunduAtaChoiceDialog) {
        GunduAtaChoiceDialog(
            onDismiss = { showGunduAtaChoiceDialog = false },
            onPlayLive = { onNavigate("gundu_ata_live") },
            onPlayNormal = { onNavigate("gundu_ata_web") }
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
        if (!viewModel.loginSuccess) {
            showLoginPopup = true
        } else {
            action()
        }
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
                selectedTab = DualNavTab.HOME,
                onHome = { /* already home */ },
                onLive = { onNavigate("live") },
                onCasino = { requireLoginOr { onNavigate("casino_games") } },
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

            SearchBar(onSearch = { searchQuery = it })

            // Promo banners — infinite side-scroll (360° loop)
            HomePromoBannerCarousel(
                banners = listOf(
                    HomePromoBanner(
                        imageRes = R.drawable.live_casino_banner,
                        allowCustomLiveCasino = true,
                        onPlayNow = { requireLoginOr { showGunduAtaChoiceDialog = true } }
                    ),
                    HomePromoBanner(
                        imageRes = R.drawable.cock_fight_banner,
                        onPlayNow = { requireLoginOr { onNavigate("cock_fight") } }
                    ),
                    HomePromoBanner(
                        imageRes = R.drawable.auto_roulette_banner,
                        onPlayNow = { requireLoginOr { onNavigate("roulette") } }
                    ),
                    HomePromoBanner(
                        imageRes = R.drawable.referral_banner,
                        onPlayNow = { requireLoginOr { onNavigate("affiliate") } }
                    ),
                    HomePromoBanner(
                        imageRes = R.drawable.vortex_banner,
                        onPlayNow = { requireLoginOr { onNavigate("vortex") } }
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroCategoryChip(
                    label = "GA",
                    icon = null,
                    imageRes = R.drawable.gundu_ata_logo_gold,
                    selected = selectedCategory == "gundu"
                ) {
                    selectedCategory = "gundu"
                    requireLoginOr { showGunduAtaChoiceDialog = true }
                }
                HeroCategoryChip(
                    label = "CRICKET",
                    icon = Icons.Default.SportsCricket,
                    selected = selectedCategory == "cricket"
                ) {
                    selectedCategory = "cricket"
                    requireLoginOr { onNavigate("sports?sport=cricket") }
                }
                HeroCategoryChip(
                    label = "Soccer",
                    icon = Icons.Default.SportsSoccer,
                    selected = selectedCategory == "soccer"
                ) {
                    selectedCategory = "soccer"
                    requireLoginOr { onNavigate("sports?sport=soccer") }
                }
                HeroCategoryChip(
                    label = "Tennis",
                    icon = Icons.Default.SportsTennis,
                    selected = selectedCategory == "tennis"
                ) {
                    selectedCategory = "tennis"
                    requireLoginOr { onNavigate("sports?sport=tennis") }
                }
                HeroCategoryChip(
                    label = "COLOUR",
                    icon = Icons.Default.Palette,
                    selected = selectedCategory == "colour"
                ) {
                    selectedCategory = "colour"
                    requireLoginOr { onNavigate("colour_game") }
                }
                HeroCategoryChip(
                    label = "Auto Roulette",
                    icon = Icons.Default.Album,
                    selected = selectedCategory == "roulette"
                ) {
                    selectedCategory = "roulette"
                    requireLoginOr { onNavigate("roulette") }
                }
                HeroCategoryChip(
                    label = "Stock Market",
                    icon = Icons.Default.ShowChart,
                    selected = selectedCategory == "trading"
                ) {
                    selectedCategory = "trading"
                    requireLoginOr { onNavigate("trading") }
                }
                HeroCategoryChip(
                    label = "Chicken Road",
                    icon = Icons.Default.Pets,
                    selected = selectedCategory == "chicken_road"
                ) {
                    selectedCategory = "chicken_road"
                    requireLoginOr { onNavigate("chicken_road") }
                }
                HeroCategoryChip(
                    label = "Chicken Road 2",
                    icon = Icons.Default.Egg,
                    selected = selectedCategory == "chicken_road_2"
                ) {
                    selectedCategory = "chicken_road_2"
                    requireLoginOr { onNavigate("chicken_road_2") }
                }
                HeroCategoryChip(
                    label = "Vortex",
                    icon = Icons.Default.BlurOn,
                    selected = selectedCategory == "vortex"
                ) {
                    selectedCategory = "vortex"
                    requireLoginOr { onNavigate("vortex") }
                }
                HeroCategoryChip(
                    label = "Chit Pat",
                    icon = Icons.Default.MonetizationOn,
                    selected = selectedCategory == "coin"
                ) {
                    selectedCategory = "coin"
                    requireLoginOr { onNavigate("coin") }
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
                        "PRIDE",
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
            painter = painterResource(id = R.drawable.gundu_ata_logo_gold),
            contentDescription = "Pride",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .padding(end = 8.dp)
        )
        if (isLoggedIn) {
            Surface(color = SurfaceColor, shape = RoundedCornerShape(24.dp)) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(MoneyFormat.formatRupee(balance), color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
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
    icon: ImageVector? = null,
    imageRes: Int? = null,
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
            if (imageRes != null) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp)
                )
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) Color.Black else PrimaryYellow,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) PrimaryYellow else TextGrey,
            fontSize = if (label.length > 6) 8.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun HeroBottomBar(
    selectedTab: DualNavTab = DualNavTab.HOME,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onCasino: () -> Unit,
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
                .height(78.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroNavItem("HOME", Icons.Default.Home, selectedTab == DualNavTab.HOME, onHome)
            HeroNavItem("LIVE", Icons.Default.Bolt, selectedTab == DualNavTab.LIVE, onLive)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = (-8).dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PrimaryYellow)
                        .clickable(onClick = onCasino),
                    contentAlignment = Alignment.Center
                ) {
                    CasinoChipIcon(
                        selected = true,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "CASINO",
                    color = if (selectedTab == DualNavTab.CASINO) PrimaryYellow else TextGrey,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            HeroNavItem("WALLET", Icons.Default.AccountBalanceWallet, selectedTab == DualNavTab.WALLET, onWallet)
            HeroNavItem("PROFILE", Icons.Default.Person, selectedTab == DualNavTab.PROFILE, onProfile)
        }
    }
}

@Composable
private fun HeroNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) PrimaryYellow else TextGrey, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, color = if (selected) PrimaryYellow else TextGrey, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
