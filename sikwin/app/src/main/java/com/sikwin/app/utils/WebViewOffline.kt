package com.sikwin.app.utils

import android.webkit.WebResourceRequest
import android.webkit.WebView

/**
 * Helpers so web games never leave the user on Chrome's "Webpage not available".
 */
object WebViewOffline {
    fun isChromeErrorUrl(url: String?): Boolean {
        val u = url.orEmpty().lowercase()
        if (u.isBlank()) return false
        return u.startsWith("chrome-error://") ||
            u.contains("net::err_") ||
            (u.startsWith("data:") && u.contains("webpage not available"))
    }

    fun isMainFrameError(request: WebResourceRequest?): Boolean =
        request == null || request.isForMainFrame

    /** Blank + hide so Chrome's offline HTML is never visible under our overlay. */
    fun hideChromeErrorPage(view: WebView?) {
        if (view == null) return
        try {
            view.stopLoading()
            view.loadUrl("about:blank")
            view.alpha = 0f
        } catch (_: Exception) {
        }
    }
}
