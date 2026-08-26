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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.BackHandler
import com.sikwin.app.R
import com.sikwin.app.ui.theme.*
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel

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
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLoginPopup by remember { mutableStateOf(false) }
    var showGunduAtaChoiceDialog by remember { mutableStateOf(false) }
    var showSideMenu by remember { mutableStateOf(false) }
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

    BackHandler(enabled = showSideMenu) {
        showSideMenu = false
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

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = DualScreenBlack,
        bottomBar = {
            DualCardsBottomBar(
                selectedTab = DualNavTab.HOME,
                onHome = { },
                onLive = { requireLoginOr { onNavigate("sports") } },
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
            DualCardsTopBar(
                balance = viewModel.wallet?.balance ?: "0.00",
                isLoggedIn = viewModel.loginSuccess,
                onLeadingClick = { showSideMenu = true },
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

            Spacer(modifier = Modifier.height(18.dp))

            // Circular category icons — horizontally scrollable as games grow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DualCategoryCircle(
                    label = "GA",
                    icon = null,
                    imageRes = R.drawable.gundu_ata_logo_gold
                ) {
                    requireLoginOr { showGunduAtaChoiceDialog = true }
                }
                DualCategoryCircle("CRICKET", Icons.Default.SportsCricket) {
                    requireLoginOr { onNavigate("sports?sport=cricket") }
                }
                DualCategoryCircle("Soccer", Icons.Default.SportsSoccer) {
                    requireLoginOr { onNavigate("sports?sport=soccer") }
                }
                DualCategoryCircle("Tennis", Icons.Default.SportsTennis) {
                    requireLoginOr { onNavigate("sports?sport=tennis") }
                }
                DualCategoryCircle("Rangu", Icons.Default.Casino) {
                    requireLoginOr { onNavigate("colour_game") }
                }
                DualCategoryCircle("Auto Roulette", Icons.Default.Album) {
                    requireLoginOr { onNavigate("roulette") }
                }
                DualCategoryCircle("Stock Market", Icons.Default.ShowChart) {
                    requireLoginOr { onNavigate("trading") }
                }
                DualCategoryCircle("Chicken Road", Icons.Default.Pets) {
                    requireLoginOr { onNavigate("chicken_road") }
                }
                DualCategoryCircle("Chicken Road 2", Icons.Default.Egg) {
                    requireLoginOr { onNavigate("chicken_road_2") }
                }
                DualCategoryCircle("Vortex", Icons.Default.BlurOn) {
                    requireLoginOr { onNavigate("vortex") }
                }
                DualCategoryCircle("Chit Pat", Icons.Default.MonetizationOn) {
                    requireLoginOr { onNavigate("coin") }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Left: Gundu Ata video | Right: Cock Fight + Roulette stacked images
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left → Gundu Ata Live (unchanged)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DualCardDark)
                        .border(1.dp, DualGoldDeep.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { requireLoginOr { onNavigate("gundu_ata_web") } }
                ) {
                    VideoPlayer(
                        videoResId = R.raw.gundu_ata_video,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Right → Cock Fight video (top) + Roulette image (bottom)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DualHomeGameVideoCard(
                        videoResId = R.raw.cock_fight,
                        label = "COCK FIGHT",
                        modifier = Modifier.weight(1f),
                        onClick = { requireLoginOr { onNavigate("cock_fight") } }
                    )
                    DualHomeGameImageCard(
                        imageRes = R.drawable.card_roulette_home,
                        label = "ROULETTE",
                        modifier = Modifier.weight(1f),
                        onClick = { requireLoginOr { onNavigate("roulette") } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    SideMenuOverlay(
        open = showSideMenu,
        onClose = { showSideMenu = false },
        onNavigate = onNavigate,
        onPlayGunduAta = { showGunduAtaChoiceDialog = true },
        requireLoginOr = { action -> requireLoginOr(action) }
    )
    }
}

@Composable
private fun DualHomeGameVideoCard(
    videoResId: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DualCardDark)
            .border(1.dp, DualGoldDeep.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        VideoPlayer(
            videoResId = videoResId,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                color = DualGoldMid,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun DualHomeGameImageCard(
    imageRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DualCardDark)
            .border(1.dp, DualGoldDeep.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                color = DualGoldMid,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun DualCategoryCircle(
    label: String,
    icon: ImageVector? = null,
    imageRes: Int? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF121212))
                .border(1.5.dp, DualGoldMid, CircleShape),
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
                Icon(icon, contentDescription = label, tint = DualGoldMid, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            color = DualGoldMid,
            fontSize = if (label.length > 6) 8.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
