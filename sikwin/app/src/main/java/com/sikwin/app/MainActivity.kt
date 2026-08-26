package com.sikwin.app

import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.sikwin.app.data.api.RetrofitClient
import androidx.compose.ui.viewinterop.AndroidView
import com.sikwin.app.data.auth.SessionManager
import com.sikwin.app.navigation.AppNavigation
import com.sikwin.app.ui.theme.GunduAtaTheme
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import com.sikwin.app.ui.viewmodels.GunduAtaViewModelFactory

class MainActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        com.sikwin.app.data.prefs.LanguagePreferences(this).applySavedLocale()
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        RetrofitClient.init(sessionManager)
        com.sikwin.app.utils.EventLogger.init(this, sessionManager)

        // Drop any parked game WebViews + HTTP cache so reopen shows latest casino/sports/etc.
        try {
            com.sikwin.app.utils.CasinoPrefetcher.clear()
            com.sikwin.app.utils.SportsPrefetcher.clear()
            com.sikwin.app.utils.GunduAtaPrefetcher.clear()
            android.webkit.WebView(this).apply {
                clearCache(true)
                destroy()
            }
        } catch (_: Exception) {
        }

        // Capture uncaught crashes so we can fix them from the backend.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                com.sikwin.app.utils.EventLogger.error(
                    name = "uncaught_exception",
                    message = throwable.message ?: throwable.javaClass.name,
                    throwable = throwable,
                    props = mapOf("thread" to (thread.name ?: ""))
                )
            } catch (_: Exception) {
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Handle incoming logout request from Unity or other sources first.
        // Then (re)sync tokens according to latest session state.
        handleIntent(intent)

        // Ensure Unity cannot auto-login using previously stored credentials.
        sessionManager.scrubUnityCredentials()

        // CRITICAL: When the app is relaunched from recents swipe-kill, in-memory holders are empty.
        // Sync tokens into Unity-readable prefs + static holder again so the next Unity launch works.
        sessionManager.syncAuthToUnity()

        setContent {
            GunduAtaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: GunduAtaViewModel = viewModel(
                        factory = GunduAtaViewModelFactory(sessionManager)
                    )
                    
                    // Listen for deep links or special intents
                    LaunchedEffect(intent) {
                        if (intent?.getStringExtra("action") == "logout") {
                            viewModel.logout()
                            navController.navigate("home") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        
                        // Handle referral deep link: https://gunduata.tech/signup?ref=CODE
                        intent?.data?.let { uri ->
                            if (uri.path == "/signup") {
                                val refCode = uri.getQueryParameter("ref")
                                if (!refCode.isNullOrBlank()) {
                                    navController.navigate("signup?ref=$refCode")
                                }
                            }
                        }
                    }

                    AppNavigation(
                        navController = navController, 
                        viewModel = viewModel,
                        sessionManager = sessionManager
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Proactively sync tokens whenever the app comes to foreground
        try {
            // If Unity logged in/refreshed and wrote newer tokens, adopt them so Kotlin doesn't get session_invalidated.
            sessionManager.syncAuthFromUnityPrefs()
            sessionManager.scrubUnityCredentials()
            sessionManager.syncAuthToUnity()
        } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // We don't quit Unity here to keep it alive for next launch if needed, 
        // but if the app is truly being destroyed by the OS, we should clean up.
        // However, UnityPlayer.quit() usually kills the process.
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // If the app is brought to front via an intent, apply action then re-sync tokens again.
        handleIntent(intent)
        try {
            sessionManager.syncAuthFromUnityPrefs()
            sessionManager.scrubUnityCredentials()
            sessionManager.syncAuthToUnity()
        } catch (_: Exception) {}
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val action = intent?.getStringExtra("action")
        if (action == "logout") {
            android.util.Log.w("MainActivity", "handleIntent: received action=logout, forcing sessionManager.logout()")
            sessionManager.forceLogout("MainActivity_intent_action_logout")
        } else if (!action.isNullOrBlank()) {
            android.util.Log.d("MainActivity", "handleIntent: received action=$action (ignored)")
        }
    }
}
