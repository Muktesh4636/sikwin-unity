package com.sikwin.app.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.sikwin.app.data.prefs.ThemePreferences
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

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
    /** Ignore rapid same-mode tab taps that re-click the hub / force-reload. */
    private var lastNavigateMode: String? = null
    private var lastNavigateAtMs: Long = 0L
    /** 0–100 WebView load progress for the cricket/sports boot overlay. */
    @Volatile private var loadProgress: Int = 0
    /**
     * Keep WebView hidden until Compose finishes the 100% bar and calls [revealAfterBoot].
     * Prevents empty black screen when a reload sets alpha=0 after the overlay was dismissed.
     */
    @Volatile private var waitingForOverlayReveal: Boolean = false
    /** True while Sports screen has the WebView in its Compose hierarchy. */
    @Volatile private var attachedVisible: Boolean = false
    /** After hub load, drop warm/reload history so swipe-back exits Sports. */
    @Volatile private var clearHistoryAfterHubLoad: Boolean = false
    private var feedPollRunnable: Runnable? = null
    private val readyListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private val networkErrorListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private val progressListeners = CopyOnWriteArrayList<(Int) -> Unit>()
    private val themeListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val themeBridgeAttached = AtomicBoolean(false)

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

    /** True while Sports LIVE screen has the WebView attached (Compose visible). */
    fun isAttachedVisible(): Boolean = attachedVisible

    /** 0–100 progress while the hub boots under the loading overlay. */
    fun getLoadProgress(): Int = loadProgress.coerceIn(0, 100)

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

    fun addProgressListener(listener: (Int) -> Unit) {
        progressListeners.add(listener)
        try {
            listener(getLoadProgress())
        } catch (_: Exception) {
        }
    }

    fun removeProgressListener(listener: (Int) -> Unit) {
        progressListeners.remove(listener)
    }

    fun addThemeListener(listener: (String) -> Unit) {
        themeListeners.add(listener)
    }

    fun removeThemeListener(listener: (String) -> Unit) {
        themeListeners.remove(listener)
    }

    /**
     * Called from WebView when sports page toggles light/dark.
     * Persists app theme + notifies Compose (bottom bar).
     * Does NOT touch Casino WebView while LIVE is open — that froze Chromium.
     */
    fun notifyWebThemeChanged(theme: String) {
        val mode = if (theme == "light") "light" else "dark"
        mainHandler.post {
            var changed = false
            try {
                activityRef?.get()?.let { act ->
                    val prefs = ThemePreferences(act)
                    val want = if (mode == "light") {
                        ThemePreferences.THEME_WHITE
                    } else {
                        ThemePreferences.THEME_DUAL_CARDS
                    }
                    if (prefs.getAppTheme() != want) {
                        prefs.setAppTheme(want)
                        changed = true
                    }
                }
            } catch (_: Exception) {
            }
            themeListeners.forEach {
                try {
                    it(mode)
                } catch (_: Exception) {
                }
            }
            // Sync casino only after leave LIVE, and only when theme actually changed.
            if (changed && !attachedVisible) {
                try {
                    CasinoPrefetcher.applyAppTheme(activityRef?.get(), mode)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun retry(accessToken: String?, refreshToken: String?, sport: String?, mode: String? = null) {
        mainHandler.post {
            networkError = false
            notifyNetworkError(false)
            setLoadProgress(2, force = true)
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

    /**
     * Push app Black/White theme into sports/cricket WebView immediately.
     * Writes `gundu_sports_theme` and calls GunduSportsTheme.apply when present.
     */
    fun applyAppTheme(context: Context?, mode: String? = null) {
        val activity = resolveActivity(context) ?: return
        activityRef = WeakReference(activity)
        val theme = mode?.takeIf { it == "light" || it == "dark" }
            ?: ThemePreferences(activity).webThemeMode()
        mainHandler.post {
            try {
                ensureWebView(activity)
                val wv = webView ?: return@post
                try {
                    wv.onResume()
                } catch (_: Exception) {
                }
                wv.evaluateJavascript(buildThemeInjectJs(theme), null)
                val url = wv.url.orEmpty()
                val onHub = (url.contains("/sports") || url.contains("/cricket")) &&
                    !WebViewOffline.isChromeErrorUrl(url) &&
                    !url.equals("about:blank", ignoreCase = true)
                if (onHub) {
                    // Soft reload so CSS/theme scripts re-read storage cleanly.
                    wv.reload()
                }
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
        attachedVisible = true

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
        // While on LIVE screen, keep WebView opaque — Compose loader covers boot.
        // Alpha 0 + dismissed overlay was the intermittent black screen.
        wv.alpha = if (networkError) 0f else 1f
        waitingForOverlayReveal = false
        val blank = wv.url.isNullOrBlank() ||
            wv.url.equals("about:blank", ignoreCase = true) ||
            WebViewOffline.isChromeErrorUrl(wv.url.orEmpty())
        val needsReload = loadedAccess != accessToken ||
            sportChanged(sport) ||
            modeChanged(mode) ||
            !pageReady ||
            networkError ||
            blank
        if (needsReload) {
            clearHistoryAfterHubLoad = true
        }
        if (!accessToken.isNullOrBlank()) {
            wv.evaluateJavascript(buildInjectJs(accessToken, refreshToken.orEmpty()), null)
        }
        loadSports(accessToken, refreshToken, sport, mode, force = needsReload)
        return isReady(accessToken, sport)
    }

    /** Show the parked WebView after the Compose loading overlay hits 100%. */
    fun revealAfterBoot() {
        mainHandler.post {
            waitingForOverlayReveal = false
            forceVisibleIfAttached()
        }
    }

    /** Hard guarantee: attached LIVE WebView is never left at alpha 0. */
    fun forceVisibleIfAttached() {
        mainHandler.post {
            applyAttachedVisibility()
        }
    }

    private fun applyAttachedVisibility() {
        if (!attachedVisible || networkError) return
        val wv = webView ?: return
        try {
            wv.visibility = android.view.View.VISIBLE
            wv.alpha = 1f
            wv.onResume()
        } catch (_: Exception) {
        }
    }

    /** Switch Live / Upcoming / My Bets without leaving Sports screen. */
    fun navigateMode(accessToken: String?, refreshToken: String?, sport: String?, mode: String?) {
        mainHandler.post {
            val m = normalizeMode(mode)
            val now = SystemClock.uptimeMillis()
            // Same tab double-tap / spam — ignore (was re-clicking hub mode and glitching feed).
            if (m != "bets" &&
                m == lastNavigateMode &&
                now - lastNavigateAtMs < 700L &&
                !myBetsOpen
            ) {
                return@post
            }
            lastNavigateMode = m
            lastNavigateAtMs = now

            val wv = webView ?: run {
                loadSports(accessToken, refreshToken, sport, m, force = true)
                return@post
            }
            // Already on this tab with hub URL — do not reload or re-click mode button.
            if (m != "bets" && loadedMode == m && !myBetsOpen && hasHubUrl()) {
                applyAttachedVisibility()
                return@post
            }
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
                    if (btn) {
                      if (btn.classList && btn.classList.contains('active')) return 'ok';
                      btn.click();
                      return 'ok';
                    }
                    // No mode button (e.g. cricket) — stay on current hub without reload.
                    if (location.href.indexOf('/sports') >= 0 || location.href.indexOf('/cricket') >= 0) {
                      return 'ok';
                    }
                  } catch (e) {}
                  return 'reload';
                })();
                """.trimIndent()
            ) { result ->
                val ok = result?.contains("ok") == true
                if (!ok) {
                    // Hub already showing — never force full reload (that caused LIVE spam glitches).
                    if (hasHubUrl()) {
                        try {
                            wv.alpha = 1f
                            waitingForOverlayReveal = false
                            applyAttachedVisibility()
                        } catch (_: Exception) {
                        }
                        return@evaluateJavascript
                    }
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
        attachedVisible = false
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
            attachedVisible = false
            cancelFeedPoll()
            try {
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
                webView?.onPause()
            } catch (_: Exception) {
            }
            pageReady = false
            waitingForOverlayReveal = false
            setReady(false)
            setLoadProgress(0, force = true)
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
        attachedVisible = false
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
            attachedVisible = false
            waitingForOverlayReveal = false
            themeBridgeAttached.set(false)
            setLoadProgress(0, force = true)
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
        if (ready) {
            setLoadProgress(100)
        }
        readyListeners.forEach {
            try {
                it(ready)
            } catch (_: Exception) {
            }
        }
    }

    private fun setLoadProgress(progress: Int, force: Boolean = false) {
        val p = progress.coerceIn(0, 100)
        if (!force && p < loadProgress && p > 5) return
        loadProgress = p
        progressListeners.forEach {
            try {
                it(p)
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
        setLoadProgress(0, force = true)
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
        // Hub HTML is already up — don't leave the APK loader hanging on slow/hung feed JS.
        if (attempt == 0) {
            setLoadProgress(92)
        } else if (attempt % 2 == 0) {
            setLoadProgress((92 + attempt.coerceAtMost(12)).coerceAtMost(98))
        }
        // Force ready after ~4s even if feed poll JS never callbacks (WebView pause hang).
        if (attempt >= 20) {
            feedPollRunnable = null
            networkError = false
            notifyNetworkError(false)
            try {
                applyAttachedVisibility()
                if (!attachedVisible) {
                    view.alpha = if (waitingForOverlayReveal) 0f else 1f
                }
            } catch (_: Exception) {
            }
            setReady(true)
            return
        }
        lateinit var runnable: Runnable
        runnable = Runnable {
            // If evaluateJavascript never returns, advance poll anyway.
            val hangWatch = Runnable {
                if (feedPollRunnable === runnable) {
                    waitForSportsFeed(view, attempt + 1)
                }
            }
            mainHandler.postDelayed(hangWatch, 450L)
            try {
                view.evaluateJavascript(FEED_POLL_JS) { result ->
                    mainHandler.removeCallbacks(hangWatch)
                    if (feedPollRunnable !== runnable && feedPollRunnable != null) return@evaluateJavascript
                    val done = result?.contains("done") == true
                    if (done || attempt >= 20) {
                        feedPollRunnable = null
                        networkError = false
                        notifyNetworkError(false)
                        try {
                            applyAttachedVisibility()
                            if (!attachedVisible) {
                                view.alpha = if (waitingForOverlayReveal) 0f else 1f
                            }
                        } catch (_: Exception) {
                        }
                        setReady(true)
                    } else {
                        waitForSportsFeed(view, attempt + 1)
                    }
                }
            } catch (_: Exception) {
                mainHandler.removeCallbacks(hangWatch)
                try {
                    applyAttachedVisibility()
                    if (!attachedVisible) {
                        view.alpha = if (waitingForOverlayReveal) 0f else 1f
                    }
                } catch (_: Exception) {
                }
                setReady(true)
            }
        }
        feedPollRunnable = runnable
        mainHandler.postDelayed(runnable, if (attempt == 0) 80L else 180L)
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
        val theme = resolveWebThemeMode()
        val themeLit = JSONObject.quote(theme)
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
                localStorage.setItem("gundu_sports_theme", $themeLit);
                try {
                  if (window.GunduSportsTheme && typeof window.GunduSportsTheme.apply === "function") {
                    window.GunduSportsTheme.write($themeLit);
                    window.GunduSportsTheme.apply($themeLit);
                  } else {
                    document.documentElement.setAttribute("data-theme", $themeLit);
                    document.documentElement.classList.toggle("theme-light", $themeLit === "light");
                    document.documentElement.classList.toggle("theme-dark", $themeLit === "dark");
                  }
                } catch (te) {}
                try { ${THEME_BRIDGE_HOOK_JS} } catch (e2) {}
              } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun buildThemeInjectJs(theme: String): String {
        val themeLit = JSONObject.quote(if (theme == "light") "light" else "dark")
        return """
            (function(){
              try {
                localStorage.setItem("gundu_sports_theme", $themeLit);
                if (window.GunduSportsTheme && typeof window.GunduSportsTheme.apply === "function") {
                  window.GunduSportsTheme.write($themeLit);
                  window.GunduSportsTheme.apply($themeLit);
                } else {
                  document.documentElement.setAttribute("data-theme", $themeLit);
                  document.documentElement.classList.toggle("theme-light", $themeLit === "light");
                  document.documentElement.classList.toggle("theme-dark", $themeLit === "dark");
                }
              } catch (e) {}
              try { ${THEME_BRIDGE_HOOK_JS} } catch (e2) {}
            })();
        """.trimIndent()
    }

    /** Hook sports page theme toggle → Android bottom bar / prefs. */
    private val THEME_BRIDGE_HOOK_JS = """
      (function(){
        function notify(t){
          try {
            t = (t === 'light') ? 'light' : 'dark';
            if (window.__gunduLastThemeNotify === t) return;
            window.__gunduLastThemeNotify = t;
            if (window.GunduTheme && typeof window.GunduTheme.onThemeChanged === 'function') {
              window.GunduTheme.onThemeChanged(t);
            }
          } catch (e) {}
        }
        function hookApi(){
          var api = window.GunduSportsTheme;
          if (!api || api.__gunduBridged) return;
          api.__gunduBridged = true;
          var _toggle = api.toggle.bind(api);
          // Only bridge user toggle — do not wrap apply/write (APK inject + page boot
          // call those and were freezing LIVE by waking Casino WebView).
          api.toggle = function(){
            var r = _toggle();
            notify(r);
            return r;
          };
        }
        hookApi();
        if (!window.__gunduThemeClickHook) {
          window.__gunduThemeClickHook = true;
          document.addEventListener('click', function(e){
            var btn = e.target && e.target.closest && e.target.closest('[data-theme-toggle]');
            if (!btn) return;
            setTimeout(function(){
              try {
                var t = (window.GunduSportsTheme && window.GunduSportsTheme.read)
                  ? window.GunduSportsTheme.read()
                  : (document.documentElement.getAttribute('data-theme') || 'dark');
                notify(t);
              } catch (err) {}
            }, 40);
          }, true);
        }
      })();
    """.trimIndent()

    private fun resolveWebThemeMode(): String {
        val ctx = activityRef?.get() ?: return "dark"
        return try {
            ThemePreferences(ctx).webThemeMode()
        } catch (_: Exception) {
            "dark"
        }
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
            setLoadProgress(100)
            setReady(true)
            return
        }
        loadedAccess = accessToken
        loadedRefresh = refreshToken
        loadedSport = normalizeSport(sport)
        loadedMode = normalizeMode(mode)
        networkError = false
        notifyNetworkError(false)
        val blank = wv.url.isNullOrBlank() ||
            wv.url.equals("about:blank", ignoreCase = true) ||
            WebViewOffline.isChromeErrorUrl(wv.url.orEmpty())
        val alreadyShowing = !blank &&
            wv.url.orEmpty().let { it.contains("/sports") || it.contains("/cricket") }
        // Soft-reload under Compose loader — keep WebView opaque while attached (no black flash).
        if (force || blank || !alreadyShowing || !pageReady) {
            waitingForOverlayReveal = !attachedVisible
            setLoadProgress(2, force = true)
            setReady(false)
            if (!attachedVisible) {
                try {
                    wv.alpha = 0f
                } catch (_: Exception) {
                }
            } else {
                applyAttachedVisibility()
            }
        }
        try {
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

            if (themeBridgeAttached.compareAndSet(false, true)) {
                addJavascriptInterface(SportsThemeBridge(), "GunduTheme")
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (pageReady || networkError) return
                    if (newProgress in 1..99) {
                        // Cap under 90 until feed poll finishes — then Compose drives to 100%.
                        setLoadProgress((newProgress * 0.88f).toInt().coerceIn(3, 88))
                    }
                }
            }

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
                        setLoadProgress(5)
                        // Attached LIVE screen: stay opaque (Compose overlay covers boot).
                        // Parked warm load may stay hidden.
                        try {
                            if (attachedVisible) {
                                applyAttachedVisibility()
                            } else if (!pageReady) {
                                view?.alpha = 0f
                            }
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
                        } else {
                            view?.evaluateJavascript(buildThemeInjectJs(resolveWebThemeMode()), null)
                        }
                        // Always re-hook theme bridge after scripts load (GunduSportsTheme / toggle).
                        view?.evaluateJavascript(
                            """
                            (function(){ try { $THEME_BRIDGE_HOOK_JS } catch (e) {} })();
                            """.trimIndent(),
                            null
                        )
                        view?.evaluateJavascript(buildFeedNudgeJs(), null)
                        networkError = false
                        notifyNetworkError(false)
                        setLoadProgress(90)
                        try {
                            if (attachedVisible) {
                                applyAttachedVisibility()
                            } else {
                                view?.alpha = if (waitingForOverlayReveal) 0f else 1f
                            }
                        } catch (_: Exception) {
                        }
                        val hub = !u.contains("view=match") &&
                            !u.contains("/sports/match")
                        if (hub && clearHistoryAfterHubLoad) {
                            clearHistoryAfterHubLoad = false
                            try {
                                view?.clearHistory()
                            } catch (_: Exception) {
                            }
                        }
                        // Mark hub ready quickly so the loading bar can't sit mid-way forever.
                        // Feed poll still runs and will re-assert ready when cards paint.
                        if (hub) {
                            setReady(true)
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

    private class SportsThemeBridge {
        @JavascriptInterface
        fun onThemeChanged(theme: String?) {
            notifyWebThemeChanged(theme.orEmpty())
        }
    }
}
