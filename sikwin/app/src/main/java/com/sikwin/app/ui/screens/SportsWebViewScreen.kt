package com.sikwin.app.ui.screens

import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.ui.theme.TextWhite
import com.sikwin.app.utils.CasinoPrefetcher
import com.sikwin.app.utils.NetworkUtils
import com.sikwin.app.utils.SportsPrefetcher
import kotlinx.coroutines.delay

private val SportsBg = Color(0xFF0A0A0A)

private enum class SportsNavTab { HOME, LIVE, UPCOMING, CASINO, MY_BETS }

/**
 * Sports hub — loads WebView when opened (no background prefetch).
 * Bottom bar: Home | Live | Upcoming | Casino | My Bets
 */
@Composable
fun SportsWebViewScreen(
    accessToken: String?,
    refreshToken: String?,
    sport: String? = null,
    onBack: () -> Unit,
    onCasino: () -> Unit = {},
    onRequireLogin: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(SportsNavTab.LIVE) }
    var leaving by remember { mutableStateOf(false) }

    fun closeScreen() {
        if (leaving) return
        leaving = true
        SportsPrefetcher.prepareLeave()
        onBack()
    }

    BackHandler(enabled = !leaving, onBack = {
        SportsPrefetcher.handleBack {
            selectedTab = SportsNavTab.HOME
            closeScreen()
        }
    })

    LaunchedEffect(accessToken, refreshToken, sport) {
        // Load only when user opens Sports
        SportsPrefetcher.warm(context, accessToken, refreshToken, sport, mode = "live")
        CasinoPrefetcher.prefetchWhilePlaying(context, accessToken)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SportsBg)
            .statusBarsPadding()
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                accessToken.isNullOrBlank() -> {
                    SportsLoginRequired(
                        onLogin = {
                            closeScreen()
                            onRequireLogin()
                        },
                        onBack = { closeScreen() }
                    )
                }
                else -> {
                    SportsPreloadedWebView(
                        accessToken = accessToken,
                        refreshToken = refreshToken.orEmpty(),
                        sport = sport,
                        mode = when (selectedTab) {
                            SportsNavTab.UPCOMING -> "upcoming"
                            SportsNavTab.MY_BETS -> "bets"
                            else -> "live"
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        SportsBottomBar(
            selectedTab = selectedTab,
            onHome = { closeScreen() },
            onLive = {
                selectedTab = SportsNavTab.LIVE
                if (!accessToken.isNullOrBlank()) {
                    SportsPrefetcher.navigateMode(accessToken, refreshToken, sport, "live")
                }
            },
            onUpcoming = {
                selectedTab = SportsNavTab.UPCOMING
                if (!accessToken.isNullOrBlank()) {
                    SportsPrefetcher.navigateMode(accessToken, refreshToken, sport, "upcoming")
                }
            },
            onCasino = {
                SportsPrefetcher.prepareLeave()
                onCasino()
            },
            onMyBets = {
                selectedTab = SportsNavTab.MY_BETS
                if (!accessToken.isNullOrBlank()) {
                    SportsPrefetcher.navigateMode(accessToken, refreshToken, sport, "bets")
                }
            }
        )
    }
}

@Composable
private fun SportsBottomBar(
    selectedTab: SportsNavTab,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onUpcoming: () -> Unit,
    onCasino: () -> Unit,
    onMyBets: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .navigationBarsPadding()
            .height(78.dp)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SportsNavItem("HOME", Icons.Default.Home, selectedTab == SportsNavTab.HOME, onHome)
        SportsNavItem("LIVE", Icons.Default.Bolt, selectedTab == SportsNavTab.LIVE, onLive)
        SportsNavItem("UPCOMING", Icons.Default.Schedule, selectedTab == SportsNavTab.UPCOMING, onUpcoming)
        DualCasinoNavItem(selected = selectedTab == SportsNavTab.CASINO, onClick = onCasino)
        SportsNavItem("MY BETS", Icons.AutoMirrored.Filled.ReceiptLong, selectedTab == SportsNavTab.MY_BETS, onMyBets)
    }
}

@Composable
private fun SportsNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) DualGoldMid else DualGoldDeep.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) DualGoldMid else DualGoldDeep.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SportsLoginRequired(onLogin: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Login required",
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Sports uses your real Gundu wallet.\nPlease sign in to continue.",
                color = TextGrey,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
            ) {
                Text("Login", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
            ) {
                Text("Back", color = TextWhite, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SportsPreloadedWebView(
    accessToken: String,
    refreshToken: String,
    sport: String?,
    mode: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Once the hub has painted once, never re-show the overlay spinner on top of matches.
    var showLoading by remember {
        mutableStateOf(!SportsPrefetcher.isReady(accessToken, sport) && !SportsPrefetcher.hasNetworkError())
    }
    var networkError by remember { mutableStateOf(SportsPrefetcher.hasNetworkError()) }

    DisposableEffect(accessToken, sport) {
        val listener: (Boolean) -> Unit = { pageReady ->
            if (pageReady || SportsPrefetcher.isReady(accessToken, sport)) {
                showLoading = false
            }
        }
        val errorListener: (Boolean) -> Unit = { err ->
            networkError = err
            if (err) showLoading = false
        }
        SportsPrefetcher.addReadyListener(listener)
        SportsPrefetcher.addNetworkErrorListener(errorListener)
        if (SportsPrefetcher.isReady(accessToken, sport)) {
            showLoading = false
        }
        networkError = SportsPrefetcher.hasNetworkError()
        onDispose {
            SportsPrefetcher.removeReadyListener(listener)
            SportsPrefetcher.removeNetworkErrorListener(errorListener)
            SportsPrefetcher.detach()
        }
    }

    LaunchedEffect(accessToken, sport, networkError) {
        if (networkError) return@LaunchedEffect
        if (!NetworkUtils.isOnline(context)) {
            networkError = true
            showLoading = false
            return@LaunchedEffect
        }
        val ok = NetworkUtils.awaitReadyOrOffline(
            context = context,
            isReady = { SportsPrefetcher.isReady(accessToken, sport) },
            hasError = { SportsPrefetcher.hasNetworkError() }
        )
        if (!ok) {
            networkError = true
        }
        showLoading = false
    }

    Box(
        modifier = modifier.background(SportsBg),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).also { frame ->
                    val already = SportsPrefetcher.attach(
                        parent = frame,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        sport = sport,
                        mode = mode,
                        onBack = {}
                    )
                    if (already || SportsPrefetcher.isReady(accessToken, sport)) {
                        showLoading = false
                    }
                    networkError = SportsPrefetcher.hasNetworkError()
                }
            },
            update = { frame ->
                if (frame.childCount == 0) {
                    SportsPrefetcher.attach(
                        parent = frame,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        sport = sport,
                        mode = mode,
                        onBack = {}
                    )
                }
                if (SportsPrefetcher.isReady(accessToken, sport)) {
                    showLoading = false
                }
                networkError = SportsPrefetcher.hasNetworkError()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showLoading && !networkError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = PrimaryYellow,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("Loading Sports…", color = TextGrey, fontSize = 14.sp)
            }
        }

        if (networkError) {
            NoInternetConnectionOverlay(
                onRetry = {
                    showLoading = true
                    SportsPrefetcher.retry(accessToken, refreshToken, sport, mode)
                },
                background = SportsBg
            )
        }
    }
}
