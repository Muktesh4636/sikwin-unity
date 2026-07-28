package com.sikwin.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.R
import com.sikwin.app.ui.theme.BlackBackground
import com.sikwin.app.ui.theme.TextWhite
import com.sikwin.app.utils.MoneyFormat

val DualGoldLight = Color(0xFFFFE082)
val DualGoldMid = Color(0xFFFFD54F)
val DualGoldDeep = Color(0xFFC9A227)
val DualGoldBrush = Brush.verticalGradient(listOf(DualGoldLight, DualGoldMid, DualGoldDeep))
val DualScreenBlack = Color(0xFF000000)
val DualCardDark = Color(0xFF0D0D0D)

enum class DualNavTab { HOME, PROMO, CASINO, WALLET, PROFILE, NONE }

@Composable
fun DualCardsTopBar(
    balance: String,
    isLoggedIn: Boolean,
    onLeadingClick: () -> Unit,
    leadingIcon: ImageVector = Icons.Default.Menu,
    onDeposit: () -> Unit,
    onLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DualScreenBlack)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onLeadingClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = DualGoldMid,
                modifier = Modifier.size(26.dp)
            )
        }

        Image(
            painter = painterResource(id = R.drawable.gundu_ata_logo_gold),
            contentDescription = "Gundu Ata",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .padding(horizontal = 4.dp)
        )

        if (isLoggedIn) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, DualGoldDeep.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp)
                    .clickable(onClick = onDeposit),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = DualGoldMid,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = MoneyFormat.formatRupee(balance),
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(DualGoldBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            TextButton(onClick = onLogin) {
                Text(stringResource(R.string.login), color = DualGoldMid, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DualCardsBottomBar(
    selectedTab: DualNavTab = DualNavTab.HOME,
    onHome: () -> Unit,
    onPromo: () -> Unit,
    onCasino: () -> Unit,
    onWallet: () -> Unit,
    onProfile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .navigationBarsPadding()
            .height(78.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DualNavItem("HOME", Icons.Default.Home, selectedTab == DualNavTab.HOME, onHome)
        DualNavItem("PROMO", Icons.Default.CardGiftcard, selectedTab == DualNavTab.PROMO, onPromo)
        DualCasinoNavItem(selected = selectedTab == DualNavTab.CASINO, onClick = onCasino)
        DualNavItem("WALLET", Icons.Default.AccountBalanceWallet, selectedTab == DualNavTab.WALLET, onWallet)
        DualNavItem("PROFILE", Icons.Default.Person, selectedTab == DualNavTab.PROFILE, onProfile)
    }
}

@Composable
fun DualCasinoNavItem(selected: Boolean, onClick: () -> Unit) {
    val labelColor = if (selected) DualGoldMid else DualGoldDeep.copy(alpha = 0.7f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        CasinoChipIcon(
            selected = selected,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            "CASINO",
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CasinoChipIcon(
    selected: Boolean = true,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_casino_chip),
        contentDescription = "Casino",
        contentScale = ContentScale.Fit,
        modifier = modifier.graphicsLayer { alpha = if (selected) 1f else 0.65f }
    )
}

@Composable
fun DualNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) DualGoldMid else DualGoldDeep.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) DualGoldMid else DualGoldDeep.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DualGoldPlayButton(text: String, fullWidth: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DualGoldBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = BlackBackground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = if (fullWidth) 0.dp else 18.dp)
        )
    }
}
