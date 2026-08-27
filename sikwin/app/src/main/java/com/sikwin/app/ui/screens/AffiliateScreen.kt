package com.sikwin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sikwin.app.R
import com.sikwin.app.ui.theme.BlackBackground
import com.sikwin.app.ui.theme.BorderColor
import com.sikwin.app.ui.theme.GoldOnWhite
import com.sikwin.app.ui.theme.GreenSuccess
import com.sikwin.app.ui.theme.PrimaryYellow
import com.sikwin.app.ui.theme.SurfaceColor
import com.sikwin.app.ui.theme.AppSubScreenHeader
import com.sikwin.app.ui.theme.rememberAppScreenColors
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import android.content.Intent
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Commission tier rates by lifetime referral count (matches backend). */
private data class CommissionTierRow(val minRefs: Int, val maxRefs: Int?, val rateLabel: String)

private val COMMISSION_TIER_ROWS = listOf(
    CommissionTierRow(1, 10, "2%"),
    CommissionTierRow(11, 30, "3%"),
    CommissionTierRow(31, 50, "4%"),
    CommissionTierRow(51, 100, "6%"),
    CommissionTierRow(101, null, "8%"),
)

private val AffiliateCodeBoxDark = Color(0xFF1A1A1A).copy(alpha = 0.5f)
private val AffiliateCodeBoxLight = Color(0xFFF3F4F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AffiliateScreen(
    viewModel: GunduAtaViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Fetch referral data
    LaunchedEffect(Unit) {
        viewModel.fetchReferralData()
        if (viewModel.userProfile == null || viewModel.userProfile?.referral_code == null) {
            viewModel.fetchProfile()
        }
    }
    
    val referralData = viewModel.referralData
    val referralCode = referralData?.referral_code
        ?: viewModel.userProfile?.referral_code
        ?: viewModel.savedReferralCode
        ?: ""
    val colors = rememberAppScreenColors()
    val accent = colors.accent
    val cardSurface = if (colors.isWhite) colors.surface else SurfaceColor
    val codeBoxBg = if (colors.isWhite) AffiliateCodeBoxLight else AffiliateCodeBoxDark
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AppSubScreenHeader(
            title = stringResource(R.string.refer_earn_title),
            colors = colors,
            onBack = onBack
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (colors.isWhite) {
                                listOf(PrimaryYellow.copy(alpha = 0.22f), colors.background)
                            } else {
                                listOf(PrimaryYellow.copy(alpha = 0.3f), BlackBackground)
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.invite_friends_win),
                        color = colors.text,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-30).dp),
                    shape = RoundedCornerShape(16.dp),
                    color = cardSurface,
                    shadowElevation = if (colors.isWhite) 2.dp else 8.dp,
                    border = colors.listItemBorder
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.your_referral_code),
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val codeCopiedText = stringResource(R.string.code_copied)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(codeBoxBg)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clickable(enabled = referralCode.isNotEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Referral Code", referralCode)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, codeCopiedText, android.widget.Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = referralCode,
                                    color = accent,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ContentCopy, null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                enabled = referralCode.isNotEmpty(),
                                onClick = {
                                    val shareMessage = "🎲 Join me on Pride and win big!\n\nUse my referral code: $referralCode\n\nDownload now: https://gunduata.tech/signup?ref=$referralCode"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareMessage)
                                        setPackage("com.whatsapp")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val genericIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                                        }
                                        context.startActivity(Intent.createChooser(genericIntent, "Share via"))
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(painterResource(id = R.drawable.ic_whatsapp), null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.whatsapp), fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                enabled = referralCode.isNotEmpty(),
                                onClick = {
                                    val shareMessage = "🎲 Join me on Pride and win big!\n\nUse my referral code: $referralCode\n\nDownload now: https://gunduata.tech/signup?ref=$referralCode"
                                    val genericIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareMessage)
                                    }
                                    context.startActivity(Intent.createChooser(genericIntent, "Share via"))
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, null, tint = BlackBackground, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.share), color = BlackBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // How it works (at top, under referral code)
                Text(
                    stringResource(R.string.how_it_works),
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardSurface,
                    border = colors.listItemBorder
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StepItem("1", stringResource(R.string.step_share_code))
                        StepDivider()
                        StepItem("2", stringResource(R.string.step_friend_deposits))
                        StepDivider()
                        StepItem("3", stringResource(R.string.step_get_bonus))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.referral_commission_note),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    stringResource(R.string.daily_commission_tiers),
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                val totalRefs = referralData?.total_referrals ?: 0
                val currentRate = referralData?.commission_tier_percent
                    ?: COMMISSION_TIER_ROWS.lastOrNull { row ->
                        row.maxRefs == null && totalRefs >= row.minRefs
                            || row.maxRefs != null && totalRefs in row.minRefs..row.maxRefs
                    }?.rateLabel
                    ?: "2%"
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = cardSurface,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.your_commission_rate), color = colors.textMuted, fontSize = 12.sp)
                        Text(currentRate, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text(
                            stringResource(R.string.commission_on_referee_loss),
                            color = colors.text,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        referralData?.next_commission_tier?.let { next ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                stringResource(
                                    R.string.next_commission_tier_hint,
                                    next.referrals_needed,
                                    next.rate_percent
                                ),
                                color = GreenSuccess,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                val tiers = referralData?.commission_tiers
                if (!tiers.isNullOrEmpty()) {
                    tiers.forEach { tier ->
                        CommissionTierCard(
                            label = if (tier.max_referrals != null) {
                                "${tier.min_referrals}–${tier.max_referrals} referrals"
                            } else {
                                "${tier.min_referrals}+ referrals"
                            },
                            rate = tier.rate_percent,
                            active = tier.active
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    COMMISSION_TIER_ROWS.forEach { row ->
                        val active = when {
                            row.maxRefs == null -> totalRefs >= row.minRefs
                            else -> totalRefs in row.minRefs..row.maxRefs
                        }
                        CommissionTierCard(
                            label = if (row.maxRefs != null) "${row.minRefs}–${row.maxRefs} referrals" else "${row.minRefs}+ referrals",
                            rate = row.rateLabel,
                            active = active
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Stats Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = stringResource(R.string.total_referrals),
                            value = "${referralData?.total_referrals ?: 0}",
                            icon = Icons.Filled.People,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = stringResource(R.string.deposited_counts),
                            value = "${referralData?.active_referrals ?: 0}",
                            icon = Icons.Filled.CheckCircle,
                            modifier = Modifier.weight(1f),
                            color = GreenSuccess
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = stringResource(R.string.instant_bonuses_earned),
                            value = "₹${referralData?.total_instant_bonuses ?: "0"}",
                            icon = Icons.Filled.CardGiftcard,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = stringResource(R.string.commission_earned),
                            value = "₹${referralData?.total_commission_earnings ?: "0"}",
                            icon = Icons.Filled.TrendingUp,
                            modifier = Modifier.weight(1f),
                            color = GreenSuccess
                        )
                    }
                    StatCard(
                        title = stringResource(R.string.total_earned),
                        value = "₹${referralData?.total_earnings ?: "0"}",
                        icon = Icons.Filled.AccountBalanceWallet,
                        modifier = Modifier.fillMaxWidth(),
                        color = accent
                    )
                }

                // My Referrals section - show 3 initially, View All to expand
                val referralsList = referralData?.referrals ?: emptyList()
                if (referralsList.isNotEmpty()) {
                    var showAllReferrals by remember { mutableStateOf(false) }
                    val displayedReferrals = if (showAllReferrals) referralsList else referralsList.take(3)
                    val hasMore = referralsList.size > 3

                    Text(
                        stringResource(R.string.my_referrals),
                        color = colors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cardSurface,
                        border = colors.listItemBorder
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            displayedReferrals.forEachIndexed { index, ref ->
                                ReferralListItem(
                                    username = ref.username,
                                    hasDeposit = ref.has_deposit
                                )
                                if (index < displayedReferrals.size - 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                            if (hasMore && !showAllReferrals) {
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    onClick = { showAllReferrals = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.view_all_more, referralsList.size - 3), color = accent, fontWeight = FontWeight.Bold)
                                }
                            } else if (hasMore && showAllReferrals) {
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    onClick = { showAllReferrals = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.view_less), color = accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun CommissionTierCard(label: String, rate: String, active: Boolean) {
    val colors = rememberAppScreenColors()
    val accent = colors.accent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (active) accent.copy(alpha = 0.12f) else colors.surface,
        border = BorderStroke(1.dp, if (active) accent else colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.text, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
            Text(rate, color = if (active) accent else colors.textMuted, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun StepItem(number: String, text: String) {
    val colors = rememberAppScreenColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = PrimaryYellow
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, color = BlackBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StepDivider() {
    Box(
        modifier = Modifier
            .padding(start = 13.dp)
            .width(2.dp)
            .height(20.dp)
            .background(PrimaryYellow.copy(alpha = 0.3f))
    )
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    color: Color = GoldOnWhite
) {
    val colors = rememberAppScreenColors()
    val accent = if (color == GoldOnWhite) colors.accent else color
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = colors.listItemBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value,
                    color = colors.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                title,
                color = colors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun MilestoneCard(
    count: Int,
    bonus: Int,
    bonusDisplay: String? = null,
    achieved: Boolean,
    progressCurrent: Int = 0,
    target: Int = count,
    labelResId: Int? = null,
    labelFormatArgs: Array<Any> = emptyArray(),
    label: String? = null,
    celebrateFirstMilestone: Boolean = false
) {
    val resolvedLabel = when {
        labelResId != null -> if (labelFormatArgs.isEmpty()) stringResource(labelResId) else stringResource(labelResId, *labelFormatArgs)
        label != null -> label
        else -> "$count Referrals"
    }
    val colors = rememberAppScreenColors()
    val accent = colors.accent
    val highlightGreen = celebrateFirstMilestone && achieved
    val borderColor = when {
        highlightGreen -> GreenSuccess
        achieved -> accent
        else -> colors.border
    }
    val borderWidth = if (achieved || highlightGreen) 2.dp else 1.dp
    val bgColor = when {
        highlightGreen -> GreenSuccess.copy(alpha = 0.12f)
        achieved -> accent.copy(alpha = 0.1f)
        else -> colors.surface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(width = borderWidth, color = borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val progress = if (target > 0) (progressCurrent.toFloat() / target).coerceIn(0f, 1f) else 0f
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(44.dp),
                            color = when {
                                highlightGreen -> GreenSuccess
                                achieved -> accent
                                else -> accent.copy(alpha = 0.6f)
                            },
                            trackColor = if (colors.isWhite) Color(0xFFE5E7EB) else Color.DarkGray,
                            strokeWidth = 4.dp
                        )
                        if (achieved) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (highlightGreen) Color.White else BlackBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                "$progressCurrent/$target",
                                color = colors.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            resolvedLabel,
                            color = when {
                                highlightGreen -> GreenSuccess
                                achieved -> accent
                                else -> colors.text
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                highlightGreen -> stringResource(R.string.great_job_three_complete)
                                achieved -> stringResource(R.string.achieved)
                                else -> "$progressCurrent / $target"
                            },
                            color = if (highlightGreen) GreenSuccess.copy(alpha = 0.9f) else colors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = if (highlightGreen) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Text(
                    text = bonusDisplay ?: "₹$bonus",
                    color = if (highlightGreen) GreenSuccess else if (achieved) accent else colors.textMuted,
                    fontSize = if (bonusDisplay != null) 14.sp else 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ReferralListItem(
    username: String,
    hasDeposit: Boolean
) {
    val colors = rememberAppScreenColors()
    val accent = colors.accent
    val rowBg = if (colors.isWhite) AffiliateCodeBoxLight else AffiliateCodeBoxDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = username,
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (hasDeposit) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = GreenSuccess.copy(alpha = 0.3f)
            ) {
                Text(
                    stringResource(R.string.deposited),
                    color = GreenSuccess,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun BonusRuleItem(tier: String, bonus: String) {
    val colors = rememberAppScreenColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(tier, color = colors.textMuted, fontSize = 14.sp)
        Text(bonus, color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
