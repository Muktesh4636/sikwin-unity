package com.sikwin.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens [url] in the user’s default browser (or app chooser).
 * Use for hosted WebGL: deploy the build on your server, point [Constants.WEBGL_GAME_URL] at that page.
 */
fun Context.openInDefaultBrowser(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
