package com.sikwin.app.utils

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps a live Casino WebView warmed from APK open so Casino opens instantly
 * (no “Preparing Casino…” spinner when the page is already ready).
 */
object CasinoPrefetcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(NetworkUtils.PREFETCH_CONNECT_SEC, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var activityRef: WeakReference<Activity>? = null
    private var holder: FrameLayout? = null
    private var webView: WebView? = null
    private var loadedToken: String? = null
    @Volatile private var pageReady: Boolean = false
    @Volatile private var networkError: Boolean = false
    private val assetsStarted = AtomicBoolean(false)
    private val readyListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private val networkErrorListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private var onBack: (() -> Unit)? = null
    private var onOpenGame: ((String, String) -> Unit)? = null
    /** True only while Casino screen is showing the WebView. */
    @Volatile private var attachedVisible: Boolean = false
    /** Lobby scrollY captured before opening a game — restored on return. */
    @Volatile private var savedLobbyScrollY: Int = 0
    @Volatile private var pendingRestoreScroll: Boolean = false

    fun isReady(token: String?): Boolean {
        val wv = webView ?: return false
        if (networkError) return false
        if (!pageReady) return false
        if (!token.isNullOrBlank() && loadedToken != token) return false
        val url = wv.url.orEmpty()
        return url.contains("/casino") && !WebViewOffline.isChromeErrorUrl(url)
    }

    fun hasNetworkError(): Boolean = networkError

    fun addReadyListener(listener: (Boolean) -> Unit) {
        readyListeners.add(listener)
        // Report current readiness for the token we actually loaded
        try {
            listener(pageReady && webView != null && webView?.url.orEmpty().contains("/casino") && !networkError)
        } catch (_: Exception) {
        }
    }

    fun removeReadyListener(listener: (Boolean) -> Unit) {
        readyListeners.remove(listener)
    }

    fun addNetworkErrorListener(listener: (Boolean) -> Unit) {
        networkErrorListeners.add(listener)
        try {
            listener(networkError)
        } catch (_: Exception) {
        }
    }

    fun removeNetworkErrorListener(listener: (Boolean) -> Unit) {
        networkErrorListeners.remove(listener)
    }

    fun retry(token: String?) {
        mainHandler.post {
            networkError = false
            notifyNetworkError(false)
            setReady(false)
            loadCasino(token, force = true, cacheBust = true)
        }
    }

    /** Warm casino lobby WebView (optional force reload). */
    fun warm(context: Context?, token: String? = null, force: Boolean = false) {
        val activity = resolveActivity(context) ?: return
        activityRef = WeakReference(activity)
        warmHttpAssets()
        mainHandler.post {
            try {
                ensureWebView(activity)
                // Never interrupt an on-screen Casino session
                if (attachedVisible && !force) return@post
                if (attachedVisible && force) {
                    loadCasino(token, force = true)
                    return@post
                }
                // Must be resumed or the lobby never finishes loading in the background.
                try {
                    webView?.onResume()
                } catch (_: Exception) {
                }
                loadCasino(token, force = force)
                if (!attachedVisible) {
                    muteParked(webView)
                }
            } catch (_: Exception) {
                /* soft */
            }
        }
    }

    /**
     * Prefetch casino lobby on home / right after login (muted, still loading).
     * Skip if already ready for this token.
     */
    fun prefetchOnHome(context: Context?, token: String?) {
        if (token.isNullOrBlank()) return
        if (isReady(token)) return
        warm(context, token, force = false)
    }

    /**
     * Call when opening any game (chicken, roulette, coin, etc.) so Casino
     * is already loaded when the user taps Casino next.
     * Never force-reload — that wiped a parked lobby (and scroll) when returning.
     */
    fun prefetchWhilePlaying(context: Context?, token: String? = null) {
        if (token.isNullOrBlank()) return
        if (isReady(token)) return
        warm(context, token, force = false)
    }

    /**
     * Moves the preloaded WebView into [parent]. Returns true if already ready to show.
     */
    fun attach(
        parent: ViewGroup,
        token: String,
        onBack: () -> Unit,
        onOpenGame: (String, String) -> Unit
    ): Boolean {
        this.onBack = onBack
        this.onOpenGame = onOpenGame
        attachedVisible = true
        val activity = resolveActivity(parent.context) ?: return false
        ensureWebView(activity)
        val wv = webView ?: return false

        (wv.parent as? ViewGroup)?.removeView(wv)
        parent.addView(
            wv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        try {
            wv.onResume()
            unmuteAudio(wv)
        } catch (_: Exception) {
        }
        wv.visibility = android.view.View.VISIBLE
        applyAttachedAlpha(wv, token)

        // Use parked lobby if ready — instant Casino open + restore scroll
        if (isReady(token)) {
            restoreLobbyScroll(wv)
            return true
        }
        loadCasino(token, force = false)
        return isReady(token)
    }

    /** Hide WebView before navigating away so home does not flicker. */
    fun prepareLeave() {
        attachedVisible = false
        val wv = webView ?: return
        captureLobbyScroll(wv)
        silenceParked(wv)
        try {
            wv.alpha = 0f
            wv.visibility = android.view.View.INVISIBLE
        } catch (_: Exception) {
        }
    }

    /** Park the WebView off-screen so the next Casino open is still instant. */
    fun detach() {
        attachedVisible = false
        onBack = null
        onOpenGame = null
        val wv = webView ?: return
        val h = holder ?: return
        val url = wv.url.orEmpty()
        val token = loadedToken
        captureLobbyScroll(wv)
        val needsLobbyReload = networkError ||
            looksLikeInnerGame(url) ||
            !url.contains("/casino") ||
            WebViewOffline.isChromeErrorUrl(url)

        // Hide first — removing a visible WebView over home causes flicker.
        silenceParked(wv)
        try {
            wv.alpha = 0f
            wv.visibility = android.view.View.GONE
        } catch (_: Exception) {
        }

        try {
            (wv.parent as? ViewGroup)?.removeView(wv)
            if (wv.parent == null) {
                h.addView(wv, FrameLayout.LayoutParams(1, 1))
            }
            h.visibility = android.view.View.GONE
            h.alpha = 0f
        } catch (_: Exception) {
        }

        // Soft warm only when not already on lobby — keep scroll when possible.
        // Keep WebView resumed until lobby is ready, otherwise prefetch never finishes.
        if (needsLobbyReload) {
            pendingRestoreScroll = savedLobbyScrollY > 0
            mainHandler.post {
                try {
                    webView?.onResume()
                    loadCasino(token, force = false)
                    muteParked(webView)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun clear() {
        attachedVisible = false
        savedLobbyScrollY = 0
        pendingRestoreScroll = false
        mainHandler.post {
            try {
                webView?.stopLoading()
                webView?.clearCache(true)
                webView?.destroy()
            } catch (_: Exception) {
            }
            webView = null
            holder = null
            loadedToken = null
            pageReady = false
            networkError = false
            setReady(false)
        }
    }

    /** Mute audio while parked. Pause only after lobby is ready so prefetch can finish. */
    private fun silenceParked(wv: WebView?) {
        muteParked(wv)
        if (wv == null || !pageReady) return
        try {
            wv.onPause()
        } catch (_: Exception) {
        }
    }

    private fun muteParked(wv: WebView?) {
        if (wv == null) return
        try {
            wv.evaluateJavascript(CASINO_MUTE_JS, null)
        } catch (_: Exception) {
        }
    }

    private fun unmuteAudio(wv: WebView?) {
        if (wv == null) return
        try {
            wv.evaluateJavascript(CASINO_UNMUTE_JS, null)
        } catch (_: Exception) {
        }
    }

    /**
     * Casino lobby game id → site path (mirrors casino `games.js` / GameWebViewActivity.pathForGameId).
     * Play usually passes a full URL; this is the fallback + back-stack recognition map.
     */
    fun pathForGameId(id: String?): String? {
        return when (id.orEmpty().trim().lowercase().replace('_', '-')) {
            "gundu-ata", "gunduata" -> "/game/"
            "stock-market", "trading" -> "/trading/"
            "auto-roulette", "roulette" -> "/roulette/"
            "chicken-road" -> "/chicken-road/"
            "chicken-road-2" -> "/chicken-road-2/"
            "vortex" -> "/vortex/"
            "vortex-1", "vortex1" -> "/vortex-1/"
            "vip-vortex", "vipvortex" -> "/vip-vortex/"
            "mines" -> "/mines/"
            "steps" -> "/steps/"
            "boxes" -> "/boxes/"
            "snake" -> "/snake/"
            "slide" -> "/slide/"
            "cases" -> "/cases/"
            "drop" -> "/drop/"
            "plinko" -> "/plinko/"
            "air-balloon", "airballoon" -> "/air-balloon/"
            "horse-racing", "horseracing", "horse-race", "horserace" -> "/horse-racing/"
            "under-6", "under6", "6-cards", "six-cards" -> "/under-6/"
            "rushbet", "rush-bet" -> "/rushbet/"
            "knock6", "knock-6" -> "/knock6/"
            "tripleedge", "triple-edge" -> "/tripleedge/"
            "mirror" -> "/mirror/"
            "goldlane", "gold-lane" -> "/goldlane/"
            "dead7", "dead-7" -> "/dead7/"
            "teenpatti", "teen-patti" -> "/teenpatti/"
            "circle-game", "circlegame" -> "/circle-game/"
            "stop-bar", "stopbar" -> "/stop-bar/"
            "spin-dial", "spindial" -> "/spin-dial/"
            "mines-path", "minespath" -> "/mines-path/"
            "dice-over-under", "diceoverunder" -> "/dice-over-under/"
            "color-match", "colour-match", "colormatch" -> "/color-match/"
            "wheel-pockets", "wheelpockets" -> "/wheel-pockets/"
            "wave-surf", "wavesurf" -> "/wave-surf/"
            "keno-pick", "kenopick" -> "/keno-pick/"
            "hi-lo-cards", "hilo-cards", "hi-lo", "hilocards" -> "/hi-lo-cards/"
            "aviator" -> "/aviator/"
            "jet" -> "/jet/"
            "maestro" -> "/maestro/"
            "deep-dive", "deepdive" -> "/deep-dive/"
            "sky-lift", "skylift" -> "/sky-lift/"
            "paper-plane", "paperplane" -> "/paper-plane/"
            "ufo-lift", "ufolift" -> "/ufo-lift/"
            "shark-bite", "sharkbite" -> "/shark-bite/"
            "chit-pat", "chitpat" -> "/chit-pat/"
            "rangu" -> "/rangu/"
            else -> {
                val slug = id.orEmpty().trim().lowercase().replace('_', '-')
                if (slug.isNotBlank() && slug != "casino") "/$slug/" else null
            }
        }
    }

    /**
     * Android / JS back:
     * - Inside a game opened from Casino → return to casino lobby (not home)
     * - On casino lobby → [onClose] (home)
     */
    fun handleBack(onClose: () -> Unit) {
        mainHandler.post {
            val wv = webView
            val url = wv?.url.orEmpty()
            if (wv != null && !isCasinoLobbyUrl(url)) {
                // Playing a game — return to casino lobby and restore scroll
                pendingRestoreScroll = savedLobbyScrollY > 0
                if (wv.canGoBack() && looksLikeInnerGame(url)) {
                    try {
                        wv.goBack()
                        // History restore may land on lobby; reinforce scroll shortly after
                        mainHandler.postDelayed({ restoreLobbyScroll(webView) }, 120)
                        mainHandler.postDelayed({ restoreLobbyScroll(webView) }, 350)
                    } catch (_: Exception) {
                        loadCasino(loadedToken, force = false)
                    }
                } else {
                    loadCasino(loadedToken, force = false)
                }
                return@post
            }
            prepareLeave()
            onClose()
        }
    }

    fun isOnLobby(): Boolean = isCasinoLobbyUrl(webView?.url.orEmpty())

    /** True when Casino WebView is showing an inner game (not the lobby). */
    fun hasOpenInnerGame(): Boolean {
        val url = webView?.url.orEmpty()
        return url.isNotBlank() && !isCasinoLobbyUrl(url) && looksLikeInnerGame(url)
    }

    private fun isCasinoLobbyUrl(url: String): Boolean {
        if (url.isBlank() || WebViewOffline.isChromeErrorUrl(url)) return false
        val path = urlPath(url)
        return path == "/casino" || path.endsWith("/casino")
    }

    /** Any gunduata game page except the casino lobby — no APK update per new game path. */
    private fun looksLikeInnerGame(url: String): Boolean {
        if (url.isBlank() || WebViewOffline.isChromeErrorUrl(url)) return false
        if (isCasinoLobbyUrl(url)) return false
        val u = url.lowercase()
        return u.contains("gunduata.tech") || u.startsWith("/")
    }

    private fun urlPath(url: String): String {
        val raw = url.lowercase().substringBefore('?').substringBefore('#')
        return try {
            when {
                raw.startsWith("http://") || raw.startsWith("https://") ->
                    Uri.parse(raw).path.orEmpty().trimEnd('/')
                raw.startsWith("/") -> raw.trimEnd('/')
                else -> ""
            }
        } catch (_: Exception) {
            raw.trimEnd('/')
        }
    }

    /** Set when a casino tile opens a site game that has no dedicated Compose screen. */
    @Volatile var pendingWebGameUrl: String? = null
        private set
    @Volatile var pendingWebGameTitle: String? = null
        private set

    /**
     * Casino lobby tile → Compose route. Always leave the lobby WebView on /casino
     * so coming back does not reload game tiles.
     */
    fun nativeRouteFor(id: String?, url: String?): String? {
        val key = id.orEmpty().trim().lowercase().replace('_', '-')
        val u = url.orEmpty().lowercase()
        return when {
            key == "chit-pat" || key == "chitpat" || key == "coin" ||
                u.contains("/chit-pat") || (u.contains("/coin") && !u.contains("/colour")) ->
                "coin"
            key == "rangu" || key == "colour" || key == "colour-game" || key == "color" ||
                u.contains("/rangu") || u.contains("/colour") ->
                "colour_game"
            key == "chicken-road-2" || u.contains("/chicken-road-2") ->
                "chicken_road_2"
            key == "chicken-road" || u.contains("/chicken-road") ->
                "chicken_road"
            key == "vortex2" || key == "vortex-2" || u.contains("/vortex2") ->
                "vortex"
            key == "vip-vortex" || key == "vipvortex" || u.contains("/vip_vortex") || u.contains("/vip-vortex") ->
                "vortex"
            key == "vortex" || (u.contains("/vortex") && !u.contains("vip")) ->
                "vortex"
            key == "auto-roulette" || key == "roulette" || u.contains("/roulette") ->
                "roulette"
            key == "stock-market" || key == "trading" || u.contains("/trading") ->
                "trading"
            key == "gundu-ata" || key == "gunduata" || u.contains("/game/") ->
                "gundu_ata_web"
            else -> {
                val target = when {
                    url.orEmpty().startsWith("http://") || url.orEmpty().startsWith("https://") -> url
                    url.orEmpty().startsWith("/") -> "https://gunduata.tech$url"
                    else -> pathForGameId(id)?.let { "https://gunduata.tech$it" }
                }
                if (target.isNullOrBlank()) {
                    null
                } else {
                    val withToken = if (!loadedToken.isNullOrBlank() && !target.contains("token=")) {
                        Uri.parse(target).buildUpon()
                            .appendQueryParameter("token", loadedToken)
                            .build()
                            .toString()
                    } else {
                        target
                    }
                    pendingWebGameUrl = withToken
                    pendingWebGameTitle = id?.replace('-', ' ')?.replaceFirstChar { it.uppercase() } ?: "Game"
                    "casino_web_game"
                }
            }
        }
    }

    private fun resolveActivity(context: Context?): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        if (context is Activity) return context
        return activityRef?.get()
    }

    private fun buildUrl(token: String?, cacheBust: Boolean = false): String {
        val builder = Uri.parse(Constants.CASINO_URL).buildUpon()
        if (!token.isNullOrBlank()) builder.appendQueryParameter("token", token)
        if (cacheBust) builder.appendQueryParameter("_", System.currentTimeMillis().toString())
        return builder.build().toString()
    }

    private fun applyAttachedAlpha(wv: WebView?, token: String?) {
        if (wv == null) return
        try {
            wv.alpha = when {
                networkError -> 0f
                attachedVisible && isReady(token) -> 1f
                else -> 0f
            }
        } catch (_: Exception) {
        }
    }

    private fun setReady(ready: Boolean) {
        pageReady = ready
        readyListeners.forEach { listener ->
            try {
                listener(ready)
            } catch (_: Exception) {
            }
        }
    }

    private fun notifyNetworkError(error: Boolean) {
        networkError = error
        networkErrorListeners.forEach { listener ->
            try {
                listener(error)
            } catch (_: Exception) {
            }
        }
    }

    private fun markOffline(view: WebView?) {
        WebViewOffline.hideChromeErrorPage(view)
        setReady(false)
        notifyNetworkError(true)
    }

    private fun loadCasino(token: String?, force: Boolean = false, cacheBust: Boolean = false) {
        val wv = webView ?: return
        val ctx = activityRef?.get()
        if (ctx != null && !NetworkUtils.isOnline(ctx)) {
            markOffline(wv)
            return
        }
        val current = wv.url.orEmpty()
        val onLobby = current.contains("/casino") &&
            !looksLikeInnerGame(current) &&
            !WebViewOffline.isChromeErrorUrl(current)
        // Same token + already on (or loading) casino lobby → do not reload (keeps scroll)
        if (!force && loadedToken == token && !networkError && onLobby) {
            if (pageReady) {
                setReady(true)
                restoreLobbyScroll(wv)
            }
            return
        }
        loadedToken = token
        networkError = false
        notifyNetworkError(false)
        setReady(false)
        if (savedLobbyScrollY > 0) pendingRestoreScroll = true
        applyAttachedAlpha(wv, token)
        try {
            wv.loadUrl(buildUrl(token, cacheBust = cacheBust))
        } catch (_: Exception) {
            markOffline(wv)
        }
    }

    private fun captureLobbyScroll(wv: WebView?) {
        if (wv == null) return
        val url = wv.url.orEmpty()
        if (!isCasinoLobbyUrl(url)) return
        try {
            wv.evaluateJavascript(CASINO_GET_SCROLL_JS) { raw ->
                val n = raw?.trim()?.trim('"')?.toDoubleOrNull()?.toInt() ?: return@evaluateJavascript
                if (n >= 0) {
                    savedLobbyScrollY = n
                    pendingRestoreScroll = n > 0
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun restoreLobbyScroll(wv: WebView?) {
        if (wv == null) return
        if (!pendingRestoreScroll && savedLobbyScrollY <= 0) return
        if (!isCasinoLobbyUrl(wv.url.orEmpty()) && !pageReady) return
        val y = savedLobbyScrollY
        if (y <= 0) {
            pendingRestoreScroll = false
            return
        }
        try {
            wv.evaluateJavascript(CASINO_RESTORE_SCROLL_JS.replace("%%Y%%", y.toString()), null)
            // Keep pending briefly so late layout passes can re-apply
            mainHandler.postDelayed({
                try {
                    webView?.evaluateJavascript(
                        CASINO_RESTORE_SCROLL_JS.replace("%%Y%%", y.toString()),
                        null
                    )
                } catch (_: Exception) {
                }
                pendingRestoreScroll = false
            }, 280)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(activity: Activity) {
        if (holder == null) {
            holder = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(1, 1)
                visibility = android.view.View.GONE
            }
            // Keep off-screen under the activity content so WebView stays alive.
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            root?.addView(holder)
        }
        if (webView != null) return

        val wv = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(1, 1)
            setBackgroundColor(Color.parseColor("#0A0A0A"))
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

            addJavascriptInterface(CasinoBridge(), "AndroidBridge")

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
                    val u = url.orEmpty()
                    if (u.contains("/casino") && !looksLikeInnerGame(u)) {
                        setReady(false)
                        if (attachedVisible) {
                            try {
                                view?.alpha = 0f
                            } catch (_: Exception) {
                            }
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (WebViewOffline.isChromeErrorUrl(url)) {
                        markOffline(view)
                        return
                    }
                    val u = url.orEmpty()
                    if (u.contains("/casino") && !looksLikeInnerGame(u)) {
                        networkError = false
                        notifyNetworkError(false)
                        if (attachedVisible) {
                            applyAttachedAlpha(view, loadedToken)
                            unmuteAudio(view)
                        } else {
                            muteParked(view)
                        }
                        setReady(true)
                        restoreLobbyScroll(view)
                        // Pause only after lobby is fully loaded (saves CPU, keeps page in memory)
                        if (!attachedVisible) {
                            try {
                                view?.onPause()
                            } catch (_: Exception) {
                            }
                        }
                    } else if (!attachedVisible) {
                        muteParked(view)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (WebViewOffline.isMainFrameError(request)) {
                        markOffline(view)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    if (!failingUrl.isNullOrBlank()) {
                        markOffline(view)
                    }
                }
            }
        }
        webView = wv
        holder?.addView(wv)
    }

    private class CasinoBridge {
        @JavascriptInterface
        fun goBack() {
            handleBack { onBack?.invoke() }
        }

        @JavascriptInterface
        fun openGame(id: String, url: String) {
            mainHandler.post {
                val wv = webView
                val openTarget = {
                    // Never load a game into the lobby WebView — that wipes tiles
                    // and makes Casino reload when the user comes back.
                    val route = nativeRouteFor(id, url)
                    if (route != null) {
                        prepareLeave()
                        onOpenGame?.invoke(id, route)
                    }
                }

                // Save lobby scroll first (async JS), then open game
                if (wv != null && isCasinoLobbyUrl(wv.url.orEmpty())) {
                    try {
                        wv.evaluateJavascript(CASINO_GET_SCROLL_JS) { raw ->
                            val n = raw?.trim()?.trim('"')?.toDoubleOrNull()?.toInt()
                            if (n != null && n >= 0) {
                                savedLobbyScrollY = n
                                pendingRestoreScroll = n > 0
                            }
                            mainHandler.post { openTarget() }
                        }
                        return@post
                    } catch (_: Exception) {
                    }
                }
                openTarget()
            }
        }
    }

    private fun warmHttpAssets() {
        // Disabled — no background casino asset prefetch
    }

    private fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url).header("Accept", "*/*").build())
            .execute()
            .use { if (it.isSuccessful) it.body?.string() else null }
    } catch (_: Exception) {
        null
    }

    private fun headOrGet(url: String) {
        try {
            client.newCall(Request.Builder().url(url).get().build())
                .execute()
                .use { /* discard */ }
        } catch (_: Exception) {
            /* soft */
        }
    }

    private fun resolve(base: Uri, raw: String): String? {
        if (raw.isBlank() || raw.startsWith("data:") || raw.startsWith("javascript:")) return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "${base.scheme}://${base.host}$raw"
            else -> Constants.CASINO_URL.trimEnd('/') + "/" + raw.trimStart('/')
        }
    }

    private fun prefetchCasinoAssets() {
        val baseUrl = Constants.CASINO_URL
        val base = Uri.parse(baseUrl)
        val html = get(baseUrl) ?: return

        val urls = linkedSetOf<String>()
        Regex("""(?:href|src)\s*=\s*["']([^"']+)["']""")
            .findAll(html)
            .map { it.groupValues[1] }
            .forEach { raw -> resolve(base, raw)?.let { urls.add(it) } }

        urls.add(baseUrl.trimEnd('/') + "/styles.css")
        urls.add(baseUrl.trimEnd('/') + "/app.js")
        urls.add(baseUrl.trimEnd('/') + "/games.js")

        val gamesJs = get(baseUrl.trimEnd('/') + "/games.js").orEmpty()
        Regex("""image\s*:\s*["']([^"']+)["']""")
            .findAll(gamesJs)
            .map { it.groupValues[1] }
            .forEach { raw -> resolve(base, raw)?.let { urls.add(it) } }

        urls.forEach { url ->
            if (url.contains("gunduata.tech") || url.contains("fonts.googleapis") || url.contains("fonts.gstatic")) {
                headOrGet(url)
            }
        }
    }
}

private val CASINO_GET_SCROLL_JS = """
    (function(){
      try {
        return Math.max(
          window.scrollY || 0,
          document.documentElement.scrollTop || 0,
          document.body.scrollTop || 0
        );
      } catch (e) { return 0; }
    })();
""".trimIndent()

private val CASINO_RESTORE_SCROLL_JS = """
    (function(){
      try {
        var y = %%Y%%;
        var apply = function(){
          try {
            window.scrollTo(0, y);
            document.documentElement.scrollTop = y;
            if (document.body) document.body.scrollTop = y;
            var sc = document.querySelector('.casino-scroll, .games-scroll, main, #app, .scroll');
            if (sc) sc.scrollTop = y;
          } catch (e) {}
        };
        apply();
        setTimeout(apply, 40);
        setTimeout(apply, 160);
      } catch (e) {}
    })();
""".trimIndent()

private val CASINO_MUTE_JS = """
    (function(){
      try {
        window.__casinoParkMuted = true;
        document.querySelectorAll('audio,video').forEach(function(el){
          try { el.muted = true; el.pause(); el.volume = 0; } catch (e) {}
        });
        if (window.Howler && typeof Howler.mute === 'function') Howler.mute(true);
        if (window.Howler && typeof Howler.volume === 'function') Howler.volume(0);
      } catch (e) {}
    })();
""".trimIndent()

private val CASINO_UNMUTE_JS = """
    (function(){
      try {
        window.__casinoParkMuted = false;
        document.querySelectorAll('audio,video').forEach(function(el){
          try { el.muted = false; el.volume = 1; } catch (e) {}
        });
        if (window.Howler && typeof Howler.mute === 'function') Howler.mute(false);
        if (window.Howler && typeof Howler.volume === 'function') Howler.volume(1);
      } catch (e) {}
    })();
""".trimIndent()
