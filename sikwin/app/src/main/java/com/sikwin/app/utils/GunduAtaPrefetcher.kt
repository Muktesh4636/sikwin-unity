package com.sikwin.app.utils

import android.annotation.SuppressLint
import android.app.Activity
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
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Parks Gundu Ata (virtual web) WebView from APK open so Virtual opens instantly.
 */
object GunduAtaPrefetcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var holder: FrameLayout? = null
    private var webView: WebView? = null
    private var loadedAccess: String? = null
    private var loadedRefresh: String? = null
    @Volatile private var pageReady: Boolean = false
    @Volatile private var networkError: Boolean = false
    /** True only while Virtual screen is showing the WebView. */
    @Volatile private var attachedVisible: Boolean = false
    /** Compose close — game JS AndroidBridge.goBack/goHome must use this, not load /. */
    @Volatile private var leaveHandler: (() -> Unit)? = null
    private val bridgeAttached = AtomicBoolean(false)
    private val readyListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private val networkErrorListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun isReady(accessToken: String?): Boolean {
        val wv = webView ?: return false
        if (networkError || !pageReady) return false
        if (!accessToken.isNullOrBlank() && loadedAccess != accessToken) return false
        val url = wv.url.orEmpty()
        if (WebViewOffline.isChromeErrorUrl(url)) return false
        // Only /game — site home (/) must never count as Virtual ready.
        return url.contains("/game")
    }

    fun hasNetworkError(): Boolean = networkError

    fun addReadyListener(listener: (Boolean) -> Unit) {
        readyListeners.add(listener)
        try {
            listener(pageReady && webView != null && !networkError)
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

    fun warm(context: Context?, accessToken: String?, refreshToken: String? = null) {
        val activity = resolveActivity(context) ?: return
        activityRef = WeakReference(activity)
        mainHandler.post {
            try {
                ensureWebView(activity)
                loadGame(accessToken, refreshToken)
            } catch (_: Exception) {
            }
        }
    }

    fun attach(
        parent: ViewGroup,
        accessToken: String,
        refreshToken: String,
        onLeave: (() -> Unit)? = null
    ): Boolean {
        val activity = resolveActivity(parent.context) ?: return false
        ensureWebView(activity)
        val wv = webView ?: return false
        leaveHandler = onLeave

        (wv.parent as? ViewGroup)?.removeView(wv)
        parent.addView(
            wv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        attachedVisible = true
        try {
            wv.onResume()
        } catch (_: Exception) {
        }
        wv.visibility = android.view.View.VISIBLE
        wv.alpha = if (networkError || !isReady(accessToken)) 0f else 1f
        wv.evaluateJavascript(buildInjectJs(accessToken, refreshToken), null)
        unmuteAudio(wv)

        loadGame(accessToken, refreshToken, force = true)
        return isReady(accessToken)
    }

    fun prepareLeave() {
        attachedVisible = false
        leaveHandler = null
        val wv = webView ?: return
        silenceParked(wv)
        try {
            wv.alpha = 0f
            wv.visibility = android.view.View.INVISIBLE
        } catch (_: Exception) {
        }
    }

    fun detach() {
        prepareLeave()
        val wv = webView ?: return
        val h = holder ?: return
        try {
            (wv.parent as? ViewGroup)?.removeView(wv)
            if (wv.parent == null) {
                h.addView(wv, FrameLayout.LayoutParams(1, 1))
            }
            h.visibility = android.view.View.GONE
        } catch (_: Exception) {
        }
    }

    /**
     * Hardware / gesture back: always leave Virtual.
     * Do not WebView.goBack() — game pushState/popstate loads https://gunduata.tech/ inside WebView.
     */
    fun handleBack(onClose: () -> Unit) {
        requestLeave(onClose)
    }

    private fun requestLeave(fallback: (() -> Unit)? = null) {
        mainHandler.post {
            val leave = leaveHandler
            // Capture before prepareLeave clears it.
            prepareLeave()
            try {
                leave?.invoke() ?: fallback?.invoke()
            } catch (_: Exception) {
            }
        }
    }

    private fun isLeaveAwayUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (!lower.contains("gunduata.tech")) return false
        if (lower.contains("/game")) return false
        return try {
            val path = Uri.parse(url).path.orEmpty().trimEnd('/').ifEmpty { "/" }
            path == "/" || path == "/index.html" || path == "/ga" ||
                path == "/casino" || path.startsWith("/casino/")
        } catch (_: Exception) {
            lower.endsWith("gunduata.tech/") || lower.contains("/casino")
        }
    }

    fun clear() {
        attachedVisible = false
        leaveHandler = null
        mainHandler.post {
            try {
                webView?.stopLoading()
                webView?.clearCache(true)
                webView?.destroy()
            } catch (_: Exception) {
            }
            webView = null
            holder = null
            loadedAccess = null
            loadedRefresh = null
            pageReady = false
            networkError = false
            bridgeAttached.set(false)
            setReady(false)
        }
    }

    private fun silenceParked(wv: WebView?) {
        if (wv == null) return
        try {
            wv.evaluateJavascript(GUNDU_ATA_MUTE_JS, null)
            wv.onPause()
        } catch (_: Exception) {
        }
    }

    private fun unmuteAudio(wv: WebView?) {
        if (wv == null) return
        try {
            wv.evaluateJavascript(GUNDU_ATA_UNMUTE_JS, null)
        } catch (_: Exception) {
        }
    }

    fun retry(accessToken: String?, refreshToken: String?) {
        mainHandler.post {
            networkError = false
            notifyNetworkError(false)
            loadGame(accessToken, refreshToken, force = true)
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

    private fun setReady(ready: Boolean) {
        pageReady = ready
        readyListeners.forEach {
            try {
                it(ready)
            } catch (_: Exception) {
            }
        }
    }

    private fun notifyNetworkError(error: Boolean) {
        networkError = error
        networkErrorListeners.forEach {
            try {
                it(error)
            } catch (_: Exception) {
            }
        }
    }

    private fun markOffline(view: WebView?) {
        WebViewOffline.hideChromeErrorPage(view)
        setReady(false)
        notifyNetworkError(true)
    }

    private fun buildUrl(accessToken: String?, refreshToken: String?): String {
        val authJson = JSONObject()
            .put("accessToken", accessToken.orEmpty())
            .put("refreshToken", refreshToken.orEmpty())
            .toString()
        val b = Uri.parse(Constants.GUNDU_ATA_WEB_URL).buildUpon()
        if (!accessToken.isNullOrBlank()) {
            b.appendQueryParameter("accessToken", accessToken)
            b.appendQueryParameter("token", accessToken)
        }
        if (!refreshToken.isNullOrBlank()) {
            b.appendQueryParameter("refreshToken", refreshToken)
        }
        // Tell game page to prefer AndroidBridge leave (not website /).
        b.appendQueryParameter("from", "app")
        b.appendQueryParameter("auth", authJson)
        b.appendQueryParameter("_", System.currentTimeMillis().toString())
        return b.build().toString()
    }

    private fun buildInjectJs(accessToken: String, refreshToken: String): String {
        val accessLit = JSONObject.quote(accessToken)
        val refreshLit = JSONObject.quote(refreshToken)
        val authLit = JSONObject.quote(
            JSONObject()
                .put("accessToken", accessToken)
                .put("refreshToken", refreshToken)
                .toString()
        )
        return """
            (function(){
              try {
                localStorage.setItem("accessToken", $accessLit);
                localStorage.setItem("access_token", $accessLit);
                localStorage.setItem("gundu_access_token", $accessLit);
                localStorage.setItem("refreshToken", $refreshLit);
                localStorage.setItem("refresh_token", $refreshLit);
                localStorage.setItem("auth", $authLit);
                try { sessionStorage.setItem("gundu_game_from", "app"); } catch (e2) {}
                try { document.body && document.body.classList.add("in-app"); } catch (e3) {}
              } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun loadGame(
        accessToken: String?,
        refreshToken: String?,
        force: Boolean = false
    ) {
        val wv = webView ?: return
        val ctx = activityRef?.get()
        if (ctx != null && !NetworkUtils.isOnline(ctx)) {
            markOffline(wv)
            return
        }
        val url = buildUrl(accessToken, refreshToken)
        if (!force &&
            loadedAccess == accessToken &&
            pageReady &&
            !networkError &&
            wv.url.orEmpty().contains("/game") &&
            !WebViewOffline.isChromeErrorUrl(wv.url)
        ) {
            setReady(true)
            if (!attachedVisible) silenceParked(wv)
            return
        }
        loadedAccess = accessToken
        loadedRefresh = refreshToken
        networkError = false
        notifyNetworkError(false)
        val alreadyShowing = wv.url.orEmpty().contains("/game") &&
            !WebViewOffline.isChromeErrorUrl(wv.url)
        if (!alreadyShowing) setReady(false)
        try {
            if (!accessToken.isNullOrBlank()) {
                wv.evaluateJavascript(buildInjectJs(accessToken, refreshToken.orEmpty()), null)
            }
            if (!pageReady || attachedVisible) {
                wv.alpha = 0f
            }
            wv.loadUrl(url)
        } catch (_: Exception) {
            markOffline(wv)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(activity: Activity) {
        if (holder == null) {
            holder = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(1, 1)
                visibility = android.view.View.GONE
            }
            activity.findViewById<ViewGroup>(android.R.id.content)?.addView(holder)
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
            settings.setSupportZoom(false)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            if (bridgeAttached.compareAndSet(false, true)) {
                // Game page: window.AndroidBridge.goBack / goHome
                addJavascriptInterface(GunduAtaBridge(), "AndroidBridge")
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val u = request?.url?.toString().orEmpty()
                    if (attachedVisible && isLeaveAwayUrl(u)) {
                        requestLeave()
                        return true
                    }
                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val u = url.orEmpty()
                    if (attachedVisible && isLeaveAwayUrl(u)) {
                        requestLeave()
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    val u = url.orEmpty()
                    if (attachedVisible && isLeaveAwayUrl(u)) {
                        requestLeave()
                        return
                    }
                    if (u.contains("/game")) {
                        setReady(false)
                        if (attachedVisible) {
                            try {
                                view?.alpha = 0f
                            } catch (_: Exception) {
                            }
                        }
                    }
                    if (!attachedVisible) {
                        view?.evaluateJavascript(GUNDU_ATA_MUTE_JS, null)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (WebViewOffline.isChromeErrorUrl(url)) {
                        markOffline(view)
                        return
                    }
                    val u = url.orEmpty()
                    if (attachedVisible && isLeaveAwayUrl(u)) {
                        requestLeave()
                        return
                    }
                    if (u.contains("/game")) {
                        loadedAccess?.let { access ->
                            view?.evaluateJavascript(
                                buildInjectJs(access, loadedRefresh.orEmpty()),
                                null
                            )
                        }
                        networkError = false
                        notifyNetworkError(false)
                        if (attachedVisible) {
                            view?.alpha = 1f
                            unmuteAudio(view)
                        } else {
                            silenceParked(view)
                        }
                        setReady(true)
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

    /**
     * Game page (/game/) calls AndroidBridge.goBack / goHome on HTML back.
     * Without this, JS does location.replace('/') → website home in WebView.
     */
    private class GunduAtaBridge {
        @JavascriptInterface
        fun goBack() {
            requestLeave()
        }

        @JavascriptInterface
        fun goHome() {
            requestLeave()
        }

        @JavascriptInterface
        fun isSystemBarsInsetApplied(): Boolean = true
    }
}

private val GUNDU_ATA_MUTE_JS = """
    (function(){
      try {
        window.__gunduAtaParkMuted = true;
        document.querySelectorAll('audio,video').forEach(function(el){
          try { el.muted = true; el.pause(); el.volume = 0; } catch (e) {}
        });
        if (window.Howler && typeof Howler.mute === 'function') Howler.mute(true);
        if (window.Howler && typeof Howler.volume === 'function') Howler.volume(0);
        try {
          if (typeof Module !== 'undefined' && Module.WEBAudio && Module.WEBAudio.audioContext) {
            Module.WEBAudio.audioContext.suspend();
          }
        } catch (e) {}
        try {
          if (window.unityInstance && unityInstance.Module && unityInstance.Module.WEBAudio) {
            unityInstance.Module.WEBAudio.audioContext.suspend();
          }
        } catch (e) {}
      } catch (e) {}
    })();
""".trimIndent()

private val GUNDU_ATA_UNMUTE_JS = """
    (function(){
      try {
        window.__gunduAtaParkMuted = false;
        document.querySelectorAll('audio,video').forEach(function(el){
          try { el.muted = false; el.volume = 1; } catch (e) {}
        });
        if (window.Howler && typeof Howler.mute === 'function') Howler.mute(false);
        if (window.Howler && typeof Howler.volume === 'function') Howler.volume(1);
        try {
          if (typeof Module !== 'undefined' && Module.WEBAudio && Module.WEBAudio.audioContext) {
            Module.WEBAudio.audioContext.resume();
          }
        } catch (e) {}
        try {
          if (window.unityInstance && unityInstance.Module && unityInstance.Module.WEBAudio) {
            unityInstance.Module.WEBAudio.audioContext.resume();
          }
        } catch (e) {}
      } catch (e) {}
    })();
""".trimIndent()
