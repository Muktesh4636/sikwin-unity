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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.R
import com.sikwin.app.data.prefs.ThemePreferences
import com.sikwin.app.ui.theme.*

@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val themePrefs = remember { ThemePreferences(context) }
    var selected by remember { mutableStateOf(themePrefs.getAppTheme()) }

    fun applyTheme(themeId: String) {
        if (selected == themeId) return
        themePrefs.setAppTheme(themeId)
        selected = themeId
        activity?.recreate()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                stringResource(R.string.themes),
                color = PrimaryYellow,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Text(
            stringResource(R.string.choose_theme),
            color = TextGrey,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ThemeOptionCard(
                title = stringResource(R.string.theme_hero_title),
                subtitle = stringResource(R.string.theme_hero_subtitle),
                selected = selected == ThemePreferences.THEME_HERO,
                onClick = { applyTheme(ThemePreferences.THEME_HERO) }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hero_theme_preview),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ThemeOptionCard(
                title = stringResource(R.string.theme_classic_title),
                subtitle = stringResource(R.string.theme_classic_subtitle),
                selected = selected == ThemePreferences.THEME_CLASSIC,
                onClick = { applyTheme(ThemePreferences.THEME_CLASSIC) }
            ) {
                // Mini preview of classic dark + yellow home
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BlackBackground)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "GUNDU ATA",
                            color = PrimaryYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceColor)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("₹ ---", color = TextWhite, fontSize = 9.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(70.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1565C0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("HOT GAMES", color = PrimaryYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
