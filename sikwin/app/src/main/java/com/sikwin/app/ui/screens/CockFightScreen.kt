@file:OptIn(ExperimentalMaterial3Api::class)

package com.sikwin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.R
import com.sikwin.app.ui.theme.BlackBackground
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.utils.Constants
import com.sikwin.app.utils.openInDefaultBrowser

/**
 * Placeholder for Cock fight — opens from the home quick-games row (beside Colour game).
 */
@Composable
fun CockFightScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = BlackBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.cock_fight_title),
                        color = PrimaryYellow,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = PrimaryYellow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBackground)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BlackBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🐓",
                    fontSize = 88.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.cock_fight_title),
                    color = PrimaryYellow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.cock_fight_coming_soon),
                    color = TextGrey,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { context.openInDefaultBrowser(Constants.WEBGL_GAME_URL) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
                ) {
                    Text(
                        stringResource(R.string.cock_fight_open_in_browser),
                        color = BlackBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
