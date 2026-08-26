package com.sikwin.app.utils

import android.content.Context

/**
 * Previously warmed Sports / game assets on APK open.
 * Disabled — do not prefetch any game in the background.
 */
object GameWarmCache {
    @Suppress("UNUSED_PARAMETER")
    fun warm(context: Context?, accessToken: String? = null) {
        // no-op: no game prefetch
    }
}
