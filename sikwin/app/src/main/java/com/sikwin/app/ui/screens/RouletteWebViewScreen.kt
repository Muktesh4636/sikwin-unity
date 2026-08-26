package com.sikwin.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebResourceError
import com.sikwin.app.data.api.RetrofitClient
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.ui.theme.TextWhite
import com.sikwin.app.utils.Constants
import com.sikwin.app.utils.CasinoPrefetcher
import com.sikwin.app.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Same deep burgundy as the in-game roulette wheel / Three.js scene (`#0c0406`). */
private val RouletteBg = Color(0xFF0C0406)

/**
 * Roulette — real Gundu wallet only (no demo / guest).
 * Shows a ball + progress loader while prefetching assets / wallet, then reveals the WebView.
 */
@Composable
fun RouletteWebViewScreen(
    accessToken: String?,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(onBack = onBack)

    LaunchedEffect(accessToken) {
        CasinoPrefetcher.prefetchWhilePlaying(context, accessToken)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RouletteBg)
            .statusBarsPadding()
    ) {
        when {
            accessToken.isNullOrBlank() -> {
                LoginRequiredPanel(
                    onLogin = {
                        onBack()
                        onRequireLogin()
                    },
                    onBack = onBack
                )
            }
            else -> {
                RouletteWebView(
                    accessToken = accessToken,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LoginRequiredPanel(onLogin: () -> Unit, onBack: () -> Unit) {
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
                "Auto Roulette uses your real Gundu wallet.\nPlease sign in to continue.",
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RouletteWebView(
    accessToken: String,
    modifier: Modifier = Modifier
) {
    var pageReady by remember { mutableStateOf(false) }
    var gameReady by remember { mutableStateOf(false) }
    var prefetchDone by remember { mutableStateOf(false) }
    var networkError by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0.02f) }
    var statusText by remember { mutableStateOf("Preparing Auto Roulette…") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val injectJs = remember(accessToken) { buildTokenInjectJs(accessToken) }
    val startUrl = remember(accessToken) {
        Uri.parse(Constants.ROULETTE_URL).buildUpon()
            .appendQueryParameter("token", accessToken)
            .build()
            .toString()
    }

    fun markNetworkError() {
        networkError = true
        statusText = "No internet connection"
    }

    // Prefetch HTML/CSS/JS + Three.js + real wallet while loader is visible.
    LaunchedEffect(accessToken, retryToken) {
        networkError = false
        pageReady = false
        gameReady = false
        prefetchDone = false
        progress = 0.02f
        statusText = "Fetching game…"
        if (!NetworkUtils.isOnline(context)) {
            markNetworkError()
            return@LaunchedEffect
        }
        val ok = prefetchRouletteBackend { p, label ->
            if (!networkError) {
                progress = p.coerceIn(0.02f, 0.92f)
                statusText = label
            }
        }
        if (!ok) {
            markNetworkError()
            return@LaunchedEffect
        }
        prefetchDone = true
        statusText = "Opening table…"
        val start = System.currentTimeMillis()
        while (!pageReady || !gameReady) {
            if (networkError) return@LaunchedEffect
            val elapsed = System.currentTimeMillis() - start
            if (!NetworkUtils.isOnline(context) && elapsed >= NetworkUtils.OFFLINE_UI_MS) {
                markNetworkError()
                return@LaunchedEffect
            }
            if (elapsed > NetworkUtils.LOAD_TIMEOUT_MS) {
                markNetworkError()
                return@LaunchedEffect
            }
            if (progress < 0.96f) progress = (progress + 0.01f).coerceAtMost(0.96f)
            delay(80)
        }
        progress = 1f
        statusText = "Ready"
        delay(220)
    }

    val showLoader = !networkError && (!pageReady || !gameReady)

    Box(modifier = modifier) {
        key(retryToken) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(AndroidColor.parseColor("#0C0406"))
                        alpha = 0f

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.allowFileAccess = false
                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        val bridge = RouletteJsBridge(
                            onGameReady = {
                                scope.launch { gameReady = true }
                            }
                        )
                        addJavascriptInterface(bridge, "GunduRoulette")

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (newProgress in 1..99 && progress < 0.85f && !networkError) {
                                    progress = (0.35f + newProgress / 100f * 0.45f).coerceAtMost(0.9f)
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                pageReady = false
                                gameReady = false
                                view?.evaluateJavascript(injectJs, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (com.sikwin.app.utils.WebViewOffline.isChromeErrorUrl(url)) {
                                    com.sikwin.app.utils.WebViewOffline.hideChromeErrorPage(view)
                                    scope.launch { markNetworkError() }
                                    return
                                }
                                view?.evaluateJavascript(injectJs, null)
                                view?.evaluateJavascript(GAME_READY_POLL_JS, null)
                                pageReady = true
                                scope.launch {
                                    delay(2500)
                                    if (!gameReady && !networkError) gameReady = true
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (com.sikwin.app.utils.WebViewOffline.isMainFrameError(request)) {
                                    com.sikwin.app.utils.WebViewOffline.hideChromeErrorPage(view)
                                    scope.launch { markNetworkError() }
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                if (failingUrl != null &&
                                    (failingUrl.contains("/roulette") || failingUrl.contains("gunduata.tech"))
                                ) {
                                    com.sikwin.app.utils.WebViewOffline.hideChromeErrorPage(view)
                                    scope.launch { markNetworkError() }
                                }
                            }
                        }

                        loadUrl(startUrl)
                    }
                },
                update = { webView ->
                    webView.evaluateJavascript(injectJs, null)
                    webView.alpha = if (showLoader || networkError) 0f else 1f
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showLoader) {
            RouletteBallLoadingOverlay(
                progress = progress,
                status = statusText
            )
        }

        if (networkError) {
            NoInternetConnectionOverlay(
                onRetry = { retryToken += 1 },
                background = RouletteBg
            )
        }
    }
}

private class RouletteJsBridge(
    private val onGameReady: () -> Unit
) {
    private val fired = AtomicBoolean(false)

    @JavascriptInterface
    fun onReady() {
        if (fired.compareAndSet(false, true)) onGameReady()
    }
}

private const val GAME_READY_POLL_JS = """
(function(){
  function ready(){
    try { GunduRoulette.onReady(); } catch(e) {}
  }
  var tries = 0;
  function check(){
    tries++;
    var el = document.getElementById('loading-overlay');
    var hidden = !el || el.hidden || el.style.display === 'none' ||
      (window.getComputedStyle && getComputedStyle(el).display === 'none') ||
      (window.getComputedStyle && getComputedStyle(el).visibility === 'hidden') ||
      (el && el.classList && el.classList.contains('hidden'));
    var canvas = document.getElementById('wheel-canvas');
    if (hidden || (canvas && canvas.width > 0) || tries > 40) {
      ready();
      return;
    }
    setTimeout(check, 120);
  }
  if (document.readyState === 'complete') check();
  else window.addEventListener('load', check);
  setTimeout(ready, 8000);
})();
"""

@Composable
private fun RouletteBallLoadingOverlay(
    progress: Float,
    status: String
) {
    val spin = rememberInfiniteTransition(label = "ballSpin")
    // Extra spin so the ball keeps rolling while progress advances around the wheel
    val orbitBoost by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitBoost"
    )
    val ballSpin by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ballSpinSelf"
    )

    val p = progress.coerceIn(0f, 1f)
    // Ball travels around the wheel as load progress increases
    val orbitDeg = (p * 300f) + (orbitBoost * 0.15f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RouletteBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
        ) {
            Text(
                "AUTO ROULETTE",
                color = PrimaryYellow,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                status,
                color = TextGrey,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                // Wheel track (not a loading spinner — decorative roulette rim)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val outer = size.minDimension / 2f
                    val inner = outer * 0.72f
                    // Outer gold rim
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFFFE082),
                                Color(0xFFC9A227),
                                Color(0xFFFFCC00),
                                Color(0xFF8D6E00),
                                Color(0xFFFFE082)
                            )
                        ),
                        radius = outer,
                        center = c
                    )
                    // Center matches in-game wheel scene background
                    drawCircle(color = RouletteBg, radius = inner, center = c)
                    // Inner gold ring
                    drawCircle(
                        color = PrimaryYellow.copy(alpha = 0.85f),
                        radius = inner,
                        center = c,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    // Pocket ticks
                    val tickCount = 18
                    for (i in 0 until tickCount) {
                        val a = Math.toRadians((i * 360.0 / tickCount) - 90.0)
                        val cos = kotlin.math.cos(a).toFloat()
                        val sin = kotlin.math.sin(a).toFloat()
                        val r0 = inner + 4.dp.toPx()
                        val r1 = outer - 8.dp.toPx()
                        drawLine(
                            color = if (i % 2 == 0) Color(0xFFE53935) else Color(0xFF212121),
                            start = Offset(c.x + cos * r0, c.y + sin * r0),
                            end = Offset(c.x + cos * r1, c.y + sin * r1),
                            strokeWidth = 5.dp.toPx()
                        )
                    }
                }

                // Center label
                Text(
                    "${(p * 100).toInt()}%",
                    color = PrimaryYellow,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )

                // White roulette ball racing on the rim
                val density = LocalDensity.current
                val ballSize = 26.dp
                Box(modifier = Modifier.fillMaxSize()) {
                    val angleRad = Math.toRadians(orbitDeg.toDouble() - 90.0)
                    val radiusPx = with(density) { (220.dp / 2f - 18.dp).toPx() }
                    val cx = with(density) { (220.dp / 2f).toPx() }
                    val cy = cx
                    val bx = cx + kotlin.math.cos(angleRad).toFloat() * radiusPx - with(density) { (ballSize / 2f).toPx() }
                    val by = cy + kotlin.math.sin(angleRad).toFloat() * radiusPx - with(density) { (ballSize / 2f).toPx() }
                    RouletteBall(
                        modifier = Modifier
                            .offset(
                                x = with(density) { bx.toDp() },
                                y = with(density) { by.toDp() }
                            )
                            .graphicsLayer { rotationZ = ballSpin }
                            .size(ballSize)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                "Ball in play…",
                color = TextWhite.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RouletteBall(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(CircleShape)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE8E8E8), Color(0xFF9E9E9E)),
                center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
                radius = r * 1.2f
            ),
            radius = r,
            center = c
        )
        drawCircle(
            color = Color(0xFF1A1A1A),
            radius = r * 0.12f,
            center = c
        )
    }
}

/**
 * Prefetch roulette page + assets and warm the real wallet API.
 * Returns false if something soft-failed (still OK to open WebView).
 */
private suspend fun prefetchRouletteBackend(
    onProgress: (Float, String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(NetworkUtils.PREFETCH_CONNECT_SEC, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun get(url: String): String? {
        return try {
            client.newCall(Request.Builder().url(url).header("Accept", "*/*").build())
                .execute()
                .use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                }
        } catch (_: Exception) {
            null
        }
    }

    fun headOrGet(url: String): Boolean {
        return try {
            client.newCall(Request.Builder().url(url).get().build())
                .execute()
                .use { it.isSuccessful || it.code in 300..399 }
        } catch (_: Exception) {
            false
        }
    }

    onProgress(0.05f, "Connecting…")
    val html = get(Constants.ROULETTE_URL) ?: return@withContext false
    onProgress(0.18f, "Loading game files…")

    val base = Uri.parse(Constants.ROULETTE_URL)
    val assetUrls = linkedSetOf<String>()
    Regex("""(?:href|src)\s*=\s*["']([^"']+)["']""")
        .findAll(html)
        .map { it.groupValues[1] }
        .filter { it.isNotBlank() && !it.startsWith("data:") && !it.startsWith("javascript:") }
        .forEach { raw ->
            val resolved = when {
                raw.startsWith("http://") || raw.startsWith("https://") -> raw
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("/") -> "${base.scheme}://${base.host}$raw"
                else -> Constants.ROULETTE_URL.trimEnd('/') + "/" + raw.trimStart('/')
            }
            if (
                resolved.contains("gunduata.tech") ||
                resolved.contains("unpkg.com") ||
                resolved.contains("jsdelivr")
            ) {
                assetUrls.add(resolved)
            }
        }
    Regex(""""(https://[^"]+)"""")
        .findAll(html)
        .map { it.groupValues[1] }
        .forEach { assetUrls.add(it) }

    // Common Three.js paths from import map
    assetUrls.add("https://unpkg.com/three@0.160.0/build/three.module.js")
    assetUrls.add("https://unpkg.com/three@0.160.0/examples/jsm/controls/OrbitControls.js")

    val list = assetUrls.toList()
    list.forEachIndexed { index, url ->
        onProgress(
            0.18f + 0.55f * ((index + 1).toFloat() / list.size.coerceAtLeast(1)),
            "Prefetching ${index + 1}/${list.size}…"
        )
        headOrGet(url)
    }

    onProgress(0.82f, "Syncing wallet…")
    coroutineScope {
        val walletJob = async {
            try {
                RetrofitClient.apiService.getWallet().isSuccessful
            } catch (_: Exception) {
                false
            }
        }
        // Warm roulette API root if present
        val apiJob = async {
            headOrGet(Constants.ROULETTE_API_URL.trimEnd('/') + "/")
        }
        walletJob.await()
        apiJob.await()
    }
    onProgress(0.92f, "Opening table…")
    true
}

private fun buildTokenInjectJs(accessToken: String): String {
    val tokenLit = JSONObject.quote(accessToken)
    val apiLit = JSONObject.quote(Constants.ROULETTE_API_URL)
    return """
        (function(){
          try {
            localStorage.setItem("gundu_access_token", $tokenLit);
            localStorage.setItem("roultee_api", $apiLit);
          } catch (e) {}
        })();
    """.trimIndent()
}
