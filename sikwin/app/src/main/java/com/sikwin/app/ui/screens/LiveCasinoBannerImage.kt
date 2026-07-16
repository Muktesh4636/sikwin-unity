package com.sikwin.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sikwin.app.data.prefs.BannerPreferences

/** Matches default `live_casino_banner.png` (854 x 450). */
const val LIVE_CASINO_BANNER_ASPECT_RATIO = 854f / 450f

/**
 * Shows custom Live Casino banner from device if set, otherwise [defaultResId].
 */
@Composable
fun LiveCasinoBannerImage(
    defaultResId: Int,
    contentScale: ContentScale = ContentScale.FillWidth,
    alignment: Alignment = Alignment.TopCenter,
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

/**
 * Banner image with a tappable hotspot only on the bottom-left PLAY NOW button area.
 */
@Composable
fun LiveCasinoBannerWithPlayNow(
    defaultResId: Int,
    onPlayNowClick: () -> Unit,
    contentScale: ContentScale = ContentScale.FillWidth,
    alignment: Alignment = Alignment.TopCenter,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Box(modifier = modifier) {
        LiveCasinoBannerImage(
            defaultResId = defaultResId,
            contentScale = contentScale,
            alignment = alignment,
            modifier = Modifier.fillMaxSize()
        )
        // Hide baked-in carousel dots without cropping/zooming the artwork.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.55f)
                .fillMaxHeight(0.1f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF000000), Color(0xFF000000))
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 12.dp)
                .fillMaxWidth(0.4f)
                .fillMaxHeight(0.24f)
                .clickable(onClick = onPlayNowClick)
        )
    }
}
