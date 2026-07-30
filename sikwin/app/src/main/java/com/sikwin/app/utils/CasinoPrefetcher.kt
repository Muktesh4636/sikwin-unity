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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var activityRef: WeakReference<Activity>? = null
    private var holder: FrameLayout? = null
    private var webView: WebView? = null
    private var loadedToken: String? = null
    @Volatile private var pageReady: Boolean = false
    private val assetsStarted = AtomicBoolean(false)
    private val readyListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private var onBack: (() -> Unit)? = null
    private var onOpenGame: ((String, String) -> Unit)? = null

    fun isReady(token: String?): Boolean {
        val wv = webView ?: return false
        if (!pageReady) return false
        if (!token.isNullOrBlank() && loadedToken != token) return false
        val url = wv.url.orEmpty()
        return url.contains("/casino")
    }

    fun addReadyListener(listener: (Boolean) -> Unit) {
        readyListeners.add(listener)
        listener(isReady(loadedToken))
    }

    fun removeReadyListener(listener: (Boolean) -> Unit) {
        readyListeners.remove(listener)
    }

    /** Call as soon as the APK opens (and again when the JWT changes). */
    fun warm(context: Context?, token: String? = null) {
        val activity = resolveActivity(context) ?: return
        activityRef = WeakReference(activity)
        warmHttpAssets()
        mainHandler.post {
            try {
                ensureWebView(activity)
                loadCasino(token)
            } catch (_: Exception) {
                /* soft */
            }
        }
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
        } catch (_: Exception) {
        }
        wv.visibility = android.view.View.VISIBLE
        wv.alpha = 1f

        if (loadedToken != token || !pageReady || !wv.url.orEmpty().contains("/casino") ||
            looksLikeInnerGame(wv.url.orEmpty())
        ) {
            loadCasino(token)
        }
        return isReady(token)
    }

    /** Hide WebView before navigating away so home does not flicker. */
    fun prepareLeave() {
        val wv = webView ?: return
        try {
            wv.alpha = 0f
            wv.visibility = android.view.View.INVISIBLE
        } catch (_: Exception) {
        }
    }

    /** Park the WebView off-screen so the next Casino open is still instant. */
    fun detach() {
        onBack = null
        onOpenGame = null
        val wv = webView ?: return
        val h = holder ?: return
        val url = wv.url.orEmpty()

        // Hide first — removing a visible WebView over home causes flicker.
        try {
            wv.alpha = 0f
            wv.visibility = android.view.View.GONE
            wv.onPause()
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

        // Restore lobby in background if user opened an inner game from Casino.
        if (!url.contains("/casino") || looksLikeInnerGame(url)) {
            mainHandler.post { loadCasino(loadedToken) }
        }
    }

    fun clear() {
        mainHandler.post {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (_: Exception) {
            }
            webView = null
            holder = null
            loadedToken = null
            setReady(false)
        }
    }

    private fun looksLikeInnerGame(url: String): Boolean {
        if (!url.contains("gunduata.tech")) return false
        return url.contains("/roulette") ||
            url.contains("/trading") ||
            url.contains("/chicken-road") ||
            url.contains("/vortex") ||
            url.contains("/game")
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

    private fun buildUrl(token: String?): String {
        val builder = Uri.parse(Constants.CASINO_URL).buildUpon()
        if (!token.isNullOrBlank()) builder.appendQueryParameter("token", token)
        return builder.build().toString()
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

    private fun loadCasino(token: String?) {
        val wv = webView ?: return
        val url = buildUrl(token)
        // Same token + already on casino lobby + ready → nothing to do
        if (loadedToken == token && pageReady && wv.url.orEmpty().contains("/casino") &&
            !looksLikeInnerGame(wv.url.orEmpty())
        ) {
            setReady(true)
            return
        }
        loadedToken = token
        setReady(false)
        try {
            wv.loadUrl(url)
        } catch (_: Exception) {
            setReady(false)
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
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
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

                override fun onPageFinished(view: WebView?, url: String?) {
                    val u = url.orEmpty()
                    if (u.contains("/casino") && !looksLikeInnerGame(u)) {
                        setReady(true)
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
            mainHandler.post {
                val wv = webView
                val url = wv?.url.orEmpty()
                // Only use WebView history for inner games; casino lobby exits the screen.
                if (wv != null && looksLikeInnerGame(url) && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    prepareLeave()
                    onBack?.invoke()
                }
            }
        }

        @JavascriptInterface
        fun openGame(id: String, url: String) {
            mainHandler.post {
                try {
                    webView?.loadUrl(url)
                } catch (_: Exception) {
                }
                onOpenGame?.invoke(id, url)
            }
        }
    }

    private fun warmHttpAssets() {
        if (!assetsStarted.compareAndSet(false, true)) return
        scope.launch {
            try {
                prefetchCasinoAssets()
            } catch (_: Exception) {
                /* soft */
            }
        }
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
