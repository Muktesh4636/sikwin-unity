package com.sikwin.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.Dp
import com.sikwin.app.R
import com.sikwin.app.data.prefs.ThemePreferences
import com.sikwin.app.ui.theme.*
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import com.sikwin.app.utils.MoneyFormat

@Composable
fun ProfileScreen(
    viewModel: GunduAtaViewModel,
    sessionManager: com.sikwin.app.data.auth.SessionManager,
    onNavigate: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Redirect to login if not logged in
    LaunchedEffect(viewModel.loginSuccess) {
        if (!viewModel.loginSuccess) {
            onNavigate("login")
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkSession()
                if (viewModel.loginSuccess) {
                    viewModel.fetchProfile()
                    viewModel.fetchWallet()
                    viewModel.fetchDeposits()
                    viewModel.fetchLeaderboard()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Theme-aware profile UI
    val context = LocalContext.current
    val isWhiteTheme = ThemePreferences(context).isWhiteTheme()

    if (isWhiteTheme) {
        WhiteProfileScreen(viewModel, sessionManager, onNavigate)
    } else {
        ClassicProfileScreen(viewModel, sessionManager, onNavigate)
    }
}

@Composable
private fun ClassicProfileScreen(
    viewModel: GunduAtaViewModel,
    sessionManager: com.sikwin.app.data.auth.SessionManager,
    onNavigate: (String) -> Unit
) {
    Scaffold(
        bottomBar = {
            DualCardsBottomBar(
                selectedTab = DualNavTab.PROFILE,
                onHome = { onNavigate("home") },
                onLive = { onNavigate("sports") },
                onCasino = { onNavigate("casino_games") },
                onWallet = { onNavigate("wallet") },
                onProfile = { }
            )
        },
        containerColor = DualScreenBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Header
            ProfileHeader(
                username = viewModel.userProfile?.username ?: sessionManager.fetchUsername() ?: "User",
                balance = viewModel.wallet?.balance ?: "0.00",
                onRefreshBalance = {
                    viewModel.fetchWallet()
                    viewModel.fetchLeaderboard()
                },
                onNavigate = onNavigate
            )

            // Leaderboard Ranking Highlight — show only when turnover > 50
            if (viewModel.userRotationMoney > 50) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onNavigate("leaderboard") },
                    color = SurfaceColor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PrimaryYellow.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryYellow.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = PrimaryYellow,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.your_daily_ranking),
                                    color = TextGrey,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (viewModel.userRank > 0) "#${viewModel.userRank}" else stringResource(R.string.unranked),
                                    color = TextWhite,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.daily_prize),
                                color = TextGrey,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when(viewModel.userRank) {
                                    1 -> "₹1,000"
                                    2 -> "₹500"
                                    3 -> "₹100"
                                    else -> stringResource(R.string.win_prize)
                                },
                                color = PrimaryYellow,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Refer & Earn Highlight
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { onNavigate("affiliate") },
                color = PrimaryYellow,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BlackBackground.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = BlackBackground,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.refer_earn),
                            color = BlackBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            stringResource(R.string.refer_earn_subtitle),
                            color = BlackBackground.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = BlackBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Quick Actions Grid
            QuickActionsGrid(onNavigate)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Menu Section 1
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
            ) {
                ProfileMenuItem(stringResource(R.string.transaction_record), Icons.AutoMirrored.Filled.List) { onNavigate("transactions") }
                // Temporarily hidden: Betting history
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.deposit_record), Icons.Default.Description) { onNavigate("deposits_record") }
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.withdrawal_record), Icons.Default.Receipt) { onNavigate("withdrawals_record") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu Section 2
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
            ) {
                ProfileMenuItem(stringResource(R.string.my_withdrawal_account), Icons.Default.AccountBox) { onNavigate("withdrawal_account") }
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.personal_data), Icons.Default.Person) { onNavigate("personal_info") }
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.security), Icons.Default.Security) { onNavigate("security") }
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.languages), Icons.Default.Translate) { onNavigate("languages") }
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.themes), Icons.Default.Palette) { onNavigate("themes") }
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.help_center), Icons.Default.TipsAndUpdates) { onNavigate("help_center") }
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.refer_a_friend), Icons.Default.PersonAdd) { onNavigate("affiliate") }
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(
                    text = stringResource(R.string.white_label_account),
                    icon = Icons.Default.Business,
                    highlighted = true
                ) { onNavigate("white_label_account") }
                // Temporarily hidden: Dice results
                Divider(color = BorderColor, thickness = 0.5.dp)
                ProfileMenuItem(stringResource(R.string.game_guidelines), Icons.Default.Casino) { onNavigate("game_guidelines") }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Logout Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = {
                        // 1. Clear UI state IMMEDIATELY on Main Thread
                        // This will trigger the LaunchedEffect below to navigate to login
                        viewModel.loginSuccess = false
                        viewModel.userProfile = null
                        viewModel.wallet = null
                        
                        // 2. Perform heavy cleanup in background
                        viewModel.logout()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceColor,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.log_out),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private val WhiteProfileBg = Color(0xFFFFFFFF)
private val WhiteProfileText = Color(0xFF000000)
private val WhiteProfileMuted = Color(0xFF374151)
private val WhiteProfileBorder = Color(0xFFE5E7EB)
private val WhiteProfileCard = Color(0xFFF9FAFB)
private val WhiteProfileAccent = GoldOnWhite

@Composable
private fun WhiteProfileScreen(
    viewModel: GunduAtaViewModel,
    sessionManager: com.sikwin.app.data.auth.SessionManager,
    onNavigate: (String) -> Unit
) {
    Scaffold(
        bottomBar = {
            WhiteBottomBar(
                selectedTab = WhiteHomeTab.PROFILE,
                onHome = { onNavigate("home") },
                onLive = { onNavigate("sports?sport=cricket") },
                onCasino = { onNavigate("casino_games") },
                onWallet = { onNavigate("wallet") },
                onProfile = { }
            )
        },
        containerColor = WhiteProfileBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            WhiteProfileDashboardCard(
                username = viewModel.userProfile?.username ?: sessionManager.fetchUsername() ?: "User",
                balance = viewModel.wallet?.balance ?: "0.00",
                onRefreshBalance = {
                    viewModel.fetchWallet()
                    viewModel.fetchLeaderboard()
                },
                onNavigate = onNavigate
            )

            WhiteReferEarnBanner(onClick = { onNavigate("affiliate") })

            if (viewModel.userRotationMoney > 50) {
                WhiteProfileHighlightCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onNavigate("leaderboard") }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(WhiteProfileAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = WhiteProfileAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.your_daily_ranking),
                                    color = WhiteProfileMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (viewModel.userRank > 0) "#${viewModel.userRank}" else stringResource(R.string.unranked),
                                    color = WhiteProfileText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.daily_prize),
                                color = WhiteProfileMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when (viewModel.userRank) {
                                    1 -> "₹1,000"
                                    2 -> "₹500"
                                    3 -> "₹100"
                                    else -> stringResource(R.string.win_prize)
                                },
                                color = WhiteProfileAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            WhiteProfileMenuSection {
                WhiteProfileMenuItem(stringResource(R.string.transaction_record), Icons.AutoMirrored.Filled.List) { onNavigate("transactions") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.deposit_record), Icons.Default.Description) { onNavigate("deposits_record") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.withdrawal_record), Icons.Default.Receipt) { onNavigate("withdrawals_record") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            WhiteProfileMenuSection {
                WhiteProfileMenuItem(stringResource(R.string.my_withdrawal_account), Icons.Default.AccountBox) { onNavigate("withdrawal_account") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.personal_data), Icons.Default.Person) { onNavigate("personal_info") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.security), Icons.Default.Security) { onNavigate("security") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.languages), Icons.Default.Translate) { onNavigate("languages") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.themes), Icons.Default.Palette) { onNavigate("themes") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.help_center), Icons.Default.HelpOutline) { onNavigate("help_center") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.refer_a_friend), Icons.Default.PersonAdd) { onNavigate("affiliate") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(
                    text = stringResource(R.string.white_label_account),
                    icon = Icons.Default.Business,
                    highlighted = true
                ) { onNavigate("white_label_account") }
                WhiteProfileMenuDivider()
                WhiteProfileMenuItem(stringResource(R.string.game_guidelines), Icons.Default.Casino) { onNavigate("game_guidelines") }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    viewModel.loginSuccess = false
                    viewModel.userProfile = null
                    viewModel.wallet = null
                    viewModel.logout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, WhiteProfileBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = WhiteProfileBg,
                    contentColor = WhiteProfileText
                )
            ) {
                Text(
                    text = stringResource(R.string.log_out),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WhiteProfileText
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WhiteProfileDashboardCard(
    username: String,
    balance: String,
    onRefreshBalance: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .whiteProfileStatusBarPadding(reduce = 18.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.my_dashboard),
            color = WhiteProfileText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WhiteProfileBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, WhiteProfileBorder),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.default_profile),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.hi_username, username),
                            color = WhiteProfileText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = WhiteProfileAccent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👑", fontSize = 9.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    "VIP0",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Total Balance",
                    color = WhiteProfileMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "₹ ${MoneyFormat.format(balance)}",
                            color = WhiteProfileText,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    var rotationTarget by remember { mutableStateOf(0f) }
                    val rotationAngle by animateFloatAsState(
                        targetValue = rotationTarget,
                        animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                rotationTarget += 360f
                                onRefreshBalance()
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Balance",
                            tint = WhiteProfileAccent,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(rotationAngle)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val actions = listOf(
                        QuickAction(stringResource(R.string.my_wallet), Icons.Default.AccountBalanceWallet, "wallet"),
                        QuickAction(stringResource(R.string.withdrawal), Icons.Default.ArrowUpward, "withdraw"),
                        QuickAction(stringResource(R.string.deposit), Icons.Default.ArrowDownward, "deposit")
                    )
                    actions.forEach { action ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, WhiteProfileBorder, RoundedCornerShape(12.dp))
                                .clickable { onNavigate(action.route) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(action.icon, null, tint = WhiteProfileAccent, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                action.name,
                                color = WhiteProfileText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhiteReferEarnBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD54F), Color(0xFFFFE082)),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 0f)
                )
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", fontSize = 36.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.refer_earn_title),
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Invite friends and earn exciting rewards!",
                    color = Color(0xFF1A1A1A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = WhiteProfileAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun WhiteProfileMenuSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(WhiteProfileBg)
            .border(1.dp, WhiteProfileBorder, RoundedCornerShape(12.dp)),
        content = content
    )
}

@Composable
private fun WhiteProfileMenuDivider() {
    HorizontalDivider(color = WhiteProfileBorder, thickness = 0.5.dp)
}

@Composable
private fun WhiteProfileHighlightCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = WhiteProfileBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WhiteProfileAccent.copy(alpha = 0.45f)),
        content = content
    )
}

@Composable
private fun WhiteProfileMenuItem(
    text: String,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WhiteProfileBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                null,
                tint = if (highlighted) WhiteProfileAccent else Color(0xFF374151),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text,
            color = if (highlighted) WhiteProfileAccent else WhiteProfileText,
            fontSize = 15.sp,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Modifier.whiteProfileStatusBarPadding(reduce: Dp): Modifier {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return padding(top = (top - reduce).coerceAtLeast(0.dp))
}

@Composable
fun ProfileHeader(
    username: String,
    balance: String,
    onRefreshBalance: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.default_profile),
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    stringResource(R.string.hi_username, username),
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = Color.DarkGray,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        "VIP0",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.total_inr), color = TextGrey, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("₹", color = PrimaryYellow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(MoneyFormat.format(balance), color = TextWhite, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))

            var rotationTarget by remember { mutableStateOf(0f) }
            val rotationAngle by animateFloatAsState(
                targetValue = rotationTarget,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        rotationTarget += 360f
                        onRefreshBalance()
                    }
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh Balance",
                    tint = PrimaryYellow,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle)
                )
            }
        }
    }
}

@Composable
fun QuickActionsGrid(onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val actions = listOf(
            QuickAction(stringResource(R.string.my_wallet), Icons.Default.AccountBalanceWallet, "wallet"),
            QuickAction(stringResource(R.string.withdrawal), Icons.Default.ArrowDownward, "withdraw"),
            QuickAction(stringResource(R.string.deposit), Icons.Default.ArrowUpward, "deposit")
        )
        
        actions.forEach { action ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
                    .clickable { onNavigate(action.route) }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(action.icon, null, tint = PrimaryYellow, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(action.name, color = TextWhite, fontSize = 11.sp)
            }
        }
    }
}

data class QuickAction(val name: String, val icon: ImageVector, val route: String)

@Composable
fun ProfileMenuItem(
    text: String,
    icon: ImageVector? = null,
    customIconId: Int? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) Modifier.background(PrimaryYellow.copy(alpha = 0.12f))
                else Modifier
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (customIconId != null) {
            Image(
                painter = painterResource(id = customIconId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )
        } else if (icon != null) {
            Icon(
                icon,
                null,
                tint = if (highlighted) PrimaryYellow else TextGrey,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text,
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ArrowForward, null, tint = if (highlighted) PrimaryYellow else TextGrey)
    }
}


