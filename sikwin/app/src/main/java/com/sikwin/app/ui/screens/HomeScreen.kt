package com.sikwin.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sikwin.app.R
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.sikwin.app.ui.theme.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import android.net.Uri
import android.content.Intent
import android.view.LayoutInflater
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import com.sikwin.app.data.api.RetrofitClient
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import com.sikwin.app.utils.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GunduAtaViewModel,
    onGameClick: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val appTheme = remember {
        com.sikwin.app.data.prefs.ThemePreferences(context).getAppTheme()
    }
    when (appTheme) {
        com.sikwin.app.data.prefs.ThemePreferences.THEME_HERO -> {
            HeroHomeScreen(
                viewModel = viewModel,
                onGameClick = onGameClick,
                onNavigate = onNavigate
            )
            return
        }
        com.sikwin.app.data.prefs.ThemePreferences.THEME_DUAL_CARDS -> {
            DualCardsHomeScreen(
                viewModel = viewModel,
                onGameClick = onGameClick,
                onNavigate = onNavigate
            )
            return
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var showGuestSpinWheel by remember { mutableStateOf(false) }
    var guestWheelCloseCount by remember { mutableIntStateOf(0) }
    var showLoginPopup by remember { mutableStateOf(false) }
    var showGunduAtaChoiceDialog by remember { mutableStateOf(false) }

    if (showGunduAtaChoiceDialog) {
        GunduAtaChoiceDialog(
            onDismiss = { showGunduAtaChoiceDialog = false },
            onPlayLive = { onNavigate("gundu_ata_live") },
            onPlayNormal = { onGameClick("gundu_ata") }
        )
    }
    
    if (showLoginPopup) {
        AlertDialog(
            onDismissRequest = { showLoginPopup = false },
            title = { Text(stringResource(R.string.login_required), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.login_required_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginPopup = false
                        onNavigate("signup")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
                ) {
                    Text(stringResource(R.string.sign_up), color = BlackBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginPopup = false }) {
                    Text(stringResource(R.string.cancel), color = TextGrey)
                }
            },
            containerColor = SurfaceColor,
            titleContentColor = TextWhite,
            textContentColor = TextGrey
        )
    }
    
    // Show guest spin wheel after 3 seconds if not logged in
    LaunchedEffect(viewModel.loginSuccess, guestWheelCloseCount) {
        if (!viewModel.loginSuccess && guestWheelCloseCount < 1) {
            delay(3000)
            showGuestSpinWheel = true
        }
    }

    if (showGuestSpinWheel) {
        GuestSpinWheelDialog(
            onDismiss = { 
                showGuestSpinWheel = false
                guestWheelCloseCount++
            },
            onRegisterClick = { amount ->
                showGuestSpinWheel = false
                onNavigate("signup?ref=&spin=$amount")
            }
        )
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkSession()
                if (viewModel.loginSuccess) {
                    viewModel.fetchWallet()
                    viewModel.fetchProfile()
                    // Start pre-loading timer when app is resumed
                    viewModel.startTimerPreloading()
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                // Stop pre-loading when app goes to background to save battery
                viewModel.stopTimerPreloading()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopTimerPreloading()
        }
    }

    Scaffold(
        topBar = { 
            HomeTopBar(
                viewModel = viewModel,
                balance = viewModel.wallet?.balance ?: "0.00",
                isLoggedIn = viewModel.loginSuccess,
                onWalletClick = { onNavigate("wallet") },
                onDepositClick = { onNavigate("deposit") },
                onNavigate = onNavigate
            ) 
        },
        bottomBar = { HomeBottomNavigation(currentRoute = "home", viewModel = viewModel, onNavigate = onNavigate) },
        containerColor = BlackBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Search Bar
                SearchBar(onSearch = { searchQuery = it })
                
                if (searchQuery.isEmpty()) {
                    // Banners (no Install APK or Become a partner — those are only in Profile)
                    PromotionalBanners(viewModel, onNavigate)

                    // Quick-launch game icons row
                    Spacer(modifier = Modifier.height(8.dp))
                    QuickGamesRow(
                        onGameClick = { gameId ->
                            if (gameId == "more") {
                                // scroll to hot games — just a no-op for now
                            } else {
                                launchHomeGame(
                                    gameId = gameId,
                                    loginSuccess = viewModel.loginSuccess,
                                    onGameClick = onGameClick,
                                    onNavigate = onNavigate,
                                    onRequireLogin = { showLoginPopup = true }
                                )
                            }
                        },
                        onGunduAtaChoice = {
                            if (viewModel.loginSuccess) {
                                showGunduAtaChoiceDialog = true
                            } else {
                                showLoginPopup = true
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Hot Games
                    SectionHeader(title = stringResource(R.string.hot_games))
                    HotGamesGrid(
                        viewModel = viewModel,
                        onGameClick = onGameClick,
                        onNavigate = onNavigate,
                        onRequireLogin = { showLoginPopup = true }
                    )
                } else {
                    // Search Results
                    SectionHeader(title = stringResource(R.string.search_results))
                    val games = homeScreenGames().filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                            it.id.contains(searchQuery, ignoreCase = true)
                    }
                    
                    if (games.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            games.chunked(2).forEach { rowGames ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowGames.forEach { game ->
                                        GameCard(
                                            game = game,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                if (game.id == "gundu_ata") {
                                                    if (viewModel.loginSuccess) {
                                                        showGunduAtaChoiceDialog = true
                                                    } else {
                                                        showLoginPopup = true
                                                    }
                                                } else {
                                                    launchHomeGame(
                                                        gameId = game.id,
                                                        loginSuccess = viewModel.loginSuccess,
                                                        onGameClick = onGameClick,
                                                        onNavigate = onNavigate,
                                                        onRequireLogin = { showLoginPopup = true }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    if (rowGames.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_games_found, searchQuery), color = TextGrey)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }

        }
    }
}

@Composable
fun HomeTopBar(
    viewModel: GunduAtaViewModel,
    balance: String,
    isLoggedIn: Boolean,
    onWalletClick: () -> Unit,
    onDepositClick: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    
    // Shimmering light pass effect
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by shimmerTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val textShimmerBrush = Brush.linearGradient(
        colors = listOf(
            PrimaryYellow,
            Color.White,
            PrimaryYellow
        ),
        start = androidx.compose.ui.geometry.Offset(shimmerTranslate, shimmerTranslate),
        end = androidx.compose.ui.geometry.Offset(shimmerTranslate + 200f, shimmerTranslate + 200f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlackBackground)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable { onNavigate("gundu_ata") }
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = "GUNDU ATA",
                style = androidx.compose.ui.text.TextStyle(
                    brush = textShimmerBrush,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth()
        ) {
            if (isLoggedIn) {
                // Balance Pill
                Surface(
                    color = SurfaceColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.clickable { onDepositClick() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("₹", color = PrimaryYellow, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(MoneyFormat.format(balance), color = TextWhite, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.AddBox,
                            contentDescription = "Add money",
                            tint = PrimaryYellow,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onDepositClick() }
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    TextButton(
                        onClick = { onNavigate("login") },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.login),
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { onNavigate("signup") },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.register),
                            color = BlackBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(onSearch: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
            .height(40.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextWhite,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearch(it)
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextWhite,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(PrimaryYellow),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                stringResource(R.string.search_games),
                                color = TextGrey,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = {
                        searchQuery = ""
                        onSearch("")
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextGrey,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromotionalBanners(
    viewModel: GunduAtaViewModel,
    onNavigate: (String) -> Unit
) {
    val pageCount = 6
    val virtualCount = 1000 * pageCount
    val pagerState = rememberPagerState(
        initialPage = virtualCount / 2,
        pageCount = { virtualCount }
    )

    var lastClickTime by remember { mutableStateOf(0L) }
    val clickCooldown = 1000L

    fun handleBannerClick(route: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > clickCooldown) {
            lastClickTime = currentTime
            onNavigate(route)
        }
    }

    fun handleBannerClickRequireLogin(route: String) {
        if (!viewModel.loginSuccess) {
            onNavigate("login")
            return
        }
        handleBannerClick(route)
    }

    LaunchedEffect(Unit) {
        while (true) {
            yield()
            delay(4000)
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 16.dp),
            pageSpacing = 16.dp
        ) { virtualPage ->
            val page = virtualPage % pageCount
            val banner = when(page) {
                0 -> BannerData("DAILY REWARD", "SPIN THE WHEEL FOR BONUS!", "SPIN NOW", listOf(Color(0xFFF9A825), Color(0xFFF57F17)), { handleBannerClickRequireLogin("lucky_wheel") })
                1 -> BannerData("REFER & EARN", "Earn up to ₹1 Lakh!", "INVITE", listOf(Color(0xFF455A64), Color(0xFF263238)), { handleBannerClickRequireLogin("affiliate") })
                2 -> BannerData("MEGA SPIN", "Deposit ₹2000 or more to spin the wheel!", "SPIN NOW", listOf(Color(0xFF4A148C), Color(0xFF880E4F)), { handleBannerClickRequireLogin("lucky_draw") })
                3 -> BannerData("USDT SPECIAL ₮", "Get 5% EXTRA CASHBACK on all USDT deposits!", "DEPOSIT NOW", listOf(Color(0xFF00897B), Color(0xFF004D40)), { handleBannerClick("deposit?method=USDT") })
                4 -> BannerData(stringResource(R.string.banner_cricket_title), stringResource(R.string.banner_cricket_subtitle), "BET NOW", listOf(Color(0xFF0D47A1), Color(0xFF1A237E)), { handleBannerClick("ipl") })
                else -> BannerData("FRANCHISE", "Get Gundu Ata franchise at 50% off — Get in touch today!", "LEARN MORE", listOf(Color(0xFF795548), Color(0xFF5D4037)), { handleBannerClick("white_label_account") })
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(banner.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Text(banner.title, color = PrimaryYellow, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text(banner.subtitle, color = TextWhite, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = banner.onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(banner.buttonText, color = BlackBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pageCount) { iteration ->
                val color = if (pagerState.currentPage % pageCount == iteration) PrimaryYellow else TextGrey
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }
    }
}

data class BannerData(
    val title: String,
    val subtitle: String,
    val buttonText: String,
    val gradient: List<Color>,
    val onClick: () -> Unit
)

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Quick-launch game icons row ─────────────────────────────────────────────

private fun homeScreenGames(): List<GameItem> = listOf(
    GameItem("GUNDU ATA", "gundu_ata", Color(0xFF1565C0)),
    GameItem("COLOUR GAME", "colour_game", Color(0xFF1A1A2E)),
    GameItem("HEAD & TAILS", "coin", Color(0xFFB8860B)),
    GameItem("CRICKET", "ipl", Color(0xFF0D47A1))
)

private fun launchHomeGame(
    gameId: String,
    loginSuccess: Boolean,
    onGameClick: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onRequireLogin: () -> Unit
) {
    when (gameId) {
        "ipl", "coin" -> onNavigate(gameId)
        "gundu_ata" -> {
            if (!loginSuccess) onRequireLogin()
            else onGameClick(gameId)
        }
        else -> onGameClick(gameId)
    }
}

private data class QuickGame(val id: String, val label: String, val iconRes: Int?, val gradient: List<Color>)

@Composable
fun QuickGamesRow(
    onGameClick: (String) -> Unit,
    onGunduAtaChoice: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val iplIconRes = context.resources.getIdentifier("ic_ipl_nav", "drawable", context.packageName).takeIf { it != 0 }
    val gunduIconRes = context.resources.getIdentifier("ic_gundu_ata_nav", "drawable", context.packageName).takeIf { it != 0 }

    val cricketLabel = stringResource(R.string.game_cricket)
    data class QuickEntry(val id: String, val label: String)
    val entries = listOf(
        QuickEntry("colour_game", "Colour Game"),
        QuickEntry("gundu_ata", "Gundu Ata"),
        QuickEntry("coin", "Head & Tails"),
        QuickEntry("ipl", cricketLabel)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        entries.forEach { entry ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (entry.id == "gundu_ata" && onGunduAtaChoice != null) {
                            onGunduAtaChoice()
                        } else {
                            onGameClick(entry.id)
                        }
                    }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(
                            when (entry.id) {
                                "ipl" -> Brush.linearGradient(listOf(Color(0xFF0D47A1), Color(0xFF1A237E)))
                                "gundu_ata" -> Brush.linearGradient(listOf(Color(0xFF0A1628), Color(0xFF1565C0)))
                                "coin" -> Brush.linearGradient(listOf(Color(0xFF3D2B00), Color(0xFFB8860B)))
                                else -> Brush.linearGradient(listOf(Color(0xFF0A0A0A), Color(0xFF1A1A1A)))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (entry.id) {
                        "ipl" -> {
                            // Cricket bat + T20 badge
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (iplIconRes != null) {
                                    Image(
                                        painter = painterResource(id = iplIconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp),
                                        colorFilter = ColorFilter.tint(Color(0xFFFFCA28))
                                    )
                                } else {
                                    Text("T20", color = Color(0xFFFFCA28), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                }
                                Text("🏏", fontSize = 10.sp)
                            }
                        }
                        "gundu_ata" -> {
                            if (gunduIconRes != null) {
                                Image(
                                    painter = painterResource(id = gunduIconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            } else {
                                Text("🎲", fontSize = 22.sp)
                            }
                        }
                        "coin" -> {
                            // Gold coin split H | T
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFD700)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Color(0xFFFFD700)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("H", color = Color(0xFF3D2B00), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                    }
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF3D2B00)))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(Color(0xFFFFA000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("T", color = Color(0xFF3D2B00), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        "colour_game" -> {
                            // Three colour circles matching the game buttons
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                                    Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFF7C3AED)))
                                }
                                Box(
                                    modifier = Modifier.size(13.dp).clip(CircleShape).background(Color(0xFFDC2626)),
                                    contentAlignment = Alignment.Center
                                ) {}
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    entry.label,
                    color = TextWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun HotGamesGrid(
    viewModel: GunduAtaViewModel,
    onGameClick: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onRequireLogin: () -> Unit
) {
    val games = listOf(
        GameItem("GUNDU ATA", "gundu_ata", Color(0xFF1565C0))
    )
    val context = LocalContext.current
    
    // List of fake winning names and amounts
    val baseWinnings = remember {
        listOf(
            "Muktesh", "Sai Krishna", "Mahesh", "Rahul", "Priya", "Vikram", "Anjali", "Suresh", "Kiran", "Deepak",
            "Amit", "Sneha", "Rohan", "Neha", "Arjun", "Pooja", "Karan", "Ishita", "Sanjay", "Ritu",
            "Vijay", "Anita", "Rajesh", "Sunita", "Manoj", "Kavita", "Vinay", "Meena", "Sandeep", "Rekha",
            "Abhishek", "Swati", "Prashant", "Aarti", "Alok", "Shweta", "Vivek", "Jyoti", "Ashish", "Priyanka",
            "Manish", "Rani", "Dinesh", "Sonia", "Harish", "Preeti", "Naveen", "Madhu", "Pankaj", "Seema",
            "Rakesh", "Anu", "Om", "Lata", "Ram", "Gita", "Shyam", "Radha", "Krishna", "Meera",
            "Bala", "Lakshmi", "Murugan", "Parvati", "Ganesh", "Saraswati", "Kartik", "Durga", "Shiva", "Kali",
            "Mohan", "Indira", "Jawahar", "Sarojini", "Subhash", "Aruna", "Bhagat", "Kamala", "Sardar", "Kasturba",
            "Vikram", "Kalpana", "Homi", "Shakuntala", "C.V. Raman", "Janaki", "Visvesvaraya", "Asima", "Srinivasa", "Tessy",
            "Sachin", "Mithali", "Virat", "Mary Kom", "Dhoni", "Saina", "Kapil", "Sindhu", "Sunil", "Dipa",
            "Aamir", "Deepika", "Shah Rukh", "Alia", "Salman", "Priyanka", "Akshay", "Kareena", "Hrithik", "Katrina",
            "Ranbir", "Anushka", "Ranveer", "Sonam", "Varun", "Shraddha", "Siddharth", "Jacqueline", "Tiger", "Disha",
            "Ayushmann", "Taapsee", "Rajkummar", "Bhumi", "Vicky", "Kriti", "Kartik", "Kiara", "Ishaan", "Sara",
            "Aditya", "Janhvi", "Ananya", "Tara", "Ishaan", "Rakul", "Vijay", "Rashmika", "Dulquer", "Sai Pallavi",
            "Prabhas", "Samantha", "Mahesh Babu", "Nayanthara", "Allu Arjun", "Keerthy", "Ram Charan", "Trisha", "NTR Jr", "Tamannaah",
            "Yash", "Pooja Hegde", "Sudeep", "Anupama", "Darshan", "Rashmika", "Puneeth", "Srinidhi", "Rishab", "Sapthami",
            "Fahadh", "Nazriya", "Prithviraj", "Parvathy", "Nivin", "Manju", "Tovino", "Keerthy", "Dulquer", "Aishwarya",
            "Suriya", "Jyothika", "Karthi", "Nayanthara", "Dhanush", "Sai Pallavi", "Vijay Sethupathi", "Keerthy", "Sivakarthikeyan", "Trisha",
            "Mammootty", "Shobana", "Mohanlal", "Revathi", "Jayaram", "Urvashi", "Suresh Gopi", "Geetha", "Dileep", "Kavya",
            "Amir", "Zahra", "Omar", "Fatima", "Ali", "Maryam", "Hassan", "Aisha", "Hussein", "Khadija",
            "John", "Mary", "David", "Sarah", "Michael", "Elizabeth", "James", "Jennifer", "Robert", "Linda",
            "William", "Barbara", "Richard", "Susan", "Joseph", "Jessica", "Thomas", "Margaret", "Charles", "Karen",
            "Christopher", "Nancy", "Daniel", "Lisa", "Matthew", "Betty", "Anthony", "Dorothy", "Mark", "Sandra",
            "Donald", "Ashley", "Steven", "Kimberly", "Paul", "Donna", "Andrew", "Emily", "Joshua", "Michelle",
            "Kenneth", "Carol", "Kevin", "Amanda", "Brian", "Melissa", "George", "Deborah", "Timothy", "Stephanie",
            "Ronald", "Rebecca", "Edward", "Laura", "Jason", "Sharon", "Jeffrey", "Cynthia", "Ryan", "Kathleen",
            "Jacob", "Amy", "Gary", "Shirley", "Nicholas", "Angela", "Eric", "Helen", "Jonathan", "Anna",
            "Stephen", "Brenda", "Larry", "Pamela", "Justin", "Nicole", "Scott", "Emma", "Brandon", "Samantha",
            "Benjamin", "Katherine", "Samuel", "Christine", "Gregory", "Debra", "Alexander", "Rachel", "Frank", "Catherine",
            "Patrick", "Carolyn", "Raymond", "Janet", "Jack", "Ruth", "Dennis", "Maria", "Jerry", "Heather",
            "Tyler", "Diane", "Aaron", "Virginia", "Jose", "Julie", "Adam", "Joyce", "Nathan", "Victoria",
            "Henry", "Olivia", "Douglas", "Kelly", "Zachary", "Christina", "Peter", "Lauren", "Kyle", "Joan",
            "Ethan", "Evelyn", "Walter", "Judith", "Noah", "Megan", "Jeremy", "Cheryl", "Christian", "Andrea",
            "Keith", "Hannah", "Roger", "Martha", "Terry", "Jacqueline", "Gerald", "Frances", "Harold", "Gloria",
            "Sean", "Ann", "Austin", "Teresa", "Carl", "Kathryn", "Arthur", "Sara", "Lawrence", "Janice",
            "Dylan", "Jean", "Jesse", "Alice", "Jordan", "Madison", "Bryan", "Doris", "Billy", "Abigail",
            "Joe", "Julia", "Bruce", "Judy", "Gabriel", "Grace", "Logan", "Denise", "Albert", "Amber",
            "Willie", "Marilyn", "Alan", "Beverly", "Juan", "Danielle", "Wayne", "Theresa", "Elijah", "Sophia",
            "Randy", "Marie", "Roy", "Diana", "Vincent", "Brittany", "Ralph", "Natalie", "Eugene", "Isabella",
            "Russell", "Charlotte", "Bobby", "Rose", "Mason", "Alexis", "Philip", "Kayla", "Louis", "Alice",
            "Aarav", "Aanya", "Vivaan", "Diya", "Aditya", "Pari", "Vihaan", "Ananya", "Arjun", "Saanvi",
            "Sai", "Ira", "Reyansh", "Avni", "Krishna", "Prisha", "Ishaan", "Riya", "Shaurya", "Aadhya",
            "Aryan", "Myra", "Ayush", "Anika", "Atharv", "Navya", "Ganesh", "Kavya", "Advait", "Ishani",
            "Kabir", "Zoya", "Tushar", "Kiara", "Naksh", "Sara", "Arnav", "Vanya", "Rudr", "Shanaya",
            "Shivansh", "Kyra", "Kian", "Siya", "Veer", "Inaya", "Aaryan", "Aavya", "Rudra", "Amaira",
            "Vedant", "Mishka", "Kush", "Anvi", "Yash", "Aarna", "Dev", "Sana", "Rohan", "Zara",
            "Aadi", "Hazel", "Dhruv", "Aayat", "Kabir", "Meher", "Viaan", "Amaya", "Darsh", "Kaira",
            "Ranbir", "Miraya", "Agastya", "Riddhima", "Abeer", "Anaya", "Yuvan", "Shanaya", "Ishaan", "Zoya"
        )
    }
    
    val winnings = remember(baseWinnings, viewModel.userProfile, viewModel.bettingHistory) {
        val list = baseWinnings.map { "$it +${(100..5000).random()}" }.toMutableList()
        
        // Add current user if they have placed a bet
        val currentUser = viewModel.userProfile?.username
        if (currentUser != null && viewModel.bettingHistory.isNotEmpty()) {
            list.add(0, "$currentUser +${(100..2000).random()}")
        }
        list
    }
    
    // State to track active winning particles
    val activeWinnings = remember { mutableStateListOf<WinningParticle>() }
    var nextId by remember { mutableIntStateOf(0) }
    
    // Detect resume from recent tabs - add 2s delay before sending names to fix glitch
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasBeenPaused by remember { mutableStateOf(false) }
    var resumeTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasBeenPaused = true
                Lifecycle.Event.ON_RESUME -> {
                    if (hasBeenPaused) {
                        resumeTrigger++
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    LaunchedEffect(resumeTrigger) {
        activeWinnings.clear()
        // 2 second delay when resuming from recent tabs to prevent glitch
        if (resumeTrigger > 0) {
            delay(2000)
        }
        while (true) {
            val name = if (winnings.isNotEmpty()) winnings.random() else "Player +100"
            activeWinnings.add(WinningParticle(id = nextId++, text = name))
            delay(1200) // Spawn a new name every 1.2 seconds for continuous flow
        }
    }
    
    var showGunduAtaDialog by remember { mutableStateOf(false) }
    if (showGunduAtaDialog) {
        GunduAtaChoiceDialog(
            onDismiss = { showGunduAtaDialog = false },
            onPlayLive = { onNavigate("gundu_ata_live") },
            onPlayNormal = { onGameClick("gundu_ata") }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp)
            .clipToBounds()
    ) {
        // Hot Games — Gundu Ata banner only
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            games.forEach { game ->
                GameCard(
                    game = game,
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .padding(horizontal = 4.dp),
                    cardAspectRatio = 0.75f,
                    onClick = {
                        if (!viewModel.loginSuccess) {
                            onRequireLogin()
                        } else if (game.id == "gundu_ata") {
                            showGunduAtaDialog = true
                        } else {
                            onGameClick(game.id)
                        }
                    }
                )
            }
        }

        // Customer Support Icon - opposite side of treasury box (left), dropdown shows only WhatsApp & Telegram
        var supportMenuExpanded by remember { mutableStateOf(false) }
        var supportMenuClosing by remember { mutableStateOf(false) }  // Keeps popup in tree during exit animation
        val scaleAlpha = remember { Animatable(0f) }
        // Support contacts from https://gunduata.tech/api/support/contacts/?package=<applicationId>
        var supportWhatsApp by remember { mutableStateOf<String?>(null) }
        var supportTelegram by remember { mutableStateOf<String?>(null) }
        val supportPackageName = LocalContext.current.packageName
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.apiService.getSupportContacts(supportPackageName)
                    if (response.isSuccessful) {
                        response.body()?.let { c ->
                            supportWhatsApp = c.whatsapp_number
                            supportTelegram = c.telegram
                        }
                    }
                } catch (_: Exception) { }
            }
        }
        val scope = rememberCoroutineScope()
        val showPopup = supportMenuExpanded || supportMenuClosing
        // Auto-open only when app was just opened (cold start or resumed from background), not when navigating within app (e.g. profile -> home)
        LaunchedEffect(viewModel.showSupportPopupOnNextHomeVisit) {
            if (viewModel.showSupportPopupOnNextHomeVisit) {
                supportMenuExpanded = true
                viewModel.markSupportPopupShown()
            }
        }
        // Animate open, show for 6 seconds, then close slowly and smoothly
        LaunchedEffect(supportMenuExpanded) {
            if (supportMenuExpanded) {
                supportMenuClosing = false
                scaleAlpha.snapTo(0f)
                scaleAlpha.animateTo(1f, animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing))
                delay(6000)
                supportMenuClosing = true  // Keep in tree during exit
                scaleAlpha.animateTo(0f, animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing))
                supportMenuExpanded = false
                supportMenuClosing = false
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(y = 28.dp, x = (-16).dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (!showPopup) {
                IconButton(
                    onClick = { supportMenuExpanded = true; supportMenuClosing = false },
                    modifier = Modifier
                        .size(48.dp)
                        .background(SurfaceColor, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "Customer Support",
                        tint = PrimaryYellow,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (showPopup) {
                val density = LocalDensity.current
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, with(density) { (-100).dp.roundToPx() }),
                    onDismissRequest = {
                        if (!supportMenuClosing) scope.launch {
                            supportMenuClosing = true
                            scaleAlpha.animateTo(0f, animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing))
                            supportMenuExpanded = false
                            supportMenuClosing = false
                        }
                    },
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true  // Close when user taps on empty area outside the popup
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = scaleAlpha.value,
                                scaleY = scaleAlpha.value,
                                alpha = scaleAlpha.value,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            )
                            .background(BlackBackground)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = {
                                supportMenuExpanded = false
                                supportWhatsApp?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.let { digits ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW)
                                        intent.data = Uri.parse("https://wa.me/$digits")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {}
                                }
                            },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF25D366).copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_whatsapp),
                                        contentDescription = "WhatsApp",
                                        modifier = Modifier.size(26.dp),
                                        tint = Color(0xFF25D366)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                supportMenuExpanded = false
                                supportTelegram?.takeIf { it.isNotBlank() }?.let { handle ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW)
                                        intent.data = Uri.parse(
                                            if (handle.all { it.isDigit() }) "https://t.me/+$handle"
                                            else "https://t.me/$handle"
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {}
                                }
                            },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0088cc).copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_telegram),
                                        contentDescription = "Telegram",
                                        modifier = Modifier.size(26.dp),
                                        tint = Color(0xFF0088cc)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

                // Treasury Box Icon and Continuous Winnings Animation
                val treasuryBoxId = context.resources.getIdentifier("ic_treasury_box", "drawable", context.packageName)
                if (treasuryBoxId != 0) {
                    var lastTreasuryClickTime by remember { mutableStateOf(0L) }
                    val onTreasuryClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastTreasuryClickTime > 800L) {
                            lastTreasuryClickTime = now
                            if (viewModel.loginSuccess) {
                                onNavigate("leaderboard")
                            } else {
                                onRequireLogin()
                            }
                        }
                    }

                    // Real-box shake animation logic
                    val infiniteTransition = rememberInfiniteTransition(label = "shake")
                    
                    // Rotation for side-to-side wobble
                    val shakeRotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 6000 // Total cycle: 6 seconds
                                0f at 0
                                0f at 5500 // Wait for 5.5 seconds
                                -8f at 5600 // Start shaking
                                8f at 5700
                                -6f at 5800
                                6f at 5900
                                0f at 6000 // End shake
                            },
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shakeRotation"
                    )

                    // Scale for a "jumpy" effect
                    val shakeScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 6000 // Total cycle: 6 seconds
                                1f at 0
                                1f at 5500
                                1.15f at 5650
                                1f at 5800
                                1.1f at 5900
                                1f at 6000
                            },
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shakeScale"
                    )

                    // Single stable tap target — shake is visual-only so clicks don't miss the box
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(88.dp)
                            .fillMaxHeight()
                            .zIndex(10f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onTreasuryClick
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Continuous Winnings Particles container (STABLE - No shake)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .align(Alignment.BottomCenter)
                                .clipToBounds(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            activeWinnings.forEach { particle ->
                                key(particle.id) {
                                    WinningTextParticle(
                                        text = particle.text,
                                        onAnimationFinished = { activeWinnings.remove(particle) }
                                    )
                                }
                            }
                        }

                        // Treasury Box Image (SHAKING) — visual only; parent handles taps
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 12.dp)
                                .graphicsLayer(
                                    rotationZ = shakeRotation,
                                    scaleX = shakeScale,
                                    scaleY = shakeScale,
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                )
                        ) {
                            Image(
                                painter = painterResource(id = treasuryBoxId),
                                contentDescription = "Treasury Box",
                                modifier = Modifier.size(68.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
    }
}

data class WinningParticle(val id: Int, val text: String)

@Composable
fun WinningTextParticle(text: String, onAnimationFinished: () -> Unit) {
    // Animation state for vertical movement and alpha
    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
        )
        onAnimationFinished()
    }
    
    // Calculate offset and alpha based on progress
    // Start from y=0 (inside box) and move up to -100dp
    val yOffset = - (animProgress.value * 100).dp 
    
    // Fade in quickly at start, then stay visible, then fade out at end
    val alpha = when {
        animProgress.value < 0.1f -> animProgress.value / 0.1f // Faster fade in
        animProgress.value > 0.7f -> 1f - (animProgress.value - 0.7f) / 0.3f // Fade out
        else -> 1f
    }
    
    // Scale up slightly as it "pops" out of the box
    val scale = if (animProgress.value < 0.2f) {
        0.5f + (animProgress.value / 0.2f) * 0.5f
    } else 1f
    
    Text(
        text = text,
        color = PrimaryYellow,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .offset(y = yOffset)
            .graphicsLayer(
                alpha = alpha,
                scaleX = scale,
                scaleY = scale
            )
    )
}

@Composable
fun GunduAtaChoiceDialog(
    onDismiss: () -> Unit,
    onPlayLive: () -> Unit,
    onPlayNormal: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceColor,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Choose Game Mode",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(16.dp))

                // Live option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlayLive(); onDismiss() },
                    color = Color(0xFF1A0A0A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFEF5350).copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF5350).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔴", fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Gundu Ata",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFEF5350),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "LIVE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "Join scheduled live rounds",
                                color = TextGrey,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFFEF5350)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Normal / anytime option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlayNormal(); onDismiss() },
                    color = Color(0xFF0A1A0A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, PrimaryYellow.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PrimaryYellow.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Casino,
                                contentDescription = null,
                                tint = PrimaryYellow,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Gundu Ata",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Play anytime at your own pace",
                                color = TextGrey,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = PrimaryYellow
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = TextGrey)
                }
            }
        }
    }
}

data class GameItem(val name: String, val id: String, val color: Color)

@Composable
fun GameCard(game: GameItem, modifier: Modifier, onClick: () -> Unit, cardAspectRatio: Float = 0.7f) {
    Box(
        modifier = modifier.clickable { onClick() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(cardAspectRatio)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(game.color),
                contentAlignment = Alignment.Center
            ) {
                when (game.id) {
                    "gundu_ata" -> VideoPlayer(videoResId = R.raw.gundu_ata_video, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f))
                    "colour_game" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF064E3B),
                                            Color(0xFF4C1D95),
                                            Color(0xFF7F1D1D)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF7C3AED)))
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFDC2626)))
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(R.string.colour_game_title),
                                    color = PrimaryYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                    "coin" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF3D2B00), Color(0xFFB8860B))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.heads_coin),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    "ipl" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF0D47A1), Color(0xFF1A237E))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(R.drawable.ic_ipl_nav),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    colorFilter = ColorFilter.tint(Color(0xFFFFCA28))
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.game_cricket), color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                    "cock_fight" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF5C1A08), Color(0xFF8B4513))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🐓", fontSize = 44.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.cock_fight_title),
                                    color = PrimaryYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        Image(
                            painter = painterResource(id = R.drawable.gundu_ata_bg),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            game.name,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(game.name, color = TextGrey, fontSize = 14.sp)
        }
    }
}

@Composable
fun VideoPlayer(videoResId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    val released = remember { booleanArrayOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/$videoResId")
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = false
            prepare()
        }
    }

    fun releasePlayer() {
        if (released[0]) return
        released[0] = true
        playerViewRef.value?.player = null
        playerViewRef.value = null
        try {
            exoPlayer.playWhenReady = false
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        } catch (_: Exception) {
            // already released
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!released[0]) exoPlayer.playWhenReady = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (!released[0]) exoPlayer.playWhenReady = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releasePlayer()
        }
    }

    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.home_video_player_view, null, false) as PlayerView).also { view ->
                view.player = exoPlayer
                playerViewRef.value = view
            }
        },
        update = { view ->
            if (!released[0] && view.player !== exoPlayer) {
                view.player = exoPlayer
            }
            playerViewRef.value = view
        },
        onRelease = { view ->
            view.player = null
            if (playerViewRef.value === view) playerViewRef.value = null
        },
        modifier = modifier
    )
}

@Composable
fun HomeBottomNavigation(currentRoute: String, viewModel: GunduAtaViewModel, onNavigate: (String) -> Unit) {
    var showLoginPopup by remember { mutableStateOf(false) }
    var showGunduAtaDialog by remember { mutableStateOf(false) }
    var lastGameLaunchTime by remember { mutableStateOf(0L) }
    val gameLaunchCooldown = 1500L // Prevent double-tap crash

    if (showGunduAtaDialog) {
        GunduAtaChoiceDialog(
            onDismiss = { showGunduAtaDialog = false },
            onPlayLive = { onNavigate("gundu_ata_live") },
            onPlayNormal = { onNavigate("gundu_ata") }
        )
    }

    if (showLoginPopup) {
        AlertDialog(
            onDismissRequest = { showLoginPopup = false },
            title = { Text(stringResource(R.string.login_required), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.login_required_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginPopup = false
                        onNavigate("signup")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow)
                ) {
                    Text(stringResource(R.string.sign_up), color = BlackBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginPopup = false }) {
                    Text(stringResource(R.string.cancel), color = TextGrey)
                }
            },
            containerColor = SurfaceColor,
            titleContentColor = TextWhite,
            textContentColor = TextGrey
        )
    }

    NavigationBar(
        containerColor = BottomNavBackground,
        tonalElevation = 8.dp
    ) {
        val allNavItems = listOf(
            BottomNavItem("Home", "home", Icons.Default.Home),
            BottomNavItem("GUNDU ATA", "gundu_ata", Icons.Default.Casino),
            BottomNavItem("Me", "me", Icons.Default.AccountCircle)
        )
        val items = allNavItems

        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { 
                    if (currentRoute != item.route) {
                        when (item.route) {
                            "gundu_ata" -> {
                                if (!viewModel.loginSuccess) {
                                    showLoginPopup = true
                                } else {
                                    showGunduAtaDialog = true
                                }
                            }
                            "me" -> {
                                if (!viewModel.loginSuccess) {
                                    showLoginPopup = true
                                } else {
                                    onNavigate(item.route)
                                }
                            }
                            else -> onNavigate(item.route)
                        }
                    }
                },
                icon = { 
                    if (item.route == "gundu_ata") {
                        val context = LocalContext.current
                        val diceIconId = context.resources.getIdentifier("ic_gundu_ata_nav", "drawable", context.packageName)
                        if (diceIconId != 0) {
                            Image(
                                painter = painterResource(id = diceIconId),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                contentScale = ContentScale.Fit,
                                colorFilter = if (currentRoute == item.route) null else androidx.compose.ui.graphics.ColorFilter.tint(TextGrey)
                            )
                        } else {
                            Icon(item.icon, contentDescription = null)
                        }
                    } else {
                        Icon(item.icon, contentDescription = null)
                    }
                },
                label = { Text(item.name) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryYellow,
                    selectedTextColor = PrimaryYellow,
                    unselectedIconColor = TextGrey,
                    unselectedTextColor = TextGrey,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun WhatsAppSupportButton() {
    val context = LocalContext.current
    var whatsappNumber by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getSupportContacts(context.packageName)
                if (response.isSuccessful) whatsappNumber = response.body()?.whatsapp_number
            } catch (_: Exception) {}
        }
    }
    val digits = whatsappNumber?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(if (digits != null) Modifier.clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("https://wa.me/$digits")
                    context.startActivity(intent)
                } catch (e: Exception) {}
            } else Modifier),
        color = Color(0xFF25D366), // WhatsApp Green
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_whatsapp),
                contentDescription = "WhatsApp",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Contact Support on WhatsApp",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

data class BottomNavItem(val name: String, val route: String, val icon: ImageVector)
