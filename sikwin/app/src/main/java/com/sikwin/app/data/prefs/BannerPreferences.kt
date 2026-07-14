package com.sikwin.app.data.prefs

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class BannerPreferences(private val context: Context) {

    companion object {
        private const val FILE_LIVE_CASINO = "live_casino_banner.jpg"
    }

    private fun bannerFile(): File = File(context.filesDir, FILE_LIVE_CASINO)

    fun hasCustomLiveCasinoBanner(): Boolean = bannerFile().exists() && bannerFile().length() > 0

    fun getLiveCasinoBannerFile(): File? =
        bannerFile().takeIf { it.exists() && it.length() > 0 }

    /** Copy picked gallery image into app storage. Returns true on success. */
    fun saveLiveCasinoBanner(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(bannerFile()).use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearLiveCasinoBanner() {
        bannerFile().delete()
    }
}
