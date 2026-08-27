package com.sikwin.app.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sikwin.app.R
import com.sikwin.app.data.prefs.BannerPreferences
import com.sikwin.app.data.prefs.ThemePreferences
import com.sikwin.app.ui.theme.*

@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val themePrefs = remember { ThemePreferences(context) }
    val bannerPrefs = remember { BannerPreferences(context) }
    var selected by remember { mutableStateOf(themePrefs.pickerThemeId()) }
    var bannerRevision by remember { mutableIntStateOf(0) }
    val hasCustomBanner = remember(bannerRevision) { bannerPrefs.hasCustomLiveCasinoBanner() }

    val pickBanner = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = bannerPrefs.saveLiveCasinoBanner(uri)
        if (ok) {
            bannerRevision++
            Toast.makeText(context, context.getString(R.string.banner_saved), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.banner_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun applyTheme(themeId: String) {
        if (selected == themeId) return
        themePrefs.setAppTheme(themeId)
        selected = themeId
        activity?.recreate()
    }

    val colors = rememberAppScreenColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AppSubScreenHeader(
            title = stringResource(R.string.themes),
            colors = colors,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Casino banner editor
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceColor)
                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Text(
                    stringResource(R.string.live_casino_banner_title),
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    stringResource(R.string.live_casino_banner_subtitle),
                    color = TextGrey,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A0A0A))
                ) {
                    key(bannerRevision) {
                        if (hasCustomBanner) {
                            val file = bannerPrefs.getLiveCasinoBannerFile()
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(file).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.live_casino_banner),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryYellow)
                            .clickable { pickBanner.launch("image/*") }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (hasCustomBanner) stringResource(R.string.change_banner)
                            else stringResource(R.string.add_banner),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    if (hasCustomBanner) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable {
                                    bannerPrefs.clearLiveCasinoBanner()
                                    bannerRevision++
                                    Toast.makeText(context, context.getString(R.string.banner_reset), Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = TextGrey, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.reset_banner), color = TextGrey, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.choose_theme),
                color = TextGrey,
                fontSize = 14.sp
            )

            ThemeOptionCard(
                title = stringResource(R.string.theme_dark_title),
                subtitle = stringResource(R.string.theme_dark_subtitle),
                selected = selected == ThemePreferences.THEME_DUAL_CARDS,
                onClick = { applyTheme(ThemePreferences.THEME_DUAL_CARDS) }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.theme_dual_cards_preview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ThemeOptionCard(
                title = stringResource(R.string.theme_white_title),
                subtitle = stringResource(R.string.theme_white_subtitle),
                selected = selected == ThemePreferences.THEME_WHITE,
                onClick = { applyTheme(ThemePreferences.THEME_WHITE) }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.theme_white_preview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceColor)
            .then(
                if (selected) Modifier.border(2.dp, PrimaryYellow, RoundedCornerShape(16.dp))
                else Modifier.border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A)),
            content = preview
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = TextGrey, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryYellow)
            }
        }
    }
}
