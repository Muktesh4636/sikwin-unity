package com.sikwin.app.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri as AndroidUri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sikwin.app.data.auth.SessionManager
import com.sikwin.app.ui.screens.*
import com.sikwin.app.ui.screens.AffiliateScreen
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import com.sikwin.app.ui.theme.PrimaryYellow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.sikwin.app.utils.Constants
import com.sikwin.app.utils.CasinoPrefetcher
import com.sikwin.app.utils.SportsPrefetcher
import com.unity3d.player.UnityTokenHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: GunduAtaViewModel,
    sessionManager: SessionManager
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showAuthDialog by remember { mutableStateOf(false) }

    // When app goes to background, set flag so support popup shows on next home visit (after reopen)
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.checkMaintenanceStatus()
                // Unity may have refreshed/logged-in and written newer tokens; adopt them before doing any API calls.
                try { sessionManager.syncAuthFromUnityPrefs() } catch (_: Exception) {}
                viewModel.checkSession()
                // Server-verified session check: if backend invalidated the token (e.g., login on another phone),
                // this will receive 401/403 and trigger a forced logout.
                if (sessionManager.fetchAuthToken() != null) {
                    viewModel.fetchProfile()
                }
            }
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.showSupportPopupOnNextHomeVisit = true
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        }
    }

    // Maintenance check on first composition
    LaunchedEffect(Unit) {
        viewModel.checkMaintenanceStatus()
        try { sessionManager.syncAuthFromUnityPrefs() } catch (_: Exception) {}
        // Verify session with backend on cold start too.
        if (sessionManager.fetchAuthToken() != null) {
            viewModel.fetchProfile()
        }
    }

    // Re-check maintenance periodically while user is in the app, so if admin enables
    // maintenance they see the screen without having to reopen the app.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000) // every 30 seconds
            viewModel.checkMaintenanceStatus()
        }
    }

    if (viewModel.maintenanceActive) {
        MaintenanceScreen(
            message = viewModel.maintenanceMessage ?: "App under maintenance. Please come back soon.",
            onRetry = { viewModel.checkMaintenanceStatus() }
        )
        return
    }

    // If logged out, force user to auth flow (no wallet/balance UI for guests).
    // If logged in, quietly prefetch Casino so the tab opens instantly.
    LaunchedEffect(viewModel.loginSuccess) {
        if (!viewModel.loginSuccess) {
            // Ensure graph is set before popping.
            kotlinx.coroutines.delay(50)
            try {
                navController.navigate("login") {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            } catch (_: Exception) {
                // Fallback: just navigate; worst case user can back out.
                navController.navigate("login") { launchSingleTop = true }
            }
        } else {
            CasinoPrefetcher.prefetchOnHome(context, sessionManager.fetchAuthToken())
        }
    }

    // Register FCM token when user is logged in (for push notifications)
    LaunchedEffect(Unit) {
        if (sessionManager.fetchAuthToken() != null) {
            viewModel.registerFcmTokenIfNeeded()
        }
    }

    // App Update Check
    LaunchedEffect(Unit) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            viewModel.checkForUpdates(currentVersionCode)
        } catch (e: Exception) {
            android.util.Log.e("AppNavigation", "Failed to get version code", e)
        }
    }

    if (viewModel.showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (!viewModel.isForceUpdate) {
                    viewModel.showUpdateDialog = false
                }
            },
            containerColor = com.sikwin.app.ui.theme.SurfaceColor,
            title = { 
                Text(
                    "New Update Available", 
                    fontWeight = FontWeight.Bold,
                    color = com.sikwin.app.ui.theme.TextWhite,
                    fontSize = 20.sp
                ) 
            },
            text = { 
                Column {
                    Text(
                        "A new version of Pride is available.",
                        color = com.sikwin.app.ui.theme.TextWhite,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    viewModel.latestVersionName?.let { 
                        Text(
                            "Version: $it", 
                            fontSize = 14.sp, 
                            color = com.sikwin.app.ui.theme.TextGrey
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (viewModel.isForceUpdate) {
                            "This update is required. Please update now to continue using the app."
                        } else {
                            "A new version is available. You can update now or continue using the current version."
                        },
                        color = com.sikwin.app.ui.theme.TextWhite,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUrl?.let { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, AndroidUri.parse(url))
                                context.startActivity(intent)
                                // If force update, don't close dialog - user must update
                                if (!viewModel.isForceUpdate) {
                                    viewModel.showUpdateDialog = false
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open download link", Toast.LENGTH_SHORT).show()
                            }
                        } ?: run {
                            Toast.makeText(context, "Download URL not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Update Now", 
                        color = com.sikwin.app.ui.theme.BlackBackground, 
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = if (!viewModel.isForceUpdate) {
                {
                    TextButton(
                        onClick = { viewModel.showUpdateDialog = false }
                    ) {
                        Text(
                            "Use old version",
                            color = com.sikwin.app.ui.theme.TextGrey
                        )
                    }
                }
            } else null
        )
    }

    // Prevent rapid navigation
    var lastNavigationTime by remember { mutableStateOf(0L) }
    var lastGameLaunchTime by remember { mutableStateOf(0L) }
    val navigationCooldown = 500L // 500ms cooldown between navigation calls
    val gameLaunchCooldown = 1500L // Prevent double-tap crash when launching Unity

    fun safeNavigate(route: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigationTime > navigationCooldown) {
            lastNavigationTime = currentTime
            com.sikwin.app.utils.EventLogger.click("navigate", mapOf("route" to route))
            navController.navigate(route)
        }
    }

    /** Open sports WebView (LIVE lobby or a specific sport). Never gated on login. */
    fun openSportsWebView(sport: String? = null) {
        val dest = when (val s = sport?.trim()?.lowercase().orEmpty()) {
            "", "live" -> "live"
            "cricket", "soccer", "tennis", "football" -> {
                val key = if (s == "football") "soccer" else s
                "sports/$key"
            }
            else -> "live"
        }
        val current = navController.currentBackStackEntry?.destination?.route.orEmpty()
        // Already on this sports destination — ignore spam taps (avoids remount glitch).
        if (current == dest || (dest == "live" && (current == "live" || current.startsWith("sports?")))) {
            return
        }
        com.sikwin.app.utils.EventLogger.click("open_sports", mapOf("dest" to dest, "sport" to (sport ?: "")))
        try {
            navController.navigate(dest) {
                launchSingleTop = true
            }
        } catch (e: Exception) {
            android.util.Log.e("AppNavigation", "openSportsWebView failed dest=$dest", e)
            com.sikwin.app.utils.EventLogger.error(
                name = "sports_nav_failed",
                message = e.message ?: e.javaClass.simpleName,
                props = mapOf("dest" to dest)
            )
            Toast.makeText(context, "Could not open Sports", Toast.LENGTH_SHORT).show()
        }
    }

    /** Open Casino lobby WebView — single-top + ignore spam taps. */
    fun openCasinoGames() {
        val current = navController.currentBackStackEntry?.destination?.route.orEmpty()
        if (current == "casino_games") return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigationTime <= navigationCooldown) return
        lastNavigationTime = currentTime
        com.sikwin.app.utils.EventLogger.click("open_casino", emptyMap())
        try {
            // Halt sports before nav so first casino frame isn't contending Chromium.
            SportsPrefetcher.haltForOtherWebGame()
            navController.navigate("casino_games") {
                launchSingleTop = true
            }
        } catch (e: Exception) {
            android.util.Log.e("AppNavigation", "openCasinoGames failed", e)
            Toast.makeText(context, "Could not open Casino", Toast.LENGTH_SHORT).show()
        }
    }

    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            title = { Text(stringResource(com.sikwin.app.R.string.sign_in_required), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text(stringResource(com.sikwin.app.R.string.sign_in_required_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showAuthDialog = false
                    navController.navigate("login")
                }) {
                    Text(stringResource(com.sikwin.app.R.string.sign_in), color = PrimaryYellow, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAuthDialog = false
                    navController.navigate("signup")
                }) {
                    Text(stringResource(com.sikwin.app.R.string.sign_up), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        )
    }

    /** Free space (MB) available for the app. */
    fun getFreeSpaceMb(): Long {
        return try {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = android.os.StatFs(path.path)
            val available = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBlocksLong * stat.blockSizeLong
            } else {
                @Suppress("DEPRECATION")
                stat.availableBlocks.toLong() * stat.blockSize.toLong()
            }
            available / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }
    }

    fun executeGameLaunch(liveMode: Boolean = false) {
        // TEMP: Unity binaries removed from APK — keep Live + WebView games.
        if (!com.sikwin.app.BuildConfig.HAS_UNITY) {
            Toast.makeText(
                context,
                "Pride Unity is temporarily unavailable. Try Live or Casino games.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastGameLaunchTime < gameLaunchCooldown) return
        lastGameLaunchTime = now

        val freeSpaceMb = getFreeSpaceMb()
        if (freeSpaceMb < 100) {
            Toast.makeText(
                context,
                "Low storage. Please free up at least 100 MB and try again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val authToken = sessionManager.fetchAuthToken()
            val refreshToken = sessionManager.fetchRefreshToken()
            val userId = sessionManager.fetchUserId()
            val isLoggedIn = !authToken.isNullOrBlank()
            val (rawUser0, rawPass0) = viewModel.getLastEnteredCredentials()
            val (savedUser, savedPass) = viewModel.getSavedCredentials()
            val rawUser = rawUser0 ?: savedUser
            val rawPass = rawPass0 ?: savedPass

            android.util.Log.d("AppNavigation", "executeGameLaunch: token=${if (authToken.isNullOrBlank()) "EMPTY" else "${authToken.take(8)}..."}, refresh=${if (refreshToken.isNullOrBlank()) "EMPTY" else "${refreshToken.take(8)}..."}, freeSpaceMb=$freeSpaceMb")

            sessionManager.syncAuthToUnity()
            com.unity3d.player.UnityTokenHolder.setTokens(authToken ?: "", refreshToken ?: "", "", "")
            com.sikwin.app.utils.UnityTokenHelper.sendTokensToUnity(context, authToken ?: "", refreshToken ?: "")
            // IMPORTANT: Do not pass raw credentials to Unity here.
            // Unity-side auto-login via username/password can trigger backend single-session invalidation,
            // which then logs Kotlin out when returning from Unity.

            val intent = Intent().setClassName(
                context.packageName,
                "com.unity3d.player.UnityPlayerGameActivity"
            )
            sessionManager.markUnityLaunchNow()
            intent.putExtra("token", authToken ?: "")
            intent.putExtra("auth_token", authToken ?: "")
            intent.putExtra("access_token", authToken ?: "")
            intent.putExtra("access", authToken ?: "")
            intent.putExtra("refresh_token", refreshToken ?: "")
            intent.putExtra("refresh", refreshToken ?: "")
            intent.putExtra("user_id", userId ?: "")
            intent.putExtra("is_logged_in", isLoggedIn)
            intent.putExtra("auto_login", true)
            intent.putExtra("from_android_app", true)
            intent.putExtra("game_mode", if (liveMode) "live" else "normal")
            // Pass credentials transiently (Intent-only). UnityPlayerGameActivity will forward via UnitySendMessage.
            if (!rawUser.isNullOrBlank()) intent.putExtra("username", rawUser)
            if (!rawPass.isNullOrBlank()) intent.putExtra("password", rawPass)
            // Avoid NEW_TASK/CLEAR_TOP so Android back returns to Kotlin screen.
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            viewModel.preLoadedTimer?.let { intent.putExtra("preloaded_timer", it) }
            viewModel.preLoadedStatus?.let { intent.putExtra("preloaded_status", it) }
            viewModel.preLoadedRoundId?.let { intent.putExtra("preloaded_round_id", it) }
            intent.putExtra("preloaded_timestamp", System.currentTimeMillis())
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("AppNavigation", "Final launch failed", e)
            val msg = if (e.message?.contains("storage", ignoreCase = true) == true || freeSpaceMb < 500) {
                "Not enough storage. Free up space (Settings > Storage) and try again."
            } else {
                "Unable to open Pride. Please try again."
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun launchGame() {
        try {
            executeGameLaunch()
        } catch (e: Exception) {
            android.util.Log.e("AppNavigation", "Launch failed", e)
            Toast.makeText(context, "Unable to open game. Please try again later.", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Handle redirect requests (e.g. from Unity back button, balance click, dice results click)
    LaunchedEffect(activity?.intent) {
        val redirectRoute = activity?.intent?.getStringExtra("redirect")
        if (redirectRoute != null) {
            if (redirectRoute == "home") {
                navController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(redirectRoute) {
                    launchSingleTop = true
                }
            }
            activity.intent.removeExtra("redirect")
        }
    }

    val startDestination = if (viewModel.loginSuccess) "home" else "login"

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        if (!currentRoute.isNullOrBlank()) {
            com.sikwin.app.utils.EventLogger.setScreen(currentRoute.substringBefore("?"))
        }
    }
    
    NavHost(navController = navController, startDestination = startDestination) {
        composable("gundu_ata_game") {
            GunduAtaGameScreen(
                viewModel = viewModel,
                sessionManager = sessionManager,
                onBack = { navController.popBackStack() }
            )
        }
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToSignUp = { 
                    navController.navigate("signup") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToForgotPassword = {
                    navController.navigate("forgot_password")
                }
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSuccess = {
                    navController.navigate("login") {
                        popUpTo("forgot_password") { inclusive = true }
                    }
                }
            )
        }
        composable("signup?ref={ref}&spin={spin}") { backStackEntry ->
            val refCode = backStackEntry.arguments?.getString("ref")
            val spinBalance = backStackEntry.arguments?.getString("spin")?.toIntOrNull() ?: 0
            SignUpScreen(
                viewModel = viewModel,
                initialReferralCode = refCode ?: "",
                initialSpinBalance = spinBalance,
                onSignUpSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToSignIn = { 
                    navController.navigate("login") {
                        popUpTo("signup") { inclusive = true }
                    }
                }
            )
        }
        composable("signup?ref={ref}") { backStackEntry ->
            val refCode = backStackEntry.arguments?.getString("ref")
            SignUpScreen(
                viewModel = viewModel,
                initialReferralCode = refCode ?: "",
                onSignUpSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToSignIn = { 
                    navController.navigate("login") {
                        popUpTo("signup") { inclusive = true }
                    }
                }
            )
        }
        composable("signup") {
            SignUpScreen(
                viewModel = viewModel,
                onSignUpSuccess = { navController.navigate("home") },
                onNavigateToSignIn = { 
                    navController.navigate("login") {
                        popUpTo("signup") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onGameClick = { gameId ->
                    com.sikwin.app.utils.EventLogger.click("game_open", mapOf("game" to gameId))
                    when (gameId) {
                        "gundu_ata", "gundu_ata_web" -> {
                            if (!viewModel.loginSuccess) {
                                showAuthDialog = true
                            } else {
                                navController.navigate("gundu_ata_web") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        "gundu_ata_live" -> {
                            navController.navigate("gundu_ata_live") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        "colour_game" -> {
                            if (!viewModel.loginSuccess) {
                                showAuthDialog = true
                            } else {
                                navController.navigate("colour_game") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        "cock_fight" -> {
                            if (!viewModel.loginSuccess) {
                                showAuthDialog = true
                            } else {
                                navController.navigate("cock_fight") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                },
                onNavigate = { route ->
                    com.sikwin.app.utils.EventLogger.click("nav", mapOf("route" to route))
                    if (route == "gundu_ata" || route == "gundu_ata_web") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("gundu_ata_web") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "gundu_ata_live") {
                        navController.navigate("gundu_ata_live") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else if (route == "colour_game") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("colour_game") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "cock_fight") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("cock_fight") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "roulette") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("roulette") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "trading") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("trading") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "casino_games" || route == "casino") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            openCasinoGames()
                        }
                    } else if (route == "live" || route == "sports") {
                        openSportsWebView(null)
                    } else if (route.startsWith("sports?sport=")) {
                        openSportsWebView(route.substringAfter("sports?sport=").substringBefore("&"))
                    } else if (route.startsWith("sports/")) {
                        openSportsWebView(route.removePrefix("sports/"))
                    } else if (route == "soccer") {
                        openSportsWebView("soccer")
                    } else if (route == "tennis") {
                        openSportsWebView("tennis")
                    } else if (route == "chicken_road") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("chicken_road") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "chicken_road_2") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("chicken_road_2") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "vortex") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("vortex") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "ipl" || route == "cricket") {
                        openSportsWebView("cricket")
                    } else if (route == "coin") {
                        navController.navigate("coin") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else if (route == "me") {
                        if (viewModel.loginSuccess) {
                            navController.navigate("me") {
                                // Pop up to home to avoid backstack issues
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else {
                            showAuthDialog = true
                        }
                    } else if (route == "leaderboard") {
                        if (viewModel.loginSuccess) {
                            navController.navigate("leaderboard") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else {
                            showAuthDialog = true
                        }
                    } else if (route.startsWith("wallet") || route.startsWith("deposit") || route == "withdraw" || route.startsWith("transactions")) {
                        if (viewModel.loginSuccess) {
                            safeNavigate(route)
                        } else {
                            showAuthDialog = true
                        }
                    } else if (route == "withdrawals_record" || route == "withdrawal_account") {
                        if (viewModel.loginSuccess) {
                            safeNavigate(route)
                        } else {
                            showAuthDialog = true
                        }
                    } else if (route != "home") {
                        safeNavigate(route)
                    }
                }
            )
        }
        composable("me") {
            ProfileScreen(
                viewModel = viewModel,
                sessionManager = sessionManager,
                onNavigate = { route ->
                    com.sikwin.app.utils.EventLogger.click("nav", mapOf("route" to route))
                    if (route == "gundu_ata" || route == "gundu_ata_web") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            navController.navigate("gundu_ata_web") {
                                launchSingleTop = true
                            }
                        }
                    } else if (route == "gundu_ata_live") {
                        navController.navigate("gundu_ata_live") {
                            launchSingleTop = true
                        }
                    } else if (route == "home") {
                        // Soft-restore existing home — avoid inclusive destroy/recreate
                        // which remounts Dual Cards ExoPlayers and can leave a black screen
                        if (!navController.popBackStack("home", inclusive = false)) {
                            navController.navigate("home") {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else if (route == "casino_games" || route == "casino") {
                        if (!viewModel.loginSuccess) {
                            showAuthDialog = true
                        } else {
                            openCasinoGames()
                        }
                    } else if (route == "live" || route == "sports" || route.startsWith("sports?") || route.startsWith("sports/")) {
                        openSportsWebView(
                            when {
                                route == "live" || route == "sports" -> null
                                route.startsWith("sports?sport=") ->
                                    route.substringAfter("sports?sport=").substringBefore("&")
                                route.startsWith("sports/") -> route.removePrefix("sports/")
                                else -> null
                            }
                        )
                    } else if (route == "login") {
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(route)
                    }
                }
            )
        }
        composable("colour_game") {
            ColourGameScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDeposit = {
                    if (!viewModel.loginSuccess) {
                        showAuthDialog = true
                    } else {
                        navController.navigate("deposit") {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable("roulette") {
            RouletteWebViewScreen(
                accessToken = sessionManager.fetchAuthToken(),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("trading") {
            TradingWebViewScreen(
                accessToken = sessionManager.fetchAuthToken(),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("chicken_road") {
            ChickenRoadWebViewScreen(
                game = ChickenRoadGame.ONE,
                accessToken = sessionManager.fetchAuthToken(),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("chicken_road_2") {
            ChickenRoadWebViewScreen(
                game = ChickenRoadGame.TWO,
                accessToken = sessionManager.fetchAuthToken(),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("vortex") {
            ChickenRoadWebViewScreen(
                game = ChickenRoadGame.VORTEX,
                accessToken = sessionManager.fetchAuthToken(),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("casino_web_game") {
            CasinoSiteGameScreen(
                title = CasinoPrefetcher.pendingWebGameTitle ?: "Game",
                gameUrl = CasinoPrefetcher.pendingWebGameUrl ?: Constants.CASINO_URL,
                accessToken = sessionManager.fetchAuthToken(),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("cock_fight") {
            CockFightScreen(onBack = { navController.popBackStack() })
        }
        // LIVE bottom-nav → sports lobby WebView (unique route; avoids sports? query conflicts)
        composable("live") {
            SportsWebViewScreen(
                accessToken = sessionManager.fetchAuthToken(),
                refreshToken = sessionManager.fetchRefreshToken(),
                sport = null,
                onBack = {
                    if (!navController.popBackStack("home", inclusive = false)) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onCasino = { openCasinoGames() },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("sports") {
            SportsWebViewScreen(
                accessToken = sessionManager.fetchAuthToken(),
                refreshToken = sessionManager.fetchRefreshToken(),
                sport = null,
                onBack = {
                    if (!navController.popBackStack("home", inclusive = false)) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onCasino = { openCasinoGames() },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable(
            route = "sports/{sport}",
            arguments = listOf(navArgument("sport") { type = NavType.StringType })
        ) { backStackEntry ->
            SportsWebViewScreen(
                accessToken = sessionManager.fetchAuthToken(),
                refreshToken = sessionManager.fetchRefreshToken(),
                sport = backStackEntry.arguments?.getString("sport"),
                onBack = {
                    if (!navController.popBackStack("home", inclusive = false)) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onCasino = { openCasinoGames() },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        // Native cricket UI temporarily removed — open Sports WebView instead.
        composable("ipl") {
            SportsWebViewScreen(
                accessToken = sessionManager.fetchAuthToken(),
                refreshToken = sessionManager.fetchRefreshToken(),
                sport = "cricket",
                onBack = {
                    if (!navController.popBackStack("home", inclusive = false)) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onCasino = { openCasinoGames() },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("casino_games") {
            ChickenRoadWebViewScreen(
                game = ChickenRoadGame.CASINO,
                accessToken = sessionManager.fetchAuthToken(),
                onBack = {
                    if (!navController.popBackStack("home", inclusive = false)) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onRequireLogin = { showAuthDialog = true },
                onOpenNativeGame = { route ->
                    // Chit Pat → coin, Rangu → colour_game (Kotlin screens, not WebView)
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("coin") {
            HeadsTailsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    when {
                        route == "gundu_ata" || route == "gundu_ata_web" -> {
                            if (!viewModel.loginSuccess) {
                                showAuthDialog = true
                            } else {
                                navController.navigate("gundu_ata_web") {
                                    launchSingleTop = true
                                }
                            }
                        }
                        route == "home" -> {
                            if (!navController.popBackStack("home", inclusive = false)) {
                                navController.navigate("home") {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        route == "login" -> {
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        else -> navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
        composable("wallet") {
            WalletScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToDeposit = { navController.navigate("deposit") },
                onNavigateToWithdraw = { navController.navigate("withdraw") }
            )
        }
        composable("deposit?method={method}") { backStackEntry ->
            val method = backStackEntry.arguments?.getString("method")
            DepositScreen(
                viewModel = viewModel,
                initialMethod = method,
                onBack = { navController.popBackStack() },
                onNavigateToWithdraw = { navController.navigate("withdraw") },
                onNavigateToPayment = { amount, paymentMethod ->
                    navController.navigate("payment/$amount/$paymentMethod")
                }
            )
        }
        composable("deposit") {
            DepositScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToWithdraw = { navController.navigate("withdraw") },
                onNavigateToPayment = { amount, method ->
                    navController.navigate("payment/$amount/$method")
                }
            )
        }
        composable("payment/{amount}/{method}") { backStackEntry ->
            val amount = backStackEntry.arguments?.getString("amount") ?: "0"
            val method = backStackEntry.arguments?.getString("method") ?: "UPI"
            PaymentScreen(
                amount = amount,
                paymentMethod = method,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSubmitSuccess = {
                    navController.navigate("deposits_record") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }
        composable("withdraw") {
            WithdrawScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddBankAccount = {
                    navController.navigate("add_bank_account")
                }
            )
        }
        composable("add_bank_account") {
            AddBankAccountScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSubmitSuccess = {
                    navController.popBackStack()
                }
            )
        }
        composable("transactions") {
            TransactionHistoryScreen(
                title = "Transaction Record",
                initialCategory = "Deposit",
                showTabs = true,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("deposits_record") {
            TransactionHistoryScreen(
                title = "Deposit Record",
                initialCategory = "Deposit",
                showTabs = false,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("withdrawals_record") {
            TransactionHistoryScreen(
                title = "Withdrawal Record",
                initialCategory = "Withdraw",
                showTabs = false,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("betting_record") {
            TransactionHistoryScreen(
                title = "Betting Record",
                initialCategory = "Betting",
                showTabs = false,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        // Native cricket betting history UI temporarily removed.
        composable("cricket_betting_record") {
            SportsWebViewScreen(
                accessToken = sessionManager.fetchAuthToken(),
                refreshToken = sessionManager.fetchRefreshToken(),
                sport = "cricket",
                onBack = { navController.popBackStack() },
                onCasino = { openCasinoGames() },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("personal_info") {
            PersonalInfoScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("white_label_account") {
            WhiteLabelAccountScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("lucky_wheel") {
            LuckyWheelScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("lucky_draw") {
            LuckyDrawScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    com.sikwin.app.utils.EventLogger.click("nav", mapOf("route" to route))
                    navController.navigate(route)
                }
            )
        }
        composable("gundu_ata_web") {
            GunduAtaWebViewScreen(
                accessToken = sessionManager.fetchAuthToken(),
                refreshToken = sessionManager.fetchRefreshToken(),
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onRequireLogin = { showAuthDialog = true }
            )
        }
        composable("gundu_ata_live") {
            GunduAtaLiveScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    com.sikwin.app.utils.EventLogger.click("nav", mapOf("route" to route))
                    navController.navigate(route)
                }
            )
        }
        composable("leaderboard") {
            LeaderboardScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("withdrawal_account") {
            WithdrawalAccountScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddBankAccount = { navController.navigate("add_bank_account") }
            )
        }
        composable("security") {
            SecurityScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("info") {
            InfoScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("affiliate") {
            AffiliateScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("dice_results") {
            DiceResultsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("help_center") {
            HelpCenterScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("languages") {
            LanguageScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("themes") {
            ThemeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("game_guidelines") {
            GameGuidelinesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("game_loading") {
            GameLoadingScreen(
                onLoadingComplete = { 
                    executeGameLaunch()
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun GameLoadingScreen(onLoadingComplete: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        val duration = 7000L // 7 seconds
        val interval = 50L
        val steps = duration / interval
        
        for (i in 1..steps) {
            delay(interval)
            progress = i.toFloat() / steps
        }
        onLoadingComplete()
    }

    Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(com.sikwin.app.ui.theme.BlackBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = com.sikwin.app.R.drawable.app_logo),
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))
            Text(
                text = stringResource(com.sikwin.app.R.string.app_name),
                color = com.sikwin.app.ui.theme.TextWhite,
                fontSize = 32.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = androidx.compose.ui.Modifier.height(48.dp))
            CircularProgressIndicator(
                progress = { progress },
                color = com.sikwin.app.ui.theme.PrimaryYellow,
                strokeWidth = 4.dp,
                modifier = androidx.compose.ui.Modifier.size(64.dp)
            )
            Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
            Text(
                text = "Loading game assets...",
                color = com.sikwin.app.ui.theme.TextGrey,
                fontSize = 16.sp
            )
        }
    }
}
