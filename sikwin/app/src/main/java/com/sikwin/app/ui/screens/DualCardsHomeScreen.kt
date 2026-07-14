package com.sikwin.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sikwin.app.R
import com.sikwin.app.ui.theme.BlackBackground
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.SurfaceColor
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel

/**
 * Exact Dual Cards theme — shows the design mock image unchanged (banners included)
 * with invisible tap zones for navigation.
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
            titleContentColor = Color.White,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight

            // Exact mock — banners and artwork unchanged
            Image(
                painter = painterResource(id = R.drawable.theme_dual_cards_preview),
                contentDescription = "Dual Cards theme",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            // Menu
            Hotspot(
                modifier = Modifier
                    .size(w * 0.14f, h * 0.07f)
                    .offset(x = w * 0.02f, y = h * 0.01f),
                onClick = { onNavigate("me") }
            )

            // Wallet / deposit
            Hotspot(
                modifier = Modifier
                    .size(w * 0.40f, h * 0.07f)
                    .offset(x = w * 0.58f, y = h * 0.01f),
                onClick = { requireLoginOr { onNavigate("deposit") } }
            )

            // Hero LIVE CASINO banner + PLAY NOW
            Hotspot(
                modifier = Modifier
                    .size(w * 0.94f, h * 0.30f)
                    .offset(x = w * 0.03f, y = h * 0.085f),
                onClick = { requireLoginOr { showGunduAtaChoiceDialog = true } }
            )

            // Categories row (HOT, CRICKET, SLOTS, LIVE, MORE)
            val catY = h * 0.42f
            val catSize = w * 0.16f
            val catGap = w * 0.035f
            val catStart = w * 0.04f
            Hotspot(
                modifier = Modifier.size(catSize).offset(x = catStart, y = catY),
                onClick = { requireLoginOr { showGunduAtaChoiceDialog = true } }
            )
            Hotspot(
                modifier = Modifier.size(catSize).offset(x = catStart + (catSize + catGap), y = catY),
                onClick = { requireLoginOr { onNavigate("ipl") } }
            )
            Hotspot(
                modifier = Modifier.size(catSize).offset(x = catStart + (catSize + catGap) * 2, y = catY),
                onClick = { requireLoginOr { onNavigate("colour_game") } }
            )
            Hotspot(
                modifier = Modifier.size(catSize).offset(x = catStart + (catSize + catGap) * 3, y = catY),
                onClick = { requireLoginOr { onNavigate("gundu_ata_live") } }
            )
            Hotspot(
                modifier = Modifier.size(catSize).offset(x = catStart + (catSize + catGap) * 4, y = catY),
                onClick = { onNavigate("me") }
            )

            // Left dual card — Gundu Ata
            Hotspot(
                modifier = Modifier
                    .size(w * 0.44f, h * 0.28f)
                    .offset(x = w * 0.04f, y = h * 0.55f),
                onClick = { requireLoginOr { showGunduAtaChoiceDialog = true } }
            )

            // Right dual card — Andar Bahar artwork → colour game
            Hotspot(
                modifier = Modifier
                    .size(w * 0.44f, h * 0.28f)
                    .offset(x = w * 0.52f, y = h * 0.55f),
                onClick = { requireLoginOr { onNavigate("colour_game") } }
            )

            // Bottom nav
            val navY = h * 0.90f
            val navH = h * 0.10f
            val navW = w / 5
            Hotspot(modifier = Modifier.size(navW, navH).offset(x = 0.dp, y = navY), onClick = { })
            Hotspot(modifier = Modifier.size(navW, navH).offset(x = navW, y = navY), onClick = { onNavigate("affiliate") })
            Hotspot(modifier = Modifier.size(navW, navH).offset(x = navW * 2, y = navY), onClick = { onNavigate("leaderboard") })
            Hotspot(modifier = Modifier.size(navW, navH).offset(x = navW * 3, y = navY), onClick = { requireLoginOr { onNavigate("wallet") } })
            Hotspot(modifier = Modifier.size(navW, navH).offset(x = navW * 4, y = navY), onClick = { onNavigate("me") })
        }
    }
}

@Composable
private fun Hotspot(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
}
