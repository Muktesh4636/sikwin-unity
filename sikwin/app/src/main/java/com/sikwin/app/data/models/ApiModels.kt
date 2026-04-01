package com.sikwin.app.data.models

import com.google.gson.annotations.JsonAdapter

data class User(
    val id: Int,
    val username: String,
    val email: String,
    val phone_number: String?,
    val gender: String? = null,
    val telegram: String? = null,
    val facebook: String? = null,
    val address: String? = null,
    val date_of_birth: String? = null,
    val is_staff: Boolean,
    val profile_photo: String? = null,
    val referral_code: String? = null
)

data class AuthResponse(
    val access: String,
    val refresh: String,
    val user: User
)

data class MaintenanceStatus(
    val maintenance: Boolean,
    // Legacy (minutes)
    val maintenance_until: Int? = null, // minutes
    // New API shape
    val remaining_hours: Int? = null,
    val remaining_minutes: Int? = null
)

@JsonAdapter(WalletDeserializer::class)
data class Wallet(
    val id: Int? = null,
    val balance: String = "0.00",
    val unavaliable_balance: String = "0.00",
    val unavailable_balance: String? = null,
    val withdrawable_balance: String = "0.00",
    val user: User? = null
) {
    /** Unavailable balance: supports both API spellings (unavailable_balance / unavaliable_balance). */
    val unavailableBalanceDisplay: String
        get() = unavailable_balance?.takeIf { it.isNotBlank() } ?: unavaliable_balance.takeIf { it.isNotBlank() } ?: "0.00"
}

data class Transaction(
    val id: Int,
    val transaction_type: String,
    val amount: String,
    val balance_before: String,
    val balance_after: String,
    val description: String,
    val created_at: String
)

data class DepositRequest(
    val id: Int,
    val amount: String,
    val status: String,
    val screenshot_url: String?,
    val admin_note: String?,
    val created_at: String
)

data class WithdrawRequest(
    val id: Int,
    val amount: String,
    val status: String,
    val withdrawal_method: String,
    val withdrawal_details: String,
    val admin_note: String?,
    val created_at: String
)

data class UserBankDetail(
    val id: Int,
    val account_name: String,
    val bank_name: String,
    val account_number: String,
    val ifsc_code: String,
    val upi_id: String?,
    val is_default: Boolean
)

data class PaymentMethod(
    val id: Int,
    val name: String,
    val method_type: String,
    val bank_name: String?,
    val account_number: String?,
    val account_name: String?,
    val ifsc_code: String?,
    val upi_id: String?,
    val usdt_wallet_address: String? = null,
    val qr_image: String?,
    val is_active: Boolean
)

data class ReferralData(
    val referral_code: String,
    val total_referrals: Int,
    val active_referrals: Int,
    val total_earnings: String,
    val current_milestone_bonus: String,
    val next_milestone: NextMilestone?,
    val milestones: List<Milestone>,
    val recent_bonuses: List<RecentBonus>,
    val referrals: List<ReferralItem> = emptyList()
)

data class ReferralItem(
    val id: Int,
    val username: String,
    val date_joined: String? = null,
    val has_deposit: Boolean = false
)

data class NextMilestone(
    val next_milestone: Int?,
    val next_bonus: Double,
    val next_bonus_display: String? = null,
    val current_progress: Int,
    val progress_percentage: Double,
    val target: Int? = null,
    val tier: Int? = null
)

data class Milestone(
    val count: Int,
    val bonus: Int,
    val bonus_display: String? = null,
    val achieved: Boolean,
    val progress_current: Int = 0,
    val target: Int = count
)

data class RecentBonus(
    val amount: String,
    val description: String,
    val created_at: String
)

data class GameRound(
    val round_id: String,
    val status: String,
    val dice_result: String?,
    val created_at: String? = null
)

data class Bet(
    val id: Int,
    val round: GameRound,
    val number: Int,
    val chip_amount: String,
    val payout_amount: String,
    val is_winner: Boolean,
    val created_at: String
)

data class RecentRoundResult(
    val round_id: String,
    val dice_1: Int?,
    val dice_2: Int?,
    val dice_3: Int?,
    val dice_4: Int?,
    val dice_5: Int?,
    val dice_6: Int?,
    val dice_result: String?,
    val timestamp: String?
)

/** Response from https://gunduata.club/api/support/contacts/ */
data class SupportContacts(
    val whatsapp_number: String? = null,
    val telegram: String? = null
)

// --- Cricket / IPL: GET /api/cricket/live/, POST /api/cricket/bet/ ---

/** GET https://gunduata.club/api/cricket/live/ */
data class CricketLiveResponse(
    val data: CricketLiveEventData? = null,
    val fetched_at: String? = null,
    val source_url: String? = null
)

data class CricketLiveEventData(
    val id: Long = 0L,
    val description: String? = null,
    val markets: List<CricketLiveMarket>? = null
)

data class CricketLiveMarket(
    val id: Long = 0L,
    val description: String? = null,
    val status: String? = null,
    val outcomes: List<CricketLiveOutcome>? = null
)

data class CricketLiveOutcome(
    val id: Long = 0L,
    val description: String? = null,
    val consolidatedPrice: CricketConsolidatedPrice? = null
) {
    fun displayLabel(): String = description?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
    fun displayOdds(): String {
        val fmt = consolidatedPrice?.currentPrice?.format?.trim()
        if (!fmt.isNullOrEmpty()) return fmt
        val d = consolidatedPrice?.currentPrice?.decimal
        return if (d != null) String.format("%.2f", d) else "—"
    }
}

data class CricketConsolidatedPrice(
    val currentPrice: CricketCurrentPrice? = null
)

data class CricketCurrentPrice(
    val decimal: Double? = null,
    val format: String? = null
)

/** POST /api/cricket/bet/ */
data class CricketBetRequest(
    val event_id: Long,
    val market_id: Long,
    val outcome_id: Long,
    val stake: Int
)

data class CricketBetResponse(
    val id: Int? = null,
    val event_name: String? = null,
    val market_name: String? = null,
    val outcome_name: String? = null,
    val odds: String? = null,
    val stake: Int? = null,
    val potential_payout: Double? = null,
    val status: String? = null,
    val created_at: String? = null,
    val wallet_balance: Double? = null
)

/** One row from GET /api/cricket/bets/ */
data class CricketBetHistoryItem(
    val id: Int? = null,
    val event_id: Long? = null,
    val event_name: String? = null,
    val market_id: Long? = null,
    val market_name: String? = null,
    val outcome_id: Long? = null,
    val outcome_name: String? = null,
    val odds: String? = null,
    val stake: Int? = null,
    val potential_payout: Double? = null,
    val status: String? = null,
    val payout_amount: Double? = null,
    val created_at: String? = null,
    val settled_at: String? = null
)

/** Primary shape: `{ "bets": [...] }`; also accept legacy `data` / `results`. */
data class CricketBetListWrapper(
    val bets: List<CricketBetHistoryItem>? = null,
    val data: List<CricketBetHistoryItem>? = null,
    val results: List<CricketBetHistoryItem>? = null
)
