package com.sikwin.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sikwin.app.R
import com.sikwin.app.data.prefs.BannerPreferences
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.TextGrey
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/** Matches default `live_casino_banner.png` (854 x 450). */
const val LIVE_CASINO_BANNER_ASPECT_RATIO = 854f / 450f

data class HomePromoBanner(
    val imageRes: Int,
    /** If true, allow custom Live Casino override from BannerPreferences. */
    val allowCustomLiveCasino: Boolean = false,
    val onPlayNow: () -> Unit
)

/**
 * Side-scrolling home banners with infinite (360°) loop rotation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePromoBannerCarousel(
    banners: List<HomePromoBanner>,
    modifier: Modifier = Modifier,
    autoScrollMs: Long = 4000L
) {
    if (banners.isEmpty()) return
    val pageCount = banners.size
    val virtualCount = 1000 * pageCount
    val pagerState = rememberPagerState(
        initialPage = virtualCount / 2,
        pageCount = { virtualCount }
    )

    LaunchedEffect(pageCount, autoScrollMs) {
        while (true) {
            yield()
            delay(autoScrollMs)
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(LIVE_CASINO_BANNER_ASPECT_RATIO)
                .clip(RoundedCornerShape(18.dp)),
            pageSpacing = 12.dp
        ) { virtualPage ->
            val banner = banners[virtualPage % pageCount]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
            ) {
                if (banner.allowCustomLiveCasino) {
                    LiveCasinoBannerWithPlayNow(
                        defaultResId = banner.imageRes,
                        onPlayNowClick = banner.onPlayNow,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ImageBannerWithPlayNow(
                        imageRes = banner.imageRes,
                        contentDescription = "Promo banner",
                        onPlayNowClick = banner.onPlayNow,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pageCount) { i ->
                val active = pagerState.currentPage % pageCount == i
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 8.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (active) PrimaryYellow else TextGrey.copy(alpha = 0.55f))
                )
            }
        }
    }
}

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

/** Static drawable banner with PLAY NOW hotspot (same layout as Live Casino). */
@Composable
fun ImageBannerWithPlayNow(
    imageRes: Int,
    contentDescription: String,
    onPlayNowClick: () -> Unit,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = alignment,
            modifier = Modifier.fillMaxSize()
        )
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
