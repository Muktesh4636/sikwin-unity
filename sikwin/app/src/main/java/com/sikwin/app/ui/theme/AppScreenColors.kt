package com.sikwin.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.data.prefs.ThemePreferences

data class AppScreenColors(
    val isWhite: Boolean,
    val background: Color,
    val surface: Color,
    val text: Color,
    val textMuted: Color,
    val border: Color,
    val headerTitle: Color,
    val chipUnselected: Color,
    val accent: Color,
) {
    val listItemBorder: BorderStroke?
        get() = if (isWhite) BorderStroke(1.dp, border) else null
}

@Composable
fun rememberAppScreenColors(): AppScreenColors {
    val context = LocalContext.current
    val isWhite = remember { ThemePreferences(context).isWhiteTheme() }
    return if (isWhite) {
        AppScreenColors(
            isWhite = true,
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF111111),
            textMuted = Color(0xFF6B7280),
            border = Color(0xFFE5E7EB),
            headerTitle = Color(0xFF111111),
            chipUnselected = Color(0xFFFFFFFF),
            accent = GoldOnWhite,
        )
    } else {
        AppScreenColors(
            isWhite = false,
            background = BlackBackground,
            surface = SurfaceColor,
            text = TextWhite,
            textMuted = TextGrey,
            border = BorderColor,
            headerTitle = PrimaryYellow,
            chipUnselected = SurfaceColor,
            accent = PrimaryYellow,
        )
    }
}

@Composable
fun AppSubScreenHeader(
    title: String,
    colors: AppScreenColors,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.accent,
                modifier = Modifier.padding(4.dp)
            )
        }
        Text(
            text = title,
            color = colors.headerTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = { }, enabled = false) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.Transparent
            )
        }
    }
}
