package com.sikwin.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sikwin.app.R
import com.sikwin.app.data.api.RetrofitClient
import com.sikwin.app.data.models.CricketUpcomingMatch
import com.sikwin.app.ui.theme.CricketOutcomeBlue
import com.sikwin.app.ui.theme.CricketOutcomePink
import com.sikwin.app.ui.theme.GoldOnWhite
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import com.sikwin.app.utils.CasinoPrefetcher
import com.sikwin.app.utils.MoneyFormat
import kotlinx.coroutines.launch

private val WhiteBg = Color(0xFFFFFFFF)
private val WhiteText = Color(0xFF111111)
private val WhiteMuted = Color(0xFF6B7280)
private val WhiteBorder = Color(0xFFE5E7EB)
private val WhiteGold = GoldOnWhite

private data class WhiteCasinoTile(
    val label: String,
    val imageRes: Int,
    val route: String,
    val category: WhiteGameCategory
)

private enum class WhiteGameCategory { SPORTS, CRASH, CARD }

private data class WhiteHomeCategory(
    val id: WhiteGameCategory,
    val title: String,
    val viewAllRoute: String,
    val tiles: List<WhiteCasinoTile> = emptyList()
)

private enum class WhiteCategoryLayout { HorizontalRow, Grid }

internal enum class WhiteHomeTab { HOME, LIVE, CASINO, WALLET, PROFILE }

@Composable
fun WhiteHomeScreen(
    viewModel: GunduAtaViewModel,
    onGameClick: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var showLoginPopup by remember { mutableStateOf(false) }
    var showGunduAtaChoiceDialog by remember { mutableStateOf(false) }
    var showSideMenu by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(WhiteHomeTab.HOME) }

    if (showGunduAtaChoiceDialog) {
        GunduAtaChoiceDialog(
            onDismiss = { showGunduAtaChoiceDialog = false },
            onPlayLive = { onNavigate("gundu_ata_live") },
            onPlayNormal = { onNavigate("gundu_ata_web") }
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
                    Text(stringResource(R.string.sign_up), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginPopup = false }) {
                    Text(stringResource(R.string.cancel), color = WhiteMuted)
                }
            },
            containerColor = WhiteBg,
            titleContentColor = WhiteText,
            textContentColor = WhiteMuted
        )
    }

    fun requireLoginOr(action: () -> Unit) {
        if (!viewModel.loginSuccess) showLoginPopup = true else action()
    }

    BackHandler(enabled = showSideMenu) { showSideMenu = false }

    LaunchedEffect(viewModel.loginSuccess) {
        if (viewModel.loginSuccess) {
            val token = RetrofitClient.getSessionManager()?.fetchAuthToken()
            CasinoPrefetcher.prefetchOnHome(context, token)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkSession()
                if (viewModel.loginSuccess) {
                    viewModel.fetchWallet()
                    viewModel.fetchProfile()
                    viewModel.startTimerPreloading()
                }
                viewModel.startCricketSession()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopTimerPreloading()
                viewModel.stopCricketSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopTimerPreloading()
            viewModel.stopCricketSession()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startCricketSession()
        viewModel.cricketFetchUpcomingOnce()
    }

    val casinoTiles = remember {
        listOf(
            WhiteCasinoTile("ROULETTE", R.drawable.card_roulette_home, "roulette", WhiteGameCategory.CARD),
            WhiteCasinoTile("TEEN PATTI", R.drawable.card_chit_pat, "coin", WhiteGameCategory.CARD),
            WhiteCasinoTile("AVIATOR", R.drawable.card_aviator, "casino_games", WhiteGameCategory.CRASH),
            WhiteCasinoTile("CHICKEN ROAD", R.drawable.card_chicken_road, "chicken_road", WhiteGameCategory.CRASH),
            WhiteCasinoTile("VORTEX", R.drawable.card_vortex, "vortex", WhiteGameCategory.CRASH),
            WhiteCasinoTile("MINES", R.drawable.card_mines, "casino_games", WhiteGameCategory.CRASH)
        )
    }
    val crashTiles = remember(casinoTiles) { casinoTiles.filter { it.category == WhiteGameCategory.CRASH } }
    val cardTiles = remember(casinoTiles) { casinoTiles.filter { it.category == WhiteGameCategory.CARD } }
    val homeCategories = remember(crashTiles, cardTiles) {
        listOf(
            WhiteHomeCategory(WhiteGameCategory.SPORTS, "Sports", "sports?sport=cricket"),
            WhiteHomeCategory(WhiteGameCategory.CRASH, "Crash Games", "casino_games", crashTiles),
            WhiteHomeCategory(WhiteGameCategory.CARD, "Card Games", "casino_games", cardTiles)
        )
    }

    fun openCasinoTile(route: String) {
        requireLoginOr { onNavigate(route) }
    }

    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = WhiteBg,
            bottomBar = {
                WhiteBottomBar(
                    selectedTab = selectedTab,
                    onHome = {
                        selectedTab = WhiteHomeTab.HOME
                        scrollScope.launch { scrollState.scrollTo(0) }
                    },
                    onLive = { onNavigate("sports?sport=cricket") },
                    onCasino = { requireLoginOr { onNavigate("casino_games") } },
                    onWallet = { requireLoginOr { onNavigate("wallet") } },
                    onProfile = { onNavigate("me") }
                )
            }
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            val contentTop = (padding.calculateTopPadding() - 10.dp).coerceAtLeast(0.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        PaddingValues(
                            start = padding.calculateStartPadding(layoutDirection),
                            end = padding.calculateEndPadding(layoutDirection),
                            bottom = padding.calculateBottomPadding(),
                            top = contentTop
                        )
                    )
                    .verticalScroll(scrollState)
            ) {
                WhiteTopBar(
                    balance = viewModel.wallet?.balance ?: "0.00",
                    isLoggedIn = viewModel.loginSuccess,
                    onMenu = { showSideMenu = true },
                    onAddMoney = { requireLoginOr { onNavigate("deposit") } },
                    onLogin = { onNavigate("login") }
                )

                HomePromoBannerCarousel(
                    banners = listOf(
                        HomePromoBanner(
                            imageRes = R.drawable.white_banner_roulette,
                            onPlayNow = { requireLoginOr { onNavigate("roulette") } }
                        ),
                        HomePromoBanner(
                            imageRes = R.drawable.white_banner_cockfight,
                            onPlayNow = { requireLoginOr { onNavigate("cock_fight") } }
                        ),
                        HomePromoBanner(
                            imageRes = R.drawable.white_banner_bonus,
                            onPlayNow = { requireLoginOr { onNavigate("deposit") } }
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                homeCategories.forEach { category ->
                    when (category.id) {
                        WhiteGameCategory.SPORTS -> {
                            WhiteSportsCategorySection(
                                matches = viewModel.cricketUpcoming,
                                showEmpty = !viewModel.cricketUpcomingLoading,
                                error = viewModel.cricketUpcomingError,
                                onViewAll = { onNavigate(category.viewAllRoute) },
                                onMatchClick = { onNavigate(category.viewAllRoute) }
                            )
                        }
                        else -> {
                            WhiteCategoryGamesSection(
                                title = category.title,
                                tiles = category.tiles,
                                layout = WhiteCategoryLayout.Grid,
                                onViewAll = { requireLoginOr { onNavigate(category.viewAllRoute) } },
                                onTileClick = { openCasinoTile(it.route) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        SideMenuOverlay(
            open = showSideMenu,
            onClose = { showSideMenu = false },
            onNavigate = onNavigate,
            onPlayGunduAta = { showGunduAtaChoiceDialog = true },
            requireLoginOr = { action -> requireLoginOr(action) }
        )
    }
}

@Composable
private fun WhiteSportsCategorySection(
    matches: List<CricketUpcomingMatch>,
    showEmpty: Boolean,
    error: String? = null,
    onViewAll: () -> Unit,
    onMatchClick: () -> Unit
) {
    WhiteSectionHeader(title = "Sports", onViewAll = onViewAll)
    Spacer(modifier = Modifier.height(8.dp))
    matches.take(3).forEach { match ->
        WhiteUpcomingMatchRow(match = match, onClick = onMatchClick)
        Spacer(modifier = Modifier.height(8.dp))
    }
    if (matches.isEmpty() && showEmpty) {
        Text(
            error ?: stringResource(R.string.white_no_upcoming),
            color = if (error != null) Color(0xFFDC2626) else WhiteMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun WhiteCategoryGamesSection(
    title: String,
    tiles: List<WhiteCasinoTile>,
    layout: WhiteCategoryLayout,
    onViewAll: () -> Unit,
    onTileClick: (WhiteCasinoTile) -> Unit
) {
    WhiteSectionHeader(title = title, onViewAll = onViewAll)
    Spacer(modifier = Modifier.height(8.dp))
    when (layout) {
        WhiteCategoryLayout.HorizontalRow -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tiles.forEach { tile ->
                    WhiteCasinoGameTile(
                        tile = tile,
                        modifier = Modifier
                            .width(132.dp)
                            .height(110.dp),
                        onClick = { onTileClick(tile) }
                    )
                }
            }
        }
        WhiteCategoryLayout.Grid -> {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tiles.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { tile ->
                            WhiteCasinoGameTile(
                                tile = tile,
                                modifier = Modifier.weight(1f),
                                onClick = { onTileClick(tile) }
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun WhiteTopBar(
    balance: String,
    isLoggedIn: Boolean,
    onMenu: () -> Unit,
    onAddMoney: () -> Unit,
    onLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .reducedStatusBarsPadding(reduce = 18.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Menu, contentDescription = null, tint = WhiteText)
        }
        Text(
            text = "Pride",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = GoldOnWhite,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp
        )
        if (isLoggedIn) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF3F4F6))
                    .border(1.dp, WhiteBorder, RoundedCornerShape(20.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    MoneyFormat.formatRupee(balance),
                    color = WhiteText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(PrimaryYellow)
                        .clickable(onClick = onAddMoney),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            TextButton(onClick = onLogin) {
                Text(stringResource(R.string.login), color = WhiteText, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = { }) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = WhiteText)
        }
    }
}

@Composable
private fun WhiteUpcomingMatchRow(
    match: CricketUpcomingMatch,
    onClick: () -> Unit
) {
    val outcomes = match.previewOutcomes().take(2)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WhiteBg)
            .border(1.dp, WhiteBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                match.match.orEmpty(),
                color = WhiteText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            match.competition?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = WhiteMuted, fontSize = 11.sp)
            }
        }
        Row(
            modifier = Modifier.width(116.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            outcomes.forEachIndexed { index, o ->
                val bg = if (index % 2 == 0) CricketOutcomeBlue else CricketOutcomePink
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            o.displayLabel(),
                            color = Color(0xFF1A1A1A),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            o.displayOdds(),
                            color = Color(0xFF111111),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhiteSectionHeader(title: String, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title.uppercase(), color = WhiteText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            stringResource(R.string.view_all),
            color = WhiteGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onViewAll)
        )
    }
}

@Composable
private fun WhiteCasinoGameTile(
    tile: WhiteCasinoTile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(id = tile.imageRes),
            contentDescription = tile.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(tile.label, color = WhiteGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

/** Pull content slightly under the status bar without overlapping icons. */
@Composable
private fun Modifier.reducedStatusBarsPadding(reduce: Dp): Modifier {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return padding(top = (top - reduce).coerceAtLeast(0.dp))
}

@Composable
internal fun WhiteBottomBar(
    selectedTab: WhiteHomeTab,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onCasino: () -> Unit,
    onWallet: () -> Unit,
    onProfile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteBg)
            .border(width = 1.dp, color = WhiteBorder)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WhiteNavItem(
                label = "HOME",
                icon = Icons.Default.Home,
                selected = selectedTab == WhiteHomeTab.HOME,
                onClick = onHome,
                modifier = Modifier.weight(1f)
            )
            WhiteNavItem(
                label = "LIVE",
                icon = Icons.Default.Bolt,
                selected = selectedTab == WhiteHomeTab.LIVE,
                onClick = onLive,
                modifier = Modifier.weight(1f)
            )
            WhiteNavItem(
                label = "CASINO",
                icon = Icons.Default.Casino,
                selected = selectedTab == WhiteHomeTab.CASINO,
                onClick = onCasino,
                modifier = Modifier.weight(1f)
            )
            WhiteNavItem(
                label = "WALLET",
                icon = Icons.Default.AccountBalanceWallet,
                selected = selectedTab == WhiteHomeTab.WALLET,
                onClick = onWallet,
                modifier = Modifier.weight(1f)
            )
            WhiteNavItem(
                label = "PROFILE",
                icon = Icons.Default.Person,
                selected = selectedTab == WhiteHomeTab.PROFILE,
                onClick = onProfile,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WhiteNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) GoldOnWhite else WhiteMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (selected) WhiteText else WhiteMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 9.sp,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}
