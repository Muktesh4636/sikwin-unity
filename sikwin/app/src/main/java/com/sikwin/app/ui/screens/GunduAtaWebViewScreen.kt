package com.sikwin.app.ui.screens

import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.sikwin.app.utils.GunduAtaPrefetcher
import com.sikwin.app.utils.NetworkUtils
import kotlinx.coroutines.delay

private val GunduWebBg = Color(0xFF0A0A0A)

/**
 * Gundu Ata virtual (web) — loads when opened (no background prefetch).
 */
@Composable
fun GunduAtaWebViewScreen(
    accessToken: String?,
    refreshToken: String?,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val context = LocalContext.current

    fun closeScreen() {
        GunduAtaPrefetcher.prepareLeave()
        onBack()
    }

    BackHandler(onBack = {
        GunduAtaPrefetcher.handleBack { closeScreen() }
    })

    LaunchedEffect(accessToken, refreshToken) {
        // Load only when user opens Virtual — no background prefetch
        GunduAtaPrefetcher.warm(context, accessToken, refreshToken)
        CasinoPrefetcher.prefetchWhilePlaying(context, accessToken)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GunduWebBg)
            .statusBarsPadding()
    ) {
        when {
            accessToken.isNullOrBlank() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Login required",
                        color = TextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Gundu Ata uses your real wallet.\nPlease sign in to continue.",
                        color = TextGrey,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            closeScreen()
                            onRequireLogin()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
                    ) {
                        Text("Login", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { closeScreen() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
                    ) {
                        Text("Back", color = TextWhite, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            else -> {
                GunduAtaPreloadedWebView(
                    accessToken = accessToken,
                    refreshToken = refreshToken.orEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun GunduAtaPreloadedWebView(
    accessToken: String,
    refreshToken: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLoading by remember {
        mutableStateOf(!GunduAtaPrefetcher.isReady(accessToken) && !GunduAtaPrefetcher.hasNetworkError())
    }
    var networkError by remember { mutableStateOf(GunduAtaPrefetcher.hasNetworkError()) }

    LaunchedEffect(accessToken, refreshToken) {
        GunduAtaPrefetcher.warm(context, accessToken, refreshToken)
    }

    DisposableEffect(accessToken) {
        val readyListener: (Boolean) -> Unit = { ready ->
            if (ready || GunduAtaPrefetcher.isReady(accessToken)) showLoading = false
        }
        val errorListener: (Boolean) -> Unit = { err ->
            networkError = err
            if (err) showLoading = false
        }
        GunduAtaPrefetcher.addReadyListener(readyListener)
        GunduAtaPrefetcher.addNetworkErrorListener(errorListener)
        if (GunduAtaPrefetcher.isReady(accessToken)) showLoading = false
        networkError = GunduAtaPrefetcher.hasNetworkError()
        onDispose {
            GunduAtaPrefetcher.removeReadyListener(readyListener)
            GunduAtaPrefetcher.removeNetworkErrorListener(errorListener)
            GunduAtaPrefetcher.detach()
        }
    }

    LaunchedEffect(accessToken, networkError) {
        if (networkError) return@LaunchedEffect
        if (!NetworkUtils.isOnline(context)) {
            networkError = true
            showLoading = false
            return@LaunchedEffect
        }
        val ok = NetworkUtils.awaitReadyOrOffline(
            context = context,
            isReady = { GunduAtaPrefetcher.isReady(accessToken) },
            hasError = { GunduAtaPrefetcher.hasNetworkError() }
        )
        if (!ok) {
            networkError = true
        }
        showLoading = false
    }

    Box(
        modifier = modifier.background(GunduWebBg),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).also { frame ->
                    val already = GunduAtaPrefetcher.attach(
                        parent = frame,
                        accessToken = accessToken,
                        refreshToken = refreshToken
                    )
                    if (already || GunduAtaPrefetcher.isReady(accessToken)) {
                        showLoading = false
                    }
                    networkError = GunduAtaPrefetcher.hasNetworkError()
                }
            },
            update = { frame ->
                if (frame.childCount == 0) {
                    GunduAtaPrefetcher.attach(
                        parent = frame,
                        accessToken = accessToken,
                        refreshToken = refreshToken
                    )
                }
                if (GunduAtaPrefetcher.isReady(accessToken)) showLoading = false
                networkError = GunduAtaPrefetcher.hasNetworkError()
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
                Text("Loading Gundu Ata…", color = TextGrey, fontSize = 14.sp)
            }
        }

        if (networkError) {
            NoInternetConnectionOverlay(
                onRetry = {
                    showLoading = true
                    GunduAtaPrefetcher.retry(accessToken, refreshToken)
                },
                background = GunduWebBg
            )
        }
    }
}
