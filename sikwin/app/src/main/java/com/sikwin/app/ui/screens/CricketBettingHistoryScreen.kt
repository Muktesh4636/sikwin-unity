package com.sikwin.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sikwin.app.data.models.CricketBetHistoryItem
import com.sikwin.app.ui.theme.CricketAccentGold
import com.sikwin.app.ui.theme.CricketChipBorder
import com.sikwin.app.ui.theme.CricketMarketBg
import com.sikwin.app.ui.theme.CricketScreenBg
import com.sikwin.app.ui.theme.CricketTextMuted
import com.sikwin.app.ui.theme.GreenSuccess
import com.sikwin.app.ui.theme.RedError
import com.sikwin.app.ui.theme.TextGrey
import com.sikwin.app.ui.theme.TextWhite
import com.sikwin.app.ui.viewmodels.GunduAtaViewModel
import java.util.Locale

/** Internal outcome bucket for badges. UI shows two tabs: Pending + Successful (wins and losses together). */
private enum class CricketBetTab {
    PENDING,
    SUCCESSFUL,
    CANCELLED
}

private val myBetsTabLabels = listOf("Pending", "Successful")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CricketBettingHistoryScreen(
    viewModel: GunduAtaViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.fetchCricketBettingHistory()
    }

    val all = viewModel.cricketBettingHistory
    val filtered = remember(all, selectedTab) {
        when (selectedTab) {
            0 -> all.filter { cricketBetTab(it) == CricketBetTab.PENDING }
            else -> all.filter {
                val t = cricketBetTab(it)
                t == CricketBetTab.SUCCESSFUL || t == CricketBetTab.CANCELLED
            }
        }
    }
    val counts = remember(all) {
        val pending = all.count { cricketBetTab(it) == CricketBetTab.PENDING }
        val settled = all.count {
            val t = cricketBetTab(it)
            t == CricketBetTab.SUCCESSFUL || t == CricketBetTab.CANCELLED
        }
        intArrayOf(pending, settled)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CricketScreenBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CricketAccentGold,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                "My bets",
                color = CricketAccentGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        when {
            viewModel.cricketBetsLoading && all.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CricketAccentGold)
                }
            }
            viewModel.cricketBetsError != null && all.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.cricketBetsError ?: "Could not load.",
                        color = CricketTextMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            all.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No cricket bets yet.", color = CricketTextMuted, fontSize = 16.sp)
                }
            }
            else -> {
                CricketBetTabRow(
                    selectedIndex = selectedTab,
                    counts = counts,
                    onSelect = { selectedTab = it }
                )
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (selectedTab) {
                                0 -> "No pending bets."
                                else -> "No settled bets yet. Win and loss bets appear here."
                            },
                            color = CricketTextMuted,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = filtered,
                            key = { it.id ?: "${it.created_at}_${it.event_name}_${it.outcome_name}".hashCode() }
                        ) { bet ->
                            CricketBetHistoryItemCard(bet)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CricketBetTabRow(
    selectedIndex: Int,
    counts: IntArray,
    onSelect: (Int) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        myBetsTabLabels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val count = counts.getOrElse(index) { 0 }
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(22.dp),
                color = CricketMarketBg,
                border = BorderStroke(
                    1.5.dp,
                    if (selected) CricketAccentGold else CricketChipBorder
                )
            ) {
                Text(
                    text = "$label ($count)",
                    color = if (selected) TextWhite else CricketTextMuted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * Classifies each bet for tabs. Fixes cases where API still sends PENDING after a win
 * by trusting [CricketBetHistoryItem.payout_amount] and [CricketBetHistoryItem.settled_at].
 * Note: [String.contains] on "WIN" does **not** match status "WON" — must check WON explicitly.
 */
private fun cricketBetTab(bet: CricketBetHistoryItem): CricketBetTab {
    val raw = bet.status?.trim().orEmpty()
    val s = raw.uppercase(Locale.US)
    val payout = bet.payout_amount ?: 0.0
    val settled = !bet.settled_at.isNullOrBlank()

    // Void / cancelled / refunded
    if (s.contains("VOID") || s.contains("CANCEL") || s.contains("REFUND") || s == "REJECTED" || s == "REJECT") {
        return CricketBetTab.CANCELLED
    }

    // Lost (explicit)
    if (s == "LOST" || s == "LOSE" || s == "LOSS" || s.contains("LOST")) {
        return CricketBetTab.CANCELLED
    }

    // Won — exact tokens only (avoid .contains("WIN"): "WON" does not match; .contains("SUCCESSFUL") matches "UNSUCCESSFUL")
    val explicitWin = s == "WON" || s == "WIN" || s == "SUCCESS" || s == "SUCCESSFUL" ||
        (s == "SETTLED" && payout > 0)

    if (explicitWin || (payout > 0.0 && settled)) {
        return CricketBetTab.SUCCESSFUL
    }

    // Settled, no payout → treat as lost / closed negative
    if (settled && payout <= 0.0 && s.isNotEmpty() && !isPendingLike(s)) {
        return CricketBetTab.CANCELLED
    }

    // Still open
    if (isPendingLike(s) || s.isEmpty()) {
        return CricketBetTab.PENDING
    }

    // Fallback: positive payout without settle time still a win
    if (payout > 0.0) return CricketBetTab.SUCCESSFUL

    return CricketBetTab.PENDING
}

private fun isPendingLike(s: String): Boolean {
    if (s.contains("PENDING")) return true
    if (s == "OPEN" || s == "ACTIVE" || s == "LIVE" || s == "ACCEPTED" || s == "PLACED") return true
    return false
}

private fun displayStatusLabel(bet: CricketBetHistoryItem): String {
    val tab = cricketBetTab(bet)
    val raw = bet.status?.trim().orEmpty()
    val s = raw.uppercase(Locale.US)
    return when (tab) {
        CricketBetTab.SUCCESSFUL -> "Win"
        CricketBetTab.CANCELLED -> when {
            s.contains("VOID") || s.contains("CANCEL") || s.contains("REFUND") -> "Loss"
            s.contains("LOST") || s.contains("LOSE") || s == "LOSS" -> "Lost"
            else -> raw.ifBlank { "Loss" }
        }
        CricketBetTab.PENDING -> raw.ifBlank { "Pending" }
    }
}

@Composable
private fun CricketBetHistoryItemCard(bet: CricketBetHistoryItem) {
    val tab = cricketBetTab(bet)
    val label = displayStatusLabel(bet)
    val statusColor = when (tab) {
        CricketBetTab.SUCCESSFUL -> GreenSuccess
        CricketBetTab.CANCELLED -> RedError
        CricketBetTab.PENDING -> CricketTextMuted
    }
    Surface(
        color = CricketMarketBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CricketChipBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = bet.event_name?.takeIf { it.isNotBlank() } ?: "Match",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = label.uppercase(Locale.US),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = listOfNotNull(
                    bet.market_name?.takeIf { it.isNotBlank() },
                    bet.outcome_name?.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                color = CricketTextMuted,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Odds ${bet.odds ?: "—"}  ·  Stake ₹${bet.stake ?: 0}",
                        color = TextWhite,
                        fontSize = 13.sp
                    )
                    bet.potential_payout?.let { p ->
                        Text(
                            "Potential ₹${formatCricketMoney(p)}",
                            color = CricketTextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    val payout = bet.payout_amount
                    if (payout != null && payout > 0) {
                        Text(
                            "Paid out ₹${formatCricketMoney(payout)}",
                            color = GreenSuccess,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            val metaLines = buildList {
                bet.created_at?.takeIf { it.isNotBlank() }?.let { add("Placed $it") }
                bet.settled_at?.takeIf { it.isNotBlank() }?.let { add("Settled $it") }
            }
            if (metaLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    metaLines.joinToString("\n"),
                    color = TextGrey,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatCricketMoney(p: Double): String =
    if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()
