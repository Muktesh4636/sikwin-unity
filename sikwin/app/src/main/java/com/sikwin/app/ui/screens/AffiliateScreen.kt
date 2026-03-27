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
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.ui.theme.*
import android.content.Intent
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextAlign

/** Referrals required in this step only (step 2 is 0/12 after 3/3, not 3/12 lifetime). */
private data class ReferralTierConfig(val referralsInStep: Int, val bonus: Int, val bonusDisplay: String? = null)

private val REFERRAL_TIER_CONFIGS = listOf(
    ReferralTierConfig(3, 500),
    ReferralTierConfig(12, 2200),
    ReferralTierConfig(25, 0, "Spin & Win (up to ₹1 Lakh)")
)

private data class ActiveReferralTier(
    val tierIndex: Int,
    val config: ReferralTierConfig,
    val cumulativeStart: Int,
    val progressInStep: Int
)

/** Current step; null if all steps done. `progressInStep` resets each step (e.g. 0 right after 3/3). */
private fun deriveActiveReferralTier(totalReferrals: Int): ActiveReferralTier? {
    var start = 0
    REFERRAL_TIER_CONFIGS.forEachIndexed { index, tier ->
        val end = start + tier.referralsInStep
        if (totalReferrals < end) {
            val inStep = (totalReferrals - start).coerceIn(0, tier.referralsInStep)
            return ActiveReferralTier(index, tier, start, inStep)
        }
        start = end
    }
    return null
}

private fun cumulativeBeforeTierIndex(index: Int): Int =
    REFERRAL_TIER_CONFIGS.take(index).sumOf { it.referralsInStep }

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
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.refer_earn_title),
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite
                    )
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = BlackBackground
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // New Stylish Hero Section with Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(PrimaryYellow.copy(alpha = 0.3f), BlackBackground)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = PrimaryYellow,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.invite_friends_win),
                        color = TextWhite,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Stylish Referral Code Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-30).dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceColor,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.your_referral_code),
                            color = TextGrey,
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
                                .background(BlackBackground.copy(alpha = 0.5f))
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
                                    color = PrimaryYellow,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ContentCopy, null, tint = PrimaryYellow, modifier = Modifier.size(18.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                enabled = referralCode.isNotEmpty(),
                                onClick = {
                                    val shareMessage = "🎲 Join me on Gundu Ata and win big!\n\nUse my referral code: $referralCode\n\nDownload now: https://gunduata.club/signup?ref=$referralCode"
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
                                    val shareMessage = "🎲 Join me on Gundu Ata and win big!\n\nUse my referral code: $referralCode\n\nDownload now: https://gunduata.club/signup?ref=$referralCode"
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
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceColor
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StepItem("1", stringResource(R.string.step_share_code))
                        StepDivider()
                        StepItem("2", stringResource(R.string.step_friend_deposits))
                        StepDivider()
                        StepItem("3", stringResource(R.string.step_get_bonus))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Milestone bonuses (3, 12, 25) — progress derived from total_referrals so UI matches reality
                Text(
                    stringResource(R.string.milestone_bonuses),
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                val totalRefs = referralData?.total_referrals ?: 0
                val activeTier = deriveActiveReferralTier(totalRefs)
                val firstTierStep = REFERRAL_TIER_CONFIGS[0].referralsInStep
                val totalForSteps12 = firstTierStep + REFERRAL_TIER_CONFIGS[1].referralsInStep

                if (activeTier != null) {
                    val showGreatJobBanner = totalRefs >= firstTierStep && activeTier.tierIndex >= 1
                    val targetVal = activeTier.config.referralsInStep
                    val currentProgress = activeTier.progressInStep
                    val remaining = (targetVal - currentProgress).coerceAtLeast(0)
                    val defaultRewardLabel = activeTier.config.bonusDisplay ?: "₹${activeTier.config.bonus}"
                    val apiNext = referralData?.next_milestone
                    val apiTarget = apiNext?.target ?: apiNext?.next_milestone
                    val rewardLabel = if (apiNext != null && apiTarget == targetVal && apiNext.next_bonus_display != null) {
                        apiNext.next_bonus_display!!
                    } else {
                        defaultRewardLabel
                    }
                    val progressPct = if (targetVal > 0) {
                        (currentProgress.toDouble() / targetVal.toDouble() * 100.0).coerceIn(0.0, 100.0)
                    } else {
                        0.0
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceColor,
                        border = BorderStroke(1.dp, PrimaryYellow.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (showGreatJobBanner) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = GreenSuccess.copy(alpha = 0.18f),
                                    border = BorderStroke(2.dp, GreenSuccess.copy(alpha = 0.85f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            stringResource(R.string.great_job_title),
                                            color = GreenSuccess,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            stringResource(R.string.great_job_three_complete),
                                            color = TextWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.next_reward_this_step), color = TextGrey, fontSize = 12.sp)
                                    Text(
                                        text = rewardLabel,
                                        color = PrimaryYellow,
                                        fontSize = if (activeTier.config.bonusDisplay != null) 18.sp else 22.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    when (activeTier.tierIndex) {
                                        1 -> {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                stringResource(
                                                    R.string.milestone_step2_resets,
                                                    firstTierStep,
                                                    targetVal,
                                                    totalForSteps12
                                                ),
                                                color = GreenSuccess,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        2 -> {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                stringResource(
                                                    R.string.milestone_step3_resets,
                                                    totalForSteps12,
                                                    targetVal,
                                                    totalForSteps12 + targetVal
                                                ),
                                                color = GreenSuccess,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { (progressPct / 100.0).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier.size(60.dp),
                                        color = PrimaryYellow,
                                        trackColor = Color.DarkGray,
                                        strokeWidth = 6.dp
                                    )
                                    Text(
                                        "$currentProgress/$targetVal",
                                        color = TextWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (remaining > 0) {
                                Text(
                                    stringResource(R.string.refer_more_this_step, remaining),
                                    color = TextWhite,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceColor,
                        border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                stringResource(R.string.referral_all_milestones_unlocked),
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                val apiMilestones = referralData?.milestones ?: emptyList()
                fun findMilestone(displayCount: Int) = apiMilestones.find { it.count == displayCount }

                REFERRAL_TIER_CONFIGS.forEachIndexed { tierIndex, tier ->
                    val count = tier.referralsInStep
                    val m = findMilestone(count)
                    val bonus = m?.bonus ?: tier.bonus
                    val bonusDisplay = m?.bonus_display ?: tier.bonusDisplay
                    val tierStart = cumulativeBeforeTierIndex(tierIndex)
                    val tierEnd = tierStart + tier.referralsInStep
                    val target = m?.target?.takeIf { it == count } ?: count
                    val achieved = m?.achieved ?: (totalRefs >= tierEnd)
                    val progressCurrent =
                        if (achieved) target else (totalRefs - tierStart).coerceIn(0, target)
                    val label = stringResource(R.string.referrals_count, count)
                    val celebrateFirst = tierIndex == 0 && achieved
                    MilestoneCard(
                        count = count,
                        bonus = bonus,
                        bonusDisplay = bonusDisplay,
                        achieved = achieved,
                        progressCurrent = progressCurrent,
                        target = target,
                        label = label,
                        celebrateFirstMilestone = celebrateFirst
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                    StatCard(
                        title = stringResource(R.string.total_earned),
                        value = "₹${referralData?.total_earnings ?: "0"}",
                        icon = Icons.Filled.AccountBalanceWallet,
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryYellow
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
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceColor
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
                                    Text(stringResource(R.string.view_all_more, referralsList.size - 3), color = PrimaryYellow, fontWeight = FontWeight.Bold)
                                }
                            } else if (hasMore && showAllReferrals) {
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    onClick = { showAllReferrals = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.view_less), color = PrimaryYellow, fontWeight = FontWeight.Bold)
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
fun StepItem(number: String, text: String) {
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
        Text(text, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
    color: Color = PrimaryYellow
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor
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
                tint = color,
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
                    color = TextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                title,
                color = TextGrey,
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
    val highlightGreen = celebrateFirstMilestone && achieved
    val borderColor = when {
        highlightGreen -> GreenSuccess
        achieved -> PrimaryYellow
        else -> BorderColor
    }
    val borderWidth = if (achieved || highlightGreen) 2.dp else 1.dp
    val bgColor = when {
        highlightGreen -> GreenSuccess.copy(alpha = 0.12f)
        achieved -> PrimaryYellow.copy(alpha = 0.1f)
        else -> SurfaceColor
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
                                achieved -> PrimaryYellow
                                else -> PrimaryYellow.copy(alpha = 0.6f)
                            },
                            trackColor = Color.DarkGray,
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
                                color = TextWhite,
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
                                achieved -> PrimaryYellow
                                else -> TextWhite
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
                            color = if (highlightGreen) GreenSuccess.copy(alpha = 0.9f) else TextGrey,
                            fontSize = 12.sp,
                            fontWeight = if (highlightGreen) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Text(
                    text = bonusDisplay ?: "₹$bonus",
                    color = if (highlightGreen) GreenSuccess else if (achieved) PrimaryYellow else TextGrey,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BlackBackground.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = PrimaryYellow.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = PrimaryYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = username,
                color = TextWhite,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(tier, color = TextGrey, fontSize = 14.sp)
        Text(bonus, color = PrimaryYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
