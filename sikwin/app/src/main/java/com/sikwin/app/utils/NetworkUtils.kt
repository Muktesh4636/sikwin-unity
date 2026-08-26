package com.sikwin.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.delay

/** Fast offline checks for WebView game screens. */
object NetworkUtils {
    const val OFFLINE_UI_MS = 3_000L
    const val LOAD_TIMEOUT_MS = 20_000L
    const val PREFETCH_CONNECT_SEC = 3L

    fun isOnline(context: Context?): Boolean {
        if (context == null) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    /** Wait for ready, or fail fast when offline (~3s) instead of a 30s spinner. */
    suspend fun awaitReadyOrOffline(
        context: Context,
        offlineMs: Long = OFFLINE_UI_MS,
        loadMs: Long = LOAD_TIMEOUT_MS,
        isReady: () -> Boolean,
        hasError: () -> Boolean
    ): Boolean {
        if (!isOnline(context)) return false
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < loadMs) {
            if (hasError()) return false
            if (isReady()) return true
            val elapsed = System.currentTimeMillis() - start
            if (!isOnline(context) && elapsed >= offlineMs) return false
            delay(80)
        }
        return isReady()
    }
}
