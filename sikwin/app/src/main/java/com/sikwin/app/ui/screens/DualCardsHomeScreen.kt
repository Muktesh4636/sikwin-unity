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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    var showLoginPopup by remember { mutableStateOf(false) }
    var showGunduAtaChoiceDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
        containerColor = DualScreenBlack,
        bottomBar = {
            DualCardsBottomBar(
                selectedTab = DualNavTab.HOME,
                onHome = { },
                onPromo = { onNavigate("affiliate") },
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
                onLeadingClick = { onNavigate("me") },
                onDeposit = { requireLoginOr { onNavigate("deposit") } },
                onLogin = { onNavigate("login") }
            )

            SearchBar(onSearch = { searchQuery = it })

            // Gundu Ata LIVE banner — only PLAY NOW opens game mode picker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(LIVE_CASINO_BANNER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                LiveCasinoBannerWithPlayNow(
                    defaultResId = R.drawable.live_casino_banner,
                    modifier = Modifier.fillMaxSize(),
                    onPlayNowClick = { requireLoginOr { showGunduAtaChoiceDialog = true } }
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
                DualCategoryCircle(
                    label = "GA",
                    icon = null,
                    imageRes = R.drawable.gundu_ata_logo_gold
                ) {
                    requireLoginOr { showGunduAtaChoiceDialog = true }
                }
                DualCategoryCircle("CRICKET", Icons.Default.SportsCricket) {
                    requireLoginOr { onNavigate("ipl") }
                }
                DualCategoryCircle("Rangu", Icons.Default.Casino) {
                    requireLoginOr { onNavigate("colour_game") }
                }
                DualCategoryCircle("LIVE", Icons.Default.Videocam) {
                    requireLoginOr { onNavigate("gundu_ata_live") }
                }
                DualCategoryCircle("Chit Pat", Icons.Default.MonetizationOn) {
                    requireLoginOr { onNavigate("coin") }
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
                // First video → Gundu Ata Live
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DualCardDark)
                        .border(1.dp, DualGoldDeep.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { requireLoginOr { onNavigate("gundu_ata_live") } }
                ) {
                    VideoPlayer(
                        videoResId = R.raw.gundu_ata_video,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Second video → Unity virtual Gundu Ata
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0B3D2E))
                        .border(1.dp, DualGoldDeep.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { requireLoginOr { onGameClick("gundu_ata") } }
                ) {
                    VideoPlayer(
                        videoResId = R.raw.gundu_ata_video,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
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
