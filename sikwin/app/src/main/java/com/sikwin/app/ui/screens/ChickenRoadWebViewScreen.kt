package com.sikwin.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import com.sikwin.app.R
import com.sikwin.app.data.api.RetrofitClient
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.ui.theme.TextWhite
import com.sikwin.app.utils.CasinoPrefetcher
import com.sikwin.app.utils.NetworkUtils
import com.sikwin.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val ChickenRoadBg = Color(0xFF0A0A0A)

enum class ChickenRoadGame(
    val title: String,
    val gameUrl: String,
    val pathHint: String,
    val loadingImageRes: Int?
) {
    ONE(
        "Chicken Road",
        Constants.CHICKEN_ROAD_URL,
        "/chicken-road",
        R.drawable.chicken_road_loading
    ),
    TWO(
        "Chicken Road 2",
        Constants.CHICKEN_ROAD_2_URL,
        "/chicken-road-2",
        R.drawable.chicken_road_2_loading
    ),
    VORTEX(
        "Vortex",
        Constants.VORTEX_URL,
        "/vortex",
        null
    ),
    CASINO(
        title = "Casino Games",
        gameUrl = Constants.CASINO_URL,
        pathHint = "/casino",
        loadingImageRes = null
    )
}

/**
 * Chicken Road / Chicken Road 2 / Vortex / Casino — real Pride wallet.
 * Same pattern as Auto Roulette & Stock Market: WebView + JWT in `?token=`.
 * Opening any of these also warms the Casino lobby cache for faster Casino tab loads.
 */
@Composable
fun ChickenRoadWebViewScreen(
    game: ChickenRoadGame,
    accessToken: String?,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    /** Casino lobby: chit-pat / rangu → native Compose routes (`coin`, `colour_game`). */
    onOpenNativeGame: (route: String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val leaveCasino = remember(onBack) {
        {
            CasinoPrefetcher.prepareLeave()
            onBack()
        }
    }
    BackHandler(onBack = {
        if (game == ChickenRoadGame.CASINO) {
            // Game → casino lobby; lobby → home
            CasinoPrefetcher.handleBack { leaveCasino() }
        } else {
            onBack()
        }
    })

    LaunchedEffect(game, accessToken) {
        if (game != ChickenRoadGame.CASINO) {
            // Prefetch casino lobby while playing so Casino opens instantly next
            CasinoPrefetcher.prefetchWhilePlaying(context, accessToken)
        }
        // Casino screen: attach() loads / shows parked lobby — do not warm() here
        // (warm was calling onPause and freezing the visible page)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChickenRoadBg)
    ) {
        when {
            accessToken.isNullOrBlank() -> {
                ChickenRoadLoginRequired(
                    title = game.title,
                    onLogin = {
                        onBack()
                        onRequireLogin()
                    },
                    onBack = onBack
                )
            }
            else -> {
                if (game == ChickenRoadGame.CASINO) {
                    CasinoPreloadedWebView(
                        accessToken = accessToken,
                        onBack = leaveCasino,
                        onOpenNativeGame = onOpenNativeGame,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ChickenRoadWebView(
                        game = game,
                        accessToken = accessToken,
                        onBack = onBack,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun CasinoSiteGameScreen(
    title: String,
    gameUrl: String,
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
            .background(ChickenRoadBg)
    ) {
        when {
            accessToken.isNullOrBlank() -> {
                ChickenRoadLoginRequired(
                    title = title,
                    onLogin = {
                        onBack()
                        onRequireLogin()
                    },
                    onBack = onBack
                )
            }
            else -> {
                ChickenRoadWebView(
                    game = ChickenRoadGame.VORTEX,
                    accessToken = accessToken,
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
                    gameUrlOverride = gameUrl,
                    titleOverride = title
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CasinoPreloadedWebView(
    accessToken: String,
    onBack: () -> Unit,
    onOpenNativeGame: (route: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ready by remember { mutableStateOf(CasinoPrefetcher.isReady(accessToken)) }
    var networkError by remember { mutableStateOf(CasinoPrefetcher.hasNetworkError()) }

    // Do not call warm() here — attach() loads if needed.
    // warm() from home/login already prefetched; a second warm was pausing the visible WebView.

    DisposableEffect(accessToken) {
        val readyListener: (Boolean) -> Unit = { isReady ->
            ready = CasinoPrefetcher.isReady(accessToken)
            if (!isReady && CasinoPrefetcher.hasNetworkError()) {
                networkError = true
            }
        }
        val errorListener: (Boolean) -> Unit = { networkError = it }
        CasinoPrefetcher.addReadyListener(readyListener)
        CasinoPrefetcher.addNetworkErrorListener(errorListener)
        ready = CasinoPrefetcher.isReady(accessToken)
        networkError = CasinoPrefetcher.hasNetworkError()
        onDispose {
            CasinoPrefetcher.removeReadyListener(readyListener)
            CasinoPrefetcher.removeNetworkErrorListener(errorListener)
            CasinoPrefetcher.detach()
        }
    }

    LaunchedEffect(accessToken, networkError) {
        if (networkError) return@LaunchedEffect
        if (!NetworkUtils.isOnline(context)) {
            networkError = true
            return@LaunchedEffect
        }
        val ok = NetworkUtils.awaitReadyOrOffline(
            context = context,
            isReady = { CasinoPrefetcher.isReady(accessToken) },
            hasError = { CasinoPrefetcher.hasNetworkError() }
        )
        if (!ok) {
            networkError = true
        } else {
            ready = true
        }
    }

    Box(
        modifier = modifier.background(ChickenRoadBg),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).also { frame ->
                    val already = CasinoPrefetcher.attach(
                        parent = frame,
                        token = accessToken,
                        onBack = onBack,
                        onOpenGame = { id, urlOrRoute ->
                            val native = CasinoPrefetcher.nativeRouteFor(id, urlOrRoute)
                                ?: if (urlOrRoute == "coin" || urlOrRoute == "colour_game") urlOrRoute else null
                            if (native != null) {
                                onOpenNativeGame(native)
                            }
                        }
                    )
                    ready = already || CasinoPrefetcher.isReady(accessToken)
                    networkError = CasinoPrefetcher.hasNetworkError()
                }
            },
            update = { frame ->
                if (frame.childCount == 0) {
                    CasinoPrefetcher.attach(
                        parent = frame,
                        token = accessToken,
                        onBack = onBack,
                        onOpenGame = { id, urlOrRoute ->
                            val native = CasinoPrefetcher.nativeRouteFor(id, urlOrRoute)
                                ?: if (urlOrRoute == "coin" || urlOrRoute == "colour_game") urlOrRoute else null
                            if (native != null) {
                                onOpenNativeGame(native)
                            }
                        }
                    )
                }
                ready = CasinoPrefetcher.isReady(accessToken)
                networkError = CasinoPrefetcher.hasNetworkError()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!ready && !networkError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = PrimaryYellow,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    if (CasinoPrefetcher.hasOpenInnerGame()) "Loading game…" else "Loading Casino…",
                    color = TextGrey,
                    fontSize = 14.sp
                )
            }
        }

        if (networkError) {
            NoInternetConnectionOverlay(
                onRetry = { CasinoPrefetcher.retry(accessToken) },
                background = ChickenRoadBg
            )
        }
    }
}

@Composable
private fun ChickenRoadLoginRequired(
    title: String,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
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
                "$title uses your real Pride wallet.\nPlease sign in to continue.",
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
private fun ChickenRoadWebView(
    game: ChickenRoadGame,
    accessToken: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gameUrlOverride: String? = null,
    titleOverride: String? = null
) {
    var pageReady by remember { mutableStateOf(false) }
    var gameReady by remember { mutableStateOf(false) }
    var prefetchDone by remember { mutableStateOf(false) }
    var networkError by remember { mutableStateOf(false) }
    var initialLoadDone by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0.02f) }
    var statusText by remember { mutableStateOf("Preparing ${titleOverride ?: game.title}…") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val playUrl = gameUrlOverride ?: game.gameUrl
    val playTitle = titleOverride ?: game.title

    val injectJs = remember(accessToken) { buildChickenRoadTokenInjectJs(accessToken) }
    val startUrl = remember(accessToken, playUrl) {
        val parsed = Uri.parse(playUrl)
        if (parsed.getQueryParameter("token").isNullOrBlank()) {
            parsed.buildUpon().appendQueryParameter("token", accessToken).build().toString()
        } else {
            playUrl
        }
    }

    fun markNetworkError() {
        networkError = true
        statusText = "No internet connection"
    }

    LaunchedEffect(accessToken, playUrl, retryToken) {
        networkError = false
        pageReady = false
        gameReady = false
        prefetchDone = false
        initialLoadDone = false
        progress = 0.02f
        statusText = "Fetching game…"
        if (!NetworkUtils.isOnline(context)) {
            markNetworkError()
            return@LaunchedEffect
        }
        val ok = prefetchChickenRoadBackend(playUrl) { p, label ->
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
        statusText = "Opening $playTitle…"
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
        initialLoadDone = true
        delay(200)
    }

    // Keep loader until page + game JS are ready (not only first prefetch pass).
    val showLoader = !networkError && (!pageReady || !gameReady || !initialLoadDone)

    Box(modifier = modifier) {
        key(retryToken) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(AndroidColor.parseColor("#0A0A0A"))
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

                        val bridge = ChickenRoadJsBridge(
                            onGameReady = { scope.launch { gameReady = true } }
                        )
                        addJavascriptInterface(bridge, "GunduChicken")

                        // Casino lobby page calls window.AndroidBridge.goBack / openGame
                        if (game == ChickenRoadGame.CASINO) {
                            val webViewRef = this
                            addJavascriptInterface(
                                CasinoAndroidBridge(
                                    onBack = { scope.launch { onBack() } },
                                    onOpenGame = { id, url ->
                                        scope.launch {
                                            // Prefer CasinoPrefetcher path for lobby; here only load web games
                                            if (CasinoPrefetcher.nativeRouteFor(id, url) == null) {
                                                webViewRef.post { webViewRef.loadUrl(url) }
                                            }
                                        }
                                    }
                                ),
                                "AndroidBridge"
                            )
                        }

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

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
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
                                view?.evaluateJavascript(CHICKEN_READY_POLL_JS, null)
                                pageReady = true
                                scope.launch {
                                    delay(2000)
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
                                    (failingUrl.contains(game.pathHint) || failingUrl.contains("gunduata.tech"))
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
            ChickenRoadLoadingOverlay(
                title = playTitle,
                loadingImageRes = if (gameUrlOverride != null) null else game.loadingImageRes,
                progress = progress,
                status = statusText
            )
        }

        if (networkError) {
            NoInternetConnectionOverlay(
                onRetry = { retryToken += 1 },
                background = ChickenRoadBg
            )
        }
    }
}

private class ChickenRoadJsBridge(
    private val onGameReady: () -> Unit
) {
    private val fired = AtomicBoolean(false)

    @JavascriptInterface
    fun onReady() {
        if (fired.compareAndSet(false, true)) onGameReady()
    }
}

private class CasinoAndroidBridge(
    private val onBack: () -> Unit,
    private val onOpenGame: (String, String) -> Unit
) {
    @JavascriptInterface
    fun goBack() {
        onBack()
    }

    @JavascriptInterface
    fun openGame(id: String, url: String) {
        onOpenGame(id, url)
    }
}

private const val CHICKEN_READY_POLL_JS = """
(function(){
  function ready(){ try { GunduChicken.onReady(); } catch(e) {} }
  var tries = 0;
  function check(){
    tries++;
    if (document.body || tries > 40) {
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
private fun ChickenRoadLoadingOverlay(
    title: String,
    loadingImageRes: Int?,
    progress: Float,
    status: String
) {
    val p = progress.coerceIn(0f, 1f)
    val pct = (p * 100).toInt()
    val barShape = RoundedCornerShape(50)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChickenRoadBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            if (loadingImageRes != null) {
                Image(
                    painter = painterResource(id = loadingImageRes),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Text(
                    title.uppercase(),
                    color = PrimaryYellow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(status, color = TextGrey, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(barShape)
                    .border(1.5.dp, PrimaryYellow.copy(alpha = 0.85f), barShape)
                    .background(Color(0xFF1A1A1A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(p.coerceAtLeast(0.02f))
                        .clip(barShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFFCC00),
                                    Color(0xFFFFE082),
                                    Color(0xFFFFCC00)
                                )
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "$pct%",
                color = PrimaryYellow,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private suspend fun prefetchChickenRoadBackend(
    gameUrl: String,
    onProgress: (Float, String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(NetworkUtils.PREFETCH_CONNECT_SEC, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url).header("Accept", "*/*").build())
            .execute()
            .use { if (it.isSuccessful) it.body?.string() else null }
    } catch (_: Exception) {
        null
    }

    fun headOrGet(url: String): Boolean = try {
        client.newCall(Request.Builder().url(url).get().build())
            .execute()
            .use { it.isSuccessful || it.code in 300..399 }
    } catch (_: Exception) {
        false
    }

    onProgress(0.05f, "Connecting…")
    val html = get(gameUrl) ?: return@withContext false
    onProgress(0.2f, "Loading game…")

    val base = Uri.parse(gameUrl)
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
                else -> gameUrl.trimEnd('/') + "/" + raw.trimStart('/')
            }
            if (resolved.contains("gunduata.tech")) {
                assetUrls.add(resolved)
            }
        }

    val list = assetUrls.toList()
    list.forEachIndexed { index, url ->
        onProgress(
            0.2f + 0.55f * ((index + 1).toFloat() / list.size.coerceAtLeast(1)),
            "Prefetching ${index + 1}/${list.size}…"
        )
        headOrGet(url)
    }

    onProgress(0.85f, "Syncing wallet…")
    try {
        RetrofitClient.apiService.getWallet().isSuccessful
    } catch (_: Exception) {
        false
    }
    onProgress(0.92f, "Opening game…")
    true
}

private fun buildChickenRoadTokenInjectJs(accessToken: String): String {
    val tokenLit = JSONObject.quote(accessToken)
    return """
        (function(){
          try {
            localStorage.setItem("gundu_access_token", $tokenLit);
          } catch (e) {}
        })();
    """.trimIndent()
}
