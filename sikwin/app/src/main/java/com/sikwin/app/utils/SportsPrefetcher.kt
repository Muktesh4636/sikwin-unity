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
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parks a Sports WebView off-screen from APK/home open so LIVE / Cricket / Soccer / Tennis
 * attach instantly (phone cache — no CDN required).
 */
object SportsPrefetcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var holder: FrameLayout? = null
    private var webView: WebView? = null
    private var loadedAccess: String? = null
    private var loadedRefresh: String? = null
    private var loadedSport: String? = null
    private var loadedMode: String? = null
    @Volatile private var pageReady: Boolean = false
    @Volatile private var networkError: Boolean = false
    @Volatile private var myBetsOpen: Boolean = false
    /** After hub load, drop warm/reload history so swipe-back exits Sports. */
    @Volatile private var clearHistoryAfterHubLoad: Boolean = false
    private var feedPollRunnable: Runnable? = null
    private val readyListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private val networkErrorListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private const val FEED_POLL_JS = """
        (function(){
          var f = document.getElementById('feed');
          if (f) {
            if (f.querySelector('.card') || f.querySelector('.empty') || f.querySelector('.error')) return 'done';
            if (!f.querySelector('.loading')) return 'done';
            return 'loading';
          }
          // /cricket/ has no #feed — treat painted match UI (or no spinner) as ready
          if (document.querySelector('.card') || document.querySelector('.empty') ||
              document.querySelector('.error') || document.querySelector('.match-card') ||
              document.querySelector('[data-event-id]')) return 'done';
          if (!document.querySelector('.loading')) return 'done';
          return 'loading';
        })();
    """

    fun isReady(accessToken: String?, sport: String?): Boolean {
        val wv = webView ?: return false
        if (networkError) return false
        if (!pageReady) return false
        val wantAccess = accessToken.orEmpty()
        val haveAccess = loadedAccess.orEmpty()
        if (wantAccess != haveAccess) return false
        val url = wv.url.orEmpty()
        if (WebViewOffline.isChromeErrorUrl(url)) return false
        if (!url.contains("/sports") && !url.contains("/cricket")) return false
        val want = normalizeSport(sport)
        if (want.isNullOrBlank()) return true
        return when (want) {
            "cricket" -> url.contains("/cricket") || url.contains("sport=cricket")
            else -> url.contains("sport=$want") || url.contains("/sports")
        }
    }

    fun hasNetworkError(): Boolean = networkError

    /** True when the WebView is already on the sports/cricket hub (even if feed still loading). */
    fun hasHubUrl(): Boolean {
        val url = webView?.url.orEmpty()
        if (url.isBlank() || WebViewOffline.isChromeErrorUrl(url)) return false
        return url.contains("/sports") || url.contains("/cricket")
    }

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

    fun retry(accessToken: String?, refreshToken: String?, sport: String?, mode: String? = null) {
        mainHandler.post {
            networkError = false
            notifyNetworkError(false)
            clearHistoryAfterHubLoad = true
            loadSports(accessToken, refreshToken, sport, mode, force = true)
        }
    }

    fun warm(
        context: Context?,
        accessToken: String?,
        refreshToken: String? = null,
        sport: String? = null,
        mode: String? = null
    ) {
        val activity = resolveActivity(context) ?: return
        activityRef = WeakReference(activity)
        mainHandler.post {
            try {
                ensureWebView(activity)
                webView?.let { wv ->
                    try {
                        wv.onResume()
                    } catch (_: Exception) {
                    }
                }
                loadSports(accessToken, refreshToken, sport, mode)
            } catch (_: Exception) {
            }
        }
    }

    fun attach(
        parent: ViewGroup,
        accessToken: String?,
        refreshToken: String?,
        sport: String?,
        mode: String? = null,
        onBack: () -> Unit
    ): Boolean {
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
        wv.alpha = if (networkError || !isReady(accessToken, sport)) 0f else 1f
        if (!accessToken.isNullOrBlank()) {
            wv.evaluateJavascript(buildInjectJs(accessToken, refreshToken.orEmpty()), null)
        }

        val needsReload = loadedAccess != accessToken ||
            sportChanged(sport) ||
            modeChanged(mode) ||
            !pageReady ||
            networkError ||
            WebViewOffline.isChromeErrorUrl(wv.url.orEmpty())
        if (needsReload) {
            clearHistoryAfterHubLoad = true
        }
        loadSports(accessToken, refreshToken, sport, mode, force = needsReload)
        return isReady(accessToken, sport)
    }

    /** Switch Live / Upcoming / My Bets without leaving Sports screen. */
    fun navigateMode(accessToken: String?, refreshToken: String?, sport: String?, mode: String?) {
        mainHandler.post {
            val wv = webView ?: run {
                loadSports(accessToken, refreshToken, sport, mode, force = true)
                return@post
            }
            val m = normalizeMode(mode)
            loadedMode = m
            if (m == "bets") {
                showMyBetsOverlay(wv)
                return@post
            }
            myBetsOpen = false
            // Leave any My Bets overlay, then soft-switch Live/Upcoming on sports hub
            wv.evaluateJavascript(
                """
                (function(){
                  try {
                    var panel = document.getElementById('apk-my-bets');
                    if (panel) panel.remove();
                    var btn = document.querySelector('.mode-row button[data-mode="$m"]');
                    if (btn) { btn.click(); return 'ok'; }
                  } catch (e) {}
                  return 'reload';
                })();
                """.trimIndent()
            ) { result ->
                val ok = result?.contains("ok") == true
                if (!ok) {
                    clearHistoryAfterHubLoad = true
                    loadSports(accessToken, refreshToken, sport, m, force = true)
                }
            }
        }
    }

    private fun showMyBetsOverlay(wv: WebView) {
        myBetsOpen = true
        wv.evaluateJavascript(
            """
            (async function(){
              try {
                var old = document.getElementById('apk-my-bets');
                if (old) old.remove();
                var panel = document.createElement('div');
                panel.id = 'apk-my-bets';
                panel.style.cssText = 'position:fixed;inset:0;z-index:99999;background:#0b0f14;overflow:auto;padding:16px 14px 90px;font-family:Segoe UI,Roboto,sans-serif;';
                panel.innerHTML = '<h2 style="color:#d4af37;margin:0 0 14px;font-size:22px;">My Bets</h2><div id="apk-bets-list" style="color:#8b95a5;">Loading…</div>';
                document.body.appendChild(panel);
                var token = localStorage.getItem('accessToken') || localStorage.getItem('access_token') || localStorage.getItem('gundu_access_token') || '';
                var r = await fetch('/api/cricket/bets/', {
                  headers: { 'Authorization': 'Bearer ' + token, 'Accept': 'application/json' }
                });
                var list = document.getElementById('apk-bets-list');
                if (!r.ok) {
                  list.innerHTML = '<div style="padding:24px;text-align:center;">Could not load bets (' + r.status + ').</div>';
                  return;
                }
                var data = await r.json();
                var bets = Array.isArray(data) ? data : (data.results || data.bets || data.data || []);
                if (!bets.length) {
                  list.innerHTML = '<div style="padding:40px 12px;text-align:center;">No bets yet.</div>';
                  return;
                }
                list.innerHTML = bets.map(function(b){
                  var stake = b.stake != null ? b.stake : (b.amount != null ? b.amount : '—');
                  var status = b.status || b.state || '';
                  var title = b.market_name || b.selection || b.outcome_name || b.match_name || ('Bet #' + (b.id || ''));
                  var sub = b.match_name || b.event_name || b.sport || '';
                  var pnl = b.payout != null ? b.payout : (b.potential_win != null ? b.potential_win : '');
                  return '<div style="background:#141a22;border:1px solid #243041;border-radius:14px;padding:12px;margin-bottom:10px;">' +
                    '<div style="color:#f3f5f7;font-weight:700;font-size:14px;">' + String(title) + '</div>' +
                    (sub ? '<div style="color:#8b95a5;font-size:12px;margin-top:4px;">' + String(sub) + '</div>' : '') +
                    '<div style="display:flex;justify-content:space-between;margin-top:10px;font-size:13px;font-weight:700;">' +
                      '<span style="color:#d4af37;">₹' + String(stake) + '</span>' +
                      '<span style="color:#cfd6e0;">' + String(status) + (pnl !== '' ? ' · ₹' + String(pnl) : '') + '</span>' +
                    '</div></div>';
                }).join('');
              } catch (e) {
                var list2 = document.getElementById('apk-bets-list');
                if (list2) list2.innerHTML = '<div style="padding:24px;text-align:center;color:#ef9a9a;">Failed to load bets.</div>';
              }
            })();
            """.trimIndent(),
            null
        )
    }

    fun prepareLeave() {
        val wv = webView ?: return
        cancelFeedPoll()
        myBetsOpen = false
        try {
            wv.alpha = 0f
            wv.visibility = android.view.View.INVISIBLE
        } catch (_: Exception) {
        }
    }

    /** Pause sports JS when opening Casino so shared Chromium isn't contended. */
    fun haltForOtherWebGame() {
        mainHandler.post {
            cancelFeedPoll()
            try {
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
                webView?.onPause()
            } catch (_: Exception) {
            }
            pageReady = false
            setReady(false)
            loadedAccess = null
        }
    }

    /**
     * Android / gesture back:
     * - My Bets overlay → close overlay
     * - Match detail → WebView.goBack()
     * - Sports hub → leave screen (never walk warm/reload history — that caused the glitch)
     */
    fun handleBack(onClose: () -> Unit) {
        val wv = webView
        if (wv == null) {
            onClose()
            return
        }
        if (myBetsOpen) {
            myBetsOpen = false
            loadedMode = "live"
            wv.evaluateJavascript(
                """
                (function(){
                  var panel = document.getElementById('apk-my-bets');
                  if (panel) panel.remove();
                })();
                """.trimIndent(),
                null
            )
            return
        }
        val url = wv.url.orEmpty().lowercase()
        val inMatchDetail = url.contains("view=match") ||
            url.contains("/sports/match") ||
            (url.contains("event_id=") && !url.contains("/sports/?") && !url.endsWith("/sports/"))
        if (inMatchDetail && wv.canGoBack()) {
            wv.goBack()
            return
        }
        prepareLeave()
        onClose()
    }

    fun detach() {
        val wv = webView ?: return
        val h = holder ?: return
        cancelFeedPoll()
        try {
            wv.alpha = 0f
            wv.visibility = android.view.View.GONE
            // Keep WebView resumed while feed is still loading — onPause stops fetch/JS.
            if (pageReady) {
                wv.onPause()
            }
        } catch (_: Exception) {
        }
        try {
            (wv.parent as? ViewGroup)?.removeView(wv)
            if (wv.parent == null) {
                h.addView(wv, FrameLayout.LayoutParams(1, 1))
            }
            h.visibility = android.view.View.GONE
        } catch (_: Exception) {
        }
    }

    fun clear() {
        mainHandler.post {
            cancelFeedPoll()
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
            loadedSport = null
            loadedMode = null
            pageReady = false
            networkError = false
            setReady(false)
        }
    }

    private fun sportChanged(sport: String?): Boolean {
        val want = normalizeSport(sport)
        val have = normalizeSport(loadedSport)
        if (want.isNullOrBlank()) return false
        return want != have
    }

    private fun modeChanged(mode: String?): Boolean {
        val want = normalizeMode(mode)
        val have = normalizeMode(loadedMode)
        return want != have
    }

    private fun normalizeSport(sport: String?): String? {
        val s = sport?.trim()?.lowercase().orEmpty()
        return when (s) {
            "cricket", "soccer", "tennis" -> s
            "football" -> "soccer"
            else -> null
        }
    }

    /** live | upcoming | bets */
    private fun normalizeMode(mode: String?): String {
        return when (mode?.trim()?.lowercase()) {
            "upcoming", "pre", "prematch" -> "upcoming"
            "bets", "mybets", "my_bets", "my-bets" -> "bets"
            else -> "live"
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
        cancelFeedPoll()
        WebViewOffline.hideChromeErrorPage(view)
        setReady(false)
        notifyNetworkError(true)
    }

    private fun cancelFeedPoll() {
        feedPollRunnable?.let { mainHandler.removeCallbacks(it) }
        feedPollRunnable = null
    }

    /** Wait until match cards (or empty/error) paint — not just HTML shell. */
    private fun waitForSportsFeed(view: WebView?, attempt: Int = 0) {
        cancelFeedPoll()
        if (view == null) {
            setReady(true)
            return
        }
        val runnable = Runnable {
            try {
                view.evaluateJavascript(FEED_POLL_JS) { result ->
                    val done = result?.contains("done") == true || attempt >= 50
                    if (done) {
                        feedPollRunnable = null
                        networkError = false
                        notifyNetworkError(false)
                        view.alpha = 1f
                        setReady(true)
                    } else if (attempt >= 50) {
                        // Show hub even if cards are slow — matches browser "Loading…" state.
                        feedPollRunnable = null
                        view.alpha = 1f
                        setReady(true)
                    } else {
                        waitForSportsFeed(view, attempt + 1)
                    }
                }
            } catch (_: Exception) {
                view.alpha = 1f
                setReady(true)
            }
        }
        feedPollRunnable = runnable
        mainHandler.postDelayed(runnable, if (attempt == 0) 120L else 200L)
    }

    /**
     * Hub URL like casino:
     * - Live lobby → [Constants.SPORTS_URL]
     * - Cricket → [Constants.CRICKET_URL]
     * - Soccer/tennis → /sports/?sport=…
     * Auth: ?token=&refresh= (same as casino/games). Refresh is omitted from the
     * query when tokens are long — WebView silently fails oversized URLs; refresh
     * is still injected into localStorage on page finish.
     */
    private fun buildUrl(
        accessToken: String?,
        refreshToken: String?,
        sport: String?,
        mode: String?
    ): String {
        val s = normalizeSport(sport)
        val base = when (s) {
            "cricket" -> Constants.CRICKET_URL
            else -> Constants.SPORTS_URL
        }
        val b = Uri.parse(base).buildUpon()
        if (s != null && s != "cricket") {
            b.appendQueryParameter("sport", s)
        }
        val m = normalizeMode(mode)
        if (base == Constants.SPORTS_URL) {
            when (m) {
                "upcoming" -> b.appendQueryParameter("mode", "upcoming")
                "live" -> b.appendQueryParameter("mode", "live")
            }
        }
        appendAuthQuery(b, accessToken, refreshToken, allowRefreshInQuery = true)
        return b.build().toString()
    }

    /**
     * Casino-style: always put `token` when present.
     * Also put `refresh` when both JWTs fit a safe WebView URL budget.
     */
    private fun appendAuthQuery(
        builder: Uri.Builder,
        accessToken: String?,
        refreshToken: String?,
        allowRefreshInQuery: Boolean = true
    ) {
        if (accessToken.isNullOrBlank()) return
        builder.appendQueryParameter("token", accessToken)
        if (!allowRefreshInQuery || refreshToken.isNullOrBlank()) return
        // Dual JWTs in the query regularly exceed WebView URL limits → blank page.
        val projected = (accessToken.length + refreshToken.length)
        if (projected <= 1800) {
            builder.appendQueryParameter("refresh", refreshToken)
        }
    }

    /** Keep JWT on same-origin sports/cricket navigations (match detail, etc.). */
    private fun withAuthIfNeeded(url: String): String {
        if (url.isBlank()) return url
        if (!url.contains("gunduata.tech") && !url.startsWith("/")) return url
        val lower = url.lowercase()
        val sportsPath = lower.contains("/sports") || lower.contains("/cricket")
        if (!sportsPath) return url
        if (lower.contains("token=")) return url
        val access = loadedAccess
        if (access.isNullOrBlank()) return url
        val absolute = if (url.startsWith("/")) {
            Constants.WEB_ORIGIN + url
        } else {
            url
        }
        return try {
            val b = Uri.parse(absolute).buildUpon()
            // Match pages: token only (casino pattern) — keeps URL short
            appendAuthQuery(b, access, loadedRefresh, allowRefreshInQuery = false)
            b.build().toString()
        } catch (_: Exception) {
            absolute
        }
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
        val bearerLit = JSONObject.quote("Bearer $accessToken")
        return """
            (function(){
              try {
                localStorage.setItem("token", $accessLit);
                localStorage.setItem("accessToken", $accessLit);
                localStorage.setItem("access_token", $accessLit);
                localStorage.setItem("gundu_access_token", $accessLit);
                localStorage.setItem("refresh", $refreshLit);
                localStorage.setItem("refreshToken", $refreshLit);
                localStorage.setItem("refresh_token", $refreshLit);
                localStorage.setItem("auth", $authLit);
                localStorage.setItem("kokoroko_auth", $authLit);
                sessionStorage.setItem("token", $accessLit);
                sessionStorage.setItem("accessToken", $accessLit);
                sessionStorage.setItem("refreshToken", $refreshLit);
                localStorage.setItem("Authorization", $bearerLit);
              } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun buildFeedNudgeJs(): String {
        return """
            (function(){
              try {
                var f = document.getElementById('feed');
                if (!f || f.querySelector('.card') || f.querySelector('.empty') || f.querySelector('.error')) return;
                var btn = document.querySelector('.sport-tab.active');
                if (btn) btn.click();
              } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun loadSports(
        accessToken: String?,
        refreshToken: String?,
        sport: String?,
        mode: String? = null,
        force: Boolean = false
    ) {
        val wv = webView ?: return
        val ctx = activityRef?.get()
        if (ctx != null && !NetworkUtils.isOnline(ctx)) {
            markOffline(wv)
            return
        }
        val url = buildUrl(accessToken, refreshToken, sport, mode)
        if (!force &&
            loadedAccess == accessToken &&
            pageReady &&
            !sportChanged(sport) &&
            !modeChanged(mode) &&
            wv.url.orEmpty().let { it.contains("/sports") || it.contains("/cricket") }
        ) {
            setReady(true)
            return
        }
        loadedAccess = accessToken
        loadedRefresh = refreshToken
        loadedSport = normalizeSport(sport)
        loadedMode = normalizeMode(mode)
        networkError = false
        notifyNetworkError(false)
        // Keep spinner off if hub is already on screen (matches visible via prior paint)
        val alreadyShowing = wv.url.orEmpty().let {
            (it.contains("/sports") || it.contains("/cricket")) && !WebViewOffline.isChromeErrorUrl(it)
        }
        if (!alreadyShowing) {
            setReady(false)
        }
        try {
            if (!pageReady) {
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

        val defaultUa = WebSettings.getDefaultUserAgent(activity)
        val wv = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(1, 1)
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            settings.userAgentString = "$defaultUa GunduAtaApp/1.0"
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

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val raw = request?.url?.toString().orEmpty()
                    if (raw.isBlank()) return false
                    val withAuth = withAuthIfNeeded(raw)
                    if (withAuth != raw && view != null) {
                        view.loadUrl(withAuth)
                        return true
                    }
                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val raw = url.orEmpty()
                    if (raw.isBlank()) return false
                    val withAuth = withAuthIfNeeded(raw)
                    if (withAuth != raw && view != null) {
                        view.loadUrl(withAuth)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    val u = url.orEmpty()
                    if (u.contains("/sports") || u.contains("/cricket")) {
                        // Soft hide while new hub loads — do not clear pageReady yet so
                        // Compose does not flash a false "no internet" overlay.
                        try {
                            if (!pageReady) view?.alpha = 0f
                        } catch (_: Exception) {
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (WebViewOffline.isChromeErrorUrl(url)) {
                        markOffline(view)
                        return
                    }
                    val u = url.orEmpty()
                    if (u.contains("/sports") || u.contains("/cricket")) {
                        val access = loadedAccess
                        if (!access.isNullOrBlank()) {
                            view?.evaluateJavascript(
                                buildInjectJs(access, loadedRefresh.orEmpty()),
                                null
                            )
                        }
                        view?.evaluateJavascript(buildFeedNudgeJs(), null)
                        networkError = false
                        notifyNetworkError(false)
                        // Show shell immediately so match list can paint; feed poll refines readiness.
                        try {
                            view?.alpha = 1f
                        } catch (_: Exception) {
                        }
                        setReady(true)
                        val hub = !u.contains("view=match") &&
                            !u.contains("/sports/match") &&
                            !u.contains("view=match")
                        if (hub && clearHistoryAfterHubLoad) {
                            clearHistoryAfterHubLoad = false
                            try {
                                view?.clearHistory()
                            } catch (_: Exception) {
                            }
                        }
                        waitForSportsFeed(view)
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
                    val main = view?.url.orEmpty()
                    if (!failingUrl.isNullOrBlank() &&
                        main.isNotBlank() &&
                        failingUrl.equals(main, ignoreCase = true)
                    ) {
                        markOffline(view)
                    }
                }
            }
        }
        webView = wv
        holder?.addView(wv)
    }
}
