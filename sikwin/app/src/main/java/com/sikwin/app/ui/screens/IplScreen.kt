package com.sikwin.app.ui.screens

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sikwin.app.ui.theme.BlackBackground
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import com.sikwin.app.utils.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IplScreen(
    viewModel: GunduAtaViewModel,
    onNavigate: (String) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("IPL", color = PrimaryYellow) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BlackBackground,
                    titleContentColor = PrimaryYellow
                )
            )
        },
        bottomBar = { HomeBottomNavigation(currentRoute = "ipl", viewModel = viewModel, onNavigate = onNavigate) },
        containerColor = BlackBackground
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(Constants.IPL_WEB_URL)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
