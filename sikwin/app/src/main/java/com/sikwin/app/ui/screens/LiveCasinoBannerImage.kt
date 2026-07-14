package com.sikwin.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sikwin.app.data.prefs.BannerPreferences

/**
 * Shows custom Live Casino banner from device if set, otherwise [defaultResId].
 */
@Composable
fun LiveCasinoBannerImage(
    defaultResId: Int,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val context = LocalContext.current
    val bannerPrefs = remember { BannerPreferences(context) }
    val customFile = bannerPrefs.getLiveCasinoBannerFile()

    if (customFile != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(customFile)
                .crossfade(true)
                .build(),
            contentDescription = "Live Casino banner",
            contentScale = contentScale,
            alignment = alignment,
            modifier = modifier
        )
    } else {
        Image(
            painter = painterResource(id = defaultResId),
            contentDescription = "Live Casino banner",
            contentScale = contentScale,
            alignment = alignment,
            modifier = modifier
        )
    }
}
