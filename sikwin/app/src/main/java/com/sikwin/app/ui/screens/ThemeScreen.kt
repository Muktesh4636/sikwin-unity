package com.sikwin.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import com.sikwin.app.R
import com.sikwin.app.data.prefs.ThemePreferences
import com.sikwin.app.ui.theme.*
import com.sikwin.app.utils.CasinoPrefetcher
import com.sikwin.app.utils.SportsPrefetcher

@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val themePrefs = remember { ThemePreferences(context) }
    var selected by remember { mutableStateOf(themePrefs.pickerThemeId()) }

    fun applyTheme(themeId: String) {
        if (selected == themeId) return
        themePrefs.setAppTheme(themeId)
        selected = themeId
        val webMode = themePrefs.webThemeMode()
        SportsPrefetcher.applyAppTheme(context, webMode)
        CasinoPrefetcher.applyAppTheme(context, webMode)
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
