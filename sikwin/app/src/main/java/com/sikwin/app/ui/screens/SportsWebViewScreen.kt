package com.sikwin.app.ui.screens

import android.app.Activity
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sikwin.app.R
import com.sikwin.app.data.prefs.ThemePreferences
import com.sikwin.app.ui.theme.GoldOnWhite
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.ui.theme.TextWhite
import com.sikwin.app.ui.theme.rememberAppScreenColors
import com.sikwin.app.utils.CasinoPrefetcher
import com.sikwin.app.utils.NetworkUtils
import com.sikwin.app.utils.SportsPrefetcher
import kotlinx.coroutines.delay

private val SportsBg = Color(0xFF0A0A0A)
private val SportsNavWhiteBg = Color(0xFFFFFFFF)
private val SportsNavWhiteMuted = Color(0xFF6B7280)
private val SportsNavWhiteBorder = Color(0xFFE5E7EB)

private enum class SportsNavTab { HOME, LIVE, UPCOMING, CASINO, MY_BETS }

/**
 * Sports hub — WebView like casino.
 * LIVE lobby → [Constants.SPORTS_URL], Cricket → [Constants.CRICKET_URL],
 * auth via ?token=&refresh= + localStorage inject.
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
    val activity = context as? Activity
    val themePrefs = remember { ThemePreferences(context) }
    val entryTheme = remember { themePrefs.getAppTheme() }
    var selectedTab by remember { mutableStateOf(SportsNavTab.LIVE) }
    var leaving by remember { mutableStateOf(false) }
    // Live theme from prefs OR in-page sports toggle (updates bottom bar immediately).
    var isWhiteNav by remember { mutableStateOf(themePrefs.isWhiteTheme()) }
    val screenBg = if (isWhiteNav) SportsNavWhiteBg else SportsBg

    fun closeScreen() {
        if (leaving) return
        leaving = true
        SportsPrefetcher.prepareLeave()
        onBack()
        // Home / other screens read theme at composition — recreate if LIVE toggle changed it.
        if (themePrefs.getAppTheme() != entryTheme) {
            activity?.recreate()
        }
    }

    BackHandler(enabled = !leaving, onBack = {
        SportsPrefetcher.handleBack {
            selectedTab = SportsNavTab.HOME
            closeScreen()
        }
    })

    DisposableEffect(Unit) {
        val themeListener: (String) -> Unit = { mode ->
            isWhiteNav = mode == "light"
        }
        SportsPrefetcher.addThemeListener(themeListener)
        onDispose {
            SportsPrefetcher.removeThemeListener(themeListener)
        }
    }

    LaunchedEffect(accessToken, refreshToken, sport) {
        // Casino lobby shares gunduata.tech origin — if it keeps running, gundu-auth.js
        // can infinite-loop on storage/kokoroko-auth and freeze sports feed fetches.
        CasinoPrefetcher.prepareLeave()
        CasinoPrefetcher.haltForOtherWebGame()
        SportsPrefetcher.warm(context, accessToken, refreshToken, sport, mode = "live")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .statusBarsPadding()
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SportsPreloadedWebView(
                accessToken = accessToken,
                refreshToken = refreshToken.orEmpty(),
                sport = sport,
                mode = when (selectedTab) {
                    SportsNavTab.UPCOMING -> "upcoming"
                    SportsNavTab.MY_BETS -> "bets"
                    else -> "live"
                },
                modifier = Modifier.fillMaxSize(),
                onRequireLogin = {
                    closeScreen()
                    onRequireLogin()
                }
            )
        }

        SportsBottomBar(
            selectedTab = selectedTab,
            isWhite = isWhiteNav,
            onHome = { closeScreen() },
            onLive = {
                // Already on LIVE — ignore re-taps (repeated navigateMode caused feed/loader glitches).
                if (selectedTab == SportsNavTab.LIVE) return@SportsBottomBar
                selectedTab = SportsNavTab.LIVE
                if (!accessToken.isNullOrBlank()) {
                    SportsPrefetcher.navigateMode(accessToken, refreshToken, sport, "live")
                }
            },
            onUpcoming = {
                if (selectedTab == SportsNavTab.UPCOMING) return@SportsBottomBar
                selectedTab = SportsNavTab.UPCOMING
                if (!accessToken.isNullOrBlank()) {
                    SportsPrefetcher.navigateMode(accessToken, refreshToken, sport, "upcoming")
                }
            },
            onCasino = {
                SportsPrefetcher.prepareLeave()
                onCasino()
                if (themePrefs.getAppTheme() != entryTheme) {
                    activity?.recreate()
                }
            },
            onMyBets = {
                if (selectedTab == SportsNavTab.MY_BETS) return@SportsBottomBar
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
    isWhite: Boolean,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onUpcoming: () -> Unit,
    onCasino: () -> Unit,
    onMyBets: () -> Unit
) {
    val barBg = if (isWhite) SportsNavWhiteBg else Color(0xFF0A0A0A)
    val selectedColor = if (isWhite) GoldOnWhite else DualGoldMid
    val idleColor = if (isWhite) SportsNavWhiteMuted else DualGoldDeep.copy(alpha = 0.7f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBg)
            .then(
                if (isWhite) Modifier.border(width = 1.dp, color = SportsNavWhiteBorder)
                else Modifier
            )
            .navigationBarsPadding()
            .height(78.dp)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SportsNavItem("HOME", Icons.Default.Home, selectedTab == SportsNavTab.HOME, selectedColor, idleColor, onHome)
        SportsNavItem("LIVE", Icons.Default.Bolt, selectedTab == SportsNavTab.LIVE, selectedColor, idleColor, onLive)
        SportsNavItem("UPCOMING", Icons.Default.Schedule, selectedTab == SportsNavTab.UPCOMING, selectedColor, idleColor, onUpcoming)
        DualCasinoNavItem(selected = selectedTab == SportsNavTab.CASINO, onClick = onCasino)
        SportsNavItem(
            "MY BETS",
            Icons.AutoMirrored.Filled.ReceiptLong,
            selectedTab == SportsNavTab.MY_BETS,
            selectedColor,
            idleColor,
            onMyBets
        )
    }
}

@Composable
private fun SportsNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    idleColor: Color,
    onClick: () -> Unit
) {
    val tint = if (selected) selectedColor else idleColor
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            color = tint,
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
                "Sports uses your real Pride wallet.\nPlease sign in to continue.",
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
    accessToken: String?,
    refreshToken: String,
    sport: String?,
    mode: String,
    modifier: Modifier = Modifier,
    onRequireLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    if (mode == "bets" && accessToken.isNullOrBlank()) {
        SportsLoginRequired(onLogin = onRequireLogin, onBack = onRequireLogin)
        return
    }
    // Boot overlay until hub loads in background → bar hits 100% → reveal page.
    var showLoading by remember {
        mutableStateOf(!SportsPrefetcher.isReady(accessToken, sport) && !SportsPrefetcher.hasNetworkError())
    }
    var networkError by remember { mutableStateOf(SportsPrefetcher.hasNetworkError()) }
    var progress by remember {
        mutableFloatStateOf((SportsPrefetcher.getLoadProgress() / 100f).coerceAtLeast(0.02f))
    }
    var statusText by remember { mutableStateOf("Loading live matches…") }
    var pageReadyFlag by remember { mutableStateOf(SportsPrefetcher.isReady(accessToken, sport)) }

    DisposableEffect(accessToken, sport) {
        val listener: (Boolean) -> Unit = { pageReady ->
            val ok = pageReady || SportsPrefetcher.isReady(accessToken, sport)
            pageReadyFlag = ok
            // Reload blanked the WebView — bring the loader back so we don't sit on black.
            if (!ok && !SportsPrefetcher.hasNetworkError()) {
                showLoading = true
            }
        }
        val errorListener: (Boolean) -> Unit = { err ->
            networkError = err
            if (err) {
                showLoading = false
                statusText = "No internet connection"
            }
        }
        val progressListener: (Int) -> Unit = { p ->
            if (!networkError && showLoading) {
                progress = (p / 100f).coerceIn(0.02f, 0.98f)
                statusText = when {
                    p < 25 -> "Connecting…"
                    p < 55 -> "Loading live matches…"
                    p < 85 -> "Fetching odds…"
                    else -> "Almost ready…"
                }
            }
        }
        SportsPrefetcher.addReadyListener(listener)
        SportsPrefetcher.addNetworkErrorListener(errorListener)
        SportsPrefetcher.addProgressListener(progressListener)
        if (SportsPrefetcher.isReady(accessToken, sport)) {
            pageReadyFlag = true
            showLoading = false
            SportsPrefetcher.revealAfterBoot()
        } else if (!SportsPrefetcher.hasNetworkError()) {
            showLoading = true
            pageReadyFlag = false
        }
        networkError = SportsPrefetcher.hasNetworkError()
        onDispose {
            SportsPrefetcher.removeReadyListener(listener)
            SportsPrefetcher.removeNetworkErrorListener(errorListener)
            SportsPrefetcher.removeProgressListener(progressListener)
            SportsPrefetcher.detach()
        }
    }

    // When background load succeeds → 100% (if overlay up) → always reveal (never leave alpha 0).
    LaunchedEffect(pageReadyFlag, networkError) {
        if (networkError || !pageReadyFlag) return@LaunchedEffect
        if (showLoading) {
            progress = 1f
            statusText = "Ready"
            delay(180)
            showLoading = false
        }
        SportsPrefetcher.revealAfterBoot()
        SportsPrefetcher.forceVisibleIfAttached()
    }

    // Hub URL is enough to finish the bar — don't wait forever for feed JS.
    LaunchedEffect(showLoading, networkError) {
        if (!showLoading || networkError) return@LaunchedEffect
        var waited = 0
        while (showLoading && !networkError && waited < 20) {
            delay(400)
            waited++
            if (SportsPrefetcher.hasHubUrl() || SportsPrefetcher.isReady(accessToken, sport)) {
                pageReadyFlag = true
                if (progress < 0.95f) progress = 0.95f
                break
            }
        }
    }

    // Hard failsafe: never leave the loader stuck mid-bar.
    LaunchedEffect(showLoading, accessToken, sport) {
        if (!showLoading) return@LaunchedEffect
        // Reveal as soon as hub URL is up (don't wait 10s).
        repeat(25) {
            delay(200)
            if (!showLoading || networkError) return@LaunchedEffect
            if (SportsPrefetcher.hasHubUrl() || SportsPrefetcher.isReady(accessToken, sport)) {
                pageReadyFlag = true
                progress = 1f
                statusText = "Ready"
                delay(100)
                showLoading = false
                SportsPrefetcher.revealAfterBoot()
                SportsPrefetcher.forceVisibleIfAttached()
                return@LaunchedEffect
            }
        }
        if (!showLoading || networkError) return@LaunchedEffect
        SportsPrefetcher.retry(accessToken, refreshToken, sport, mode)
        delay(5_000)
        if (!showLoading || networkError) return@LaunchedEffect
        if (SportsPrefetcher.hasHubUrl() || SportsPrefetcher.isReady(accessToken, sport)) {
            pageReadyFlag = true
            progress = 1f
            showLoading = false
            SportsPrefetcher.revealAfterBoot()
            SportsPrefetcher.forceVisibleIfAttached()
        } else {
            // Still show the page if anything painted — avoid infinite stuck loader.
            showLoading = false
            SportsPrefetcher.forceVisibleIfAttached()
            if (!SportsPrefetcher.hasHubUrl()) {
                networkError = true
            }
        }
    }

    // Watchdog: keep WebView opaque whenever hub is up (kills intermittent black screens).
    LaunchedEffect(accessToken, sport) {
        while (true) {
            delay(700)
            if (networkError) continue
            SportsPrefetcher.forceVisibleIfAttached()
            if (SportsPrefetcher.hasHubUrl() || SportsPrefetcher.isReady(accessToken, sport)) {
                if (showLoading) {
                    pageReadyFlag = true
                } else {
                    SportsPrefetcher.revealAfterBoot()
                }
                if (SportsPrefetcher.isReady(accessToken, sport)) {
                    pageReadyFlag = true
                }
            }
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
            isReady = { SportsPrefetcher.isReady(accessToken, sport) || SportsPrefetcher.hasHubUrl() },
            hasError = { SportsPrefetcher.hasNetworkError() }
        )
        // Slow match feed is not offline — only show offline when load truly failed.
        if (!ok && !SportsPrefetcher.hasHubUrl()) {
            networkError = true
            showLoading = false
        } else if (SportsPrefetcher.isReady(accessToken, sport) || SportsPrefetcher.hasHubUrl()) {
            pageReadyFlag = true
            SportsPrefetcher.revealAfterBoot()
        }
    }

    // Soft creep while waiting so the bar never looks frozen; accelerate once hub URL is up.
    LaunchedEffect(showLoading, networkError, pageReadyFlag) {
        while (showLoading && !networkError && !pageReadyFlag) {
            val hubUp = SportsPrefetcher.hasHubUrl()
            if (hubUp) {
                progress = (progress + 0.04f).coerceAtMost(0.98f)
            } else if (progress < 0.92f) {
                progress = (progress + 0.012f).coerceAtMost(0.92f)
            }
            delay(70)
        }
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
                        pageReadyFlag = true
                        showLoading = false
                        SportsPrefetcher.revealAfterBoot()
                    } else if (!SportsPrefetcher.hasNetworkError()) {
                        showLoading = true
                        pageReadyFlag = false
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
                if (SportsPrefetcher.isReady(accessToken, sport) || SportsPrefetcher.hasHubUrl()) {
                    pageReadyFlag = true
                    if (!showLoading) {
                        SportsPrefetcher.revealAfterBoot()
                    }
                } else if (!SportsPrefetcher.hasNetworkError() && !showLoading && !SportsPrefetcher.hasHubUrl()) {
                    // True cold reload only — don't flash loader on LIVE re-taps / soft mode switch.
                    showLoading = true
                }
                networkError = SportsPrefetcher.hasNetworkError()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showLoading && !networkError) {
            SportsCricketLoadingOverlay(progress = progress, status = statusText)
        }

        if (networkError) {
            NoInternetConnectionOverlay(
                onRetry = {
                    showLoading = true
                    pageReadyFlag = false
                    progress = 0.02f
                    statusText = "Connecting…"
                    SportsPrefetcher.retry(accessToken, refreshToken, sport, mode)
                },
                background = SportsBg
            )
        }
    }
}

/** Concept B — bat/ball hero + live % bar until sports page boots. */
@Composable
private fun SportsCricketLoadingOverlay(progress: Float, status: String) {
    val p = progress.coerceIn(0f, 1f)
    val pct = (p * 100).toInt().coerceIn(0, 100)
    val barShape = RoundedCornerShape(50)
    val colors = rememberAppScreenColors()
    val accent = if (colors.isWhite) colors.accent else PrimaryYellow

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.cricket_loading),
                contentDescription = "Loading Cricket",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                status,
                color = colors.textMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(barShape)
                    .border(1.5.dp, accent.copy(alpha = 0.85f), barShape)
                    .background(if (colors.isWhite) Color(0xFFE5E7EB) else Color(0xFF1A1A1A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(p.coerceAtLeast(0.02f))
                        .clip(barShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFE65100),
                                    Color(0xFFFF9800),
                                    Color(0xFFFFD54F)
                                )
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "$pct%",
                color = accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
