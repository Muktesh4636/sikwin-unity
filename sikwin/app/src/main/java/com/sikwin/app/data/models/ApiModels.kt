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

data class PaybitraDepositResponse(
    val amount: String,
    val currency: String = "INR",
    val upi_id: String,
    val acc_holder_name: String? = null,
    val code: String? = null,
    val upi_uri: String,
    val paybitra_order_id: String,
    val payin_id: String? = null,
    val expires_at: String? = null,
    val pay_in_url: String? = null,
    val message: String? = null
)

data class ReferralData(
    val referral_code: String,
    val total_referrals: Int,
    val active_referrals: Int,
    val total_earnings: String,
    val instant_bonus_per_referee: String? = null,
    val total_instant_bonuses: String? = null,
    val total_commission_earnings: String? = null,
    val commission_tier_rate: Double? = null,
    val commission_tier_percent: String? = null,
    val commission_tiers: List<CommissionTier> = emptyList(),
    val next_commission_tier: NextCommissionTier? = null,
    val current_milestone_bonus: String? = null,
    val next_milestone: NextMilestone? = null,
    val milestones: List<Milestone> = emptyList(),
    val recent_bonuses: List<RecentBonus> = emptyList(),
    val referrals: List<ReferralItem> = emptyList()
)

data class CommissionTier(
    val min_referrals: Int,
    val max_referrals: Int? = null,
    val rate_percent: String,
    val rate: Double,
    val active: Boolean = false
)

data class NextCommissionTier(
    val target_referrals: Int,
    val referrals_needed: Int,
    val rate: Double,
    val rate_percent: String
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

/** Response from https://gunduata.tech/api/support/contacts/ */
data class SupportContacts(
    val whatsapp_number: String? = null,
    val telegram: String? = null
)

/** POST https://gunduata.tech/api/coin/ — body: `toss` (heads|tails), `bet_amount` (number). */
data class CoinFlipResponse(
    val toss: String? = null,
    val result: String? = null,
    val won: Boolean? = null,
    val bet_amount: String? = null,
    val ratio: Int? = null,
    val payout: String? = null,
    val profit: String? = null,
    val wallet_balance: String? = null
)

// --- Cricket / IPL: matches, scores, changes (+ bet) ---

/** GET /api/cricket/matches/ */
data class CricketMatchesResponse(
    val count: Int? = null,
    val last_sync: String? = null,
    val matches: List<CricketMatchSummary>? = null
)

/** GET /api/cricket/upcoming/ */
data class CricketUpcomingResponse(
    val count: Int? = null,
    val last_sync: String? = null,
    val matches: List<CricketUpcomingMatch>? = null
)

/** Pre-match row — includes inline odds (detail URL may 404 for these ids). */
data class CricketUpcomingMatch(
    val id: Long = 0L,
    val match: String? = null,
    val competition: String? = null,
    val country: String? = null,
    val date: String? = null,
    val slug: String? = null,
    val betradar_id: Long? = null,
    val odds: CricketOddsBundle? = null
) {
    fun marketCount(): Int = odds?.market_count ?: odds?.markets?.size ?: 0

    fun toLiveEvent(): CricketLiveEventData =
        CricketLiveEventData(
            id = id,
            description = match,
            competition = competition?.takeIf { it.isNotBlank() } ?: country,
            period = "Upcoming",
            markets = odds?.markets.orEmpty().toLiveMarkets()
        )

    /** First open market outcomes for list preview (usually H2H). */
    fun previewOutcomes(): List<CricketLiveOutcome> {
        val markets = odds?.markets.orEmpty()
        val open = markets.firstOrNull { it.status.equals("open", true) } ?: markets.firstOrNull()
        return open?.outcomes.orEmpty()
            .filter { it.hidden != true && it.withdrawn != true }
            .map {
                CricketLiveOutcome(
                    id = it.id,
                    description = it.description,
                    consolidatedPrice = CricketConsolidatedPrice(
                        currentPrice = CricketCurrentPrice(
                            decimal = it.price_decimal,
                            format = it.price_formatted
                        )
                    )
                )
            }
    }
}

/** Row from matches list (and overlapping fields on scores ticker). */
data class CricketMatchSummary(
    val id: Long = 0L,
    val match: String? = null,
    val competition: String? = null,
    val country: String? = null,
    val date: String? = null,
    val period: String? = null,
    val period_number: Int? = null,
    val clock: CricketMatchClock? = null,
    val scores: List<CricketTeamScore>? = null,
    val batting: String? = null,
    /** Live over/ball progress from scores / match detail. */
    val live: CricketLiveInfo? = null,
    /** List endpoint: market count as Int. */
    val markets: Int? = null,
    val live_market_count: Int? = null,
    val detail_url: String? = null,
    val betradar_id: Long? = null,
    val slug: String? = null
) {
    fun marketCount(): Int = live_market_count ?: markets ?: 0
}

data class CricketMatchClock(
    val running: Boolean? = null,
    val minutes: Int? = null,
    val seconds: Int? = null,
    val status: String? = null
)

data class CricketTeamScore(
    val team: String? = null,
    val team_id: Long? = null,
    val score: String? = null,
    val batting: Boolean? = null
)

/**
 * Live over/ball block from GET /api/cricket/scores/ and match detail.
 * Example: `{ "current_over": 12, "current_ball": null, "source": "market_inference" }`
 *
 * When [source] is `ball_by_ball`, [recent_overs] uses comma-separated [CricketLiveRecentOver.balls]
 * strings (not arrays) — wrong typing here used to break Gson for the entire matches/scores payload.
 */
data class CricketLiveInfo(
    val current_over: Int? = null,
    val current_ball: Int? = null,
    val current_over_balls: Int? = null,
    val last_ball_timestamp: Long? = null,
    val recent_overs: List<CricketLiveRecentOver>? = null,
    val source: String? = null
) {
    /** Cricket-style overs decimal, e.g. 12.3 (over 12, ball 3). */
    fun oversDecimal(): Double? {
        val over = current_over ?: return null
        val ball = (current_ball ?: current_over_balls)?.coerceIn(0, 5) ?: 0
        return over + ball / 10.0
    }

    fun oversLabel(): String? {
        val over = current_over ?: return null
        val ball = current_ball ?: current_over_balls
        return if (ball != null) "$over.$ball" else over.toString()
    }
}

/** One over from live.recent_overs (ball_by_ball source). */
data class CricketLiveRecentOver(
    val over: Int? = null,
    /** Comma-separated codes, e.g. `"1,dot,4,W,wide"`. */
    val balls: String? = null,
    val runs: Int? = null,
    val wickets: Int? = null,
    val complete: Boolean? = null
) {
    fun ballCodes(): List<String> =
        balls?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    fun toUiRecentOver(): CricketRecentOver =
        CricketRecentOver(
            overNumber = over,
            runs = runs,
            isCurrentOver = complete == false,
            balls = ballCodes()
        )
}

/** GET /api/cricket/matches/{id}/ */
data class CricketMatchDetailResponse(
    val last_sync: String? = null,
    val match: CricketMatchDetail? = null
)

data class CricketMatchDetail(
    val id: Long = 0L,
    val match: String? = null,
    val competition: String? = null,
    val country: String? = null,
    val date: String? = null,
    val period: String? = null,
    val period_number: Int? = null,
    val clock: CricketMatchClock? = null,
    val scores: List<CricketTeamScore>? = null,
    val live: CricketLiveInfo? = null,
    val live_market_count: Int? = null,
    val betradar_id: Long? = null,
    val slug: String? = null,
    val odds: CricketOddsBundle? = null
)

data class CricketOddsBundle(
    val market_count: Int? = null,
    val markets: List<CricketOddsMarket>? = null
)

data class CricketOddsMarket(
    val id: Long = 0L,
    val description: String? = null,
    val market_type: String? = null,
    val market_type_id: Long? = null,
    val status: String? = null,
    val period: String? = null,
    val period_id: Long? = null,
    val team: String? = null,
    val outcomes: List<CricketOddsOutcome>? = null
)

data class CricketOddsOutcome(
    val id: Long = 0L,
    val description: String? = null,
    val price_decimal: Double? = null,
    val price_formatted: String? = null,
    val prev_price_decimal: Double? = null,
    val line: Double? = null,
    val withdrawn: Boolean? = null,
    val hidden: Boolean? = null
)

/** GET /api/cricket/scores/ */
data class CricketScoresResponse(
    val count: Int? = null,
    val last_sync: String? = null,
    val matches: List<CricketMatchSummary>? = null
)

/**
 * GET /api/cricket/changes/?bn=N — live price deltas.
 * Upstream may be flaky; [bn] advances when present.
 */
data class CricketChangesResponse(
    val bn: Long? = null,
    val last_sync: String? = null,
    val markets: List<CricketOddsMarket>? = null,
    val events: List<CricketChangeEvent>? = null,
    val matches: List<CricketMatchDetail>? = null,
    val error: String? = null,
    val detail: String? = null
)

data class CricketChangeEvent(
    val id: Long = 0L,
    val markets: List<CricketOddsMarket>? = null,
    val odds: CricketOddsBundle? = null
)

/** UI-friendly live event built from match detail / changes. */
data class CricketLiveEventData(
    val id: Long = 0L,
    val description: String? = null,
    val competition: String? = null,
    val period: String? = null,
    val markets: List<CricketLiveMarket>? = null
)

data class CricketLiveMarket(
    val id: Long = 0L,
    val description: String? = null,
    val status: String? = null,
    val marketType: String? = null,
    val period: String? = null,
    val outcomes: List<CricketLiveOutcome>? = null
)

data class CricketLiveOutcome(
    val id: Long = 0L,
    val description: String? = null,
    val consolidatedPrice: CricketConsolidatedPrice? = null,
    val withdrawn: Boolean? = null,
    val hidden: Boolean? = null
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

/** Maps provider odds markets into the UI market model. */
fun List<CricketOddsMarket>.toLiveMarkets(): List<CricketLiveMarket> =
    map { m ->
        CricketLiveMarket(
            id = m.id,
            description = m.description,
            status = m.status,
            marketType = m.market_type,
            period = m.period,
            outcomes = m.outcomes.orEmpty()
                .filter { it.hidden != true && it.withdrawn != true }
                .map { o ->
                    CricketLiveOutcome(
                        id = o.id,
                        description = o.description,
                        consolidatedPrice = CricketConsolidatedPrice(
                            currentPrice = CricketCurrentPrice(
                                decimal = o.price_decimal,
                                format = o.price_formatted
                            )
                        ),
                        withdrawn = o.withdrawn,
                        hidden = o.hidden
                    )
                }
        )
    }

fun CricketMatchDetail.toLiveEvent(): CricketLiveEventData =
    CricketLiveEventData(
        id = id,
        description = match,
        competition = competition?.takeIf { it.isNotBlank() } ?: country,
        period = period,
        markets = odds?.markets.orEmpty().toLiveMarkets()
    )

fun CricketMatchDetail.toScorePayload(): CricketScorePayload =
    CricketMatchSummary(
        id = id,
        match = match,
        competition = competition,
        country = country,
        date = date,
        period = period,
        period_number = period_number,
        clock = clock,
        scores = scores,
        live = live,
        live_market_count = live_market_count,
        betradar_id = betradar_id,
        slug = slug
    ).toScorePayload()

/** Builds a lightweight scorecard from ticker scores for the scoreboard panel. */
fun CricketMatchSummary.toScorePayload(): CricketScorePayload {
    val liveOvers = live?.oversDecimal()
    val liveLabel = live?.oversLabel()
    val innings = scores.orEmpty().mapIndexed { index, s ->
        val (runs, wickets) = parseCricketScoreLine(s.score)
        val batting = s.batting == true
        CricketInningsScore(
            teamName = s.team,
            summary = s.score,
            conclusion = if (batting) "In Progress" else null,
            runs = runs,
            wickets = wickets,
            // Attach live overs to the batting side when API provides them.
            overs = if (batting) liveOvers else null,
            inningsNumber = index + 1
        )
    }
    val overNote = liveLabel?.let { "Ov $it" }
    return CricketScorePayload(
        matchTitle = match,
        matchCommentary = listOfNotNull(
            period,
            overNote,
            competition?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { null },
        seriesName = competition?.takeIf { it.isNotBlank() } ?: country,
        recentOvers = live?.recent_overs?.map { it.toUiRecentOver() },
        innings = innings
    )
}

fun parseCricketScoreLine(raw: String?): Pair<Int?, Int?> {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty() || s == "-" || s.equals("yet to bat", true)) return null to null
    val parts = s.split("-", limit = 2)
    val runs = parts.getOrNull(0)?.trim()?.toIntOrNull()
    val wickets = parts.getOrNull(1)?.trim()?.substringBefore(" ")?.toIntOrNull()
    return runs to wickets
}

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

// --- Cricket scorecard helpers (built from GET /api/cricket/scores/) ---

data class CricketScorePayload(
    val matchTitle: String? = null,
    val matchCommentary: String? = null,
    val matchStatus: Int? = null,
    val currentInningsNumber: Int? = null,
    val bettingSuspended: Boolean? = null,
    val seriesName: String? = null,
    /** Last few overs with ball-by-ball codes (e.g. "4", "1w", "6"). */
    val recentOvers: List<CricketRecentOver>? = null,
    val innings: List<CricketInningsScore>? = null
)

data class CricketRecentOver(
    val overNumber: Int? = null,
    val runs: Int? = null,
    val isCurrentOver: Boolean? = null,
    val balls: List<String>? = null
)

data class CricketInningsScore(
    val teamName: String? = null,
    val summary: String? = null,
    val conclusion: String? = null,
    val runs: Int? = null,
    val wickets: Int? = null,
    /** e.g. 20.0 or 13.1 (overs.balls) */
    val overs: Double? = null,
    /** T20 usually 20. */
    val oversAvailable: Int? = null,
    val inningsNumber: Int? = null,
    val target: Int? = null,
    val batsmen: List<CricketBatsmanRow>? = null,
    val bowlers: List<CricketBowlerRow>? = null
)

data class CricketBatsmanRow(
    val batsmanName: String? = null,
    val runs: Int? = null,
    val balls: Int? = null,
    val description: String? = null,
    val active: Boolean? = null,
    val onStrike: Boolean? = null,
    val didNotBat: Boolean? = null,
    val toCome: Boolean? = null,
    val fours: Int? = null,
    val sixes: Int? = null
)

data class CricketBowlerRow(
    val bowlerName: String? = null,
    val overs: Double? = null,
    val maidens: Int? = null,
    val runs: Int? = null,
    val wickets: Int? = null,
    val isActiveBowler: Boolean? = null,
    val isOtherBowler: Boolean? = null
)

// --- Colour game: GET /api/colour/round/, POST /api/colour/bet/, GET .../result/, GET /api/colour/bets/ ---

/** GET /api/colour/round/ — active round or [status] == "no_round". */
data class ColourRoundResponse(
    val status: String,
    val message: String? = null,
    val round_id: String? = null,
    val timer: Int? = null,
    val betting_open: Boolean? = null,
    val result: String? = null,
    val number: Int? = null,
    /** ISO-8601 start of round — used to derive remaining time if [timer] is absent. */
    val start_time: String? = null,
    /** Optional server “now” (ISO-8601) — if added by API, improves clock sync. */
    val server_time: String? = null,
    /** Optional total betting window in seconds for this round. */
    val round_duration_seconds: Int? = null
)

/** GET /api/colour/round/{round_id}/result/ */
data class ColourRoundResultResponse(
    val round_id: String? = null,
    val status: String? = null,
    val result: String? = null,
    val number: Int? = null,
    val result_time: String? = null
)

data class ColourBetPlacedLine(
    val id: Int? = null,
    val bet_on: String? = null,
    val number: Int? = null,
    val amount: Int? = null,
    val status: String? = null
)

/** POST /api/colour/bet/ — 201 */
data class ColourBetPlaceResponse(
    val round_id: String? = null,
    val bets_placed: Int? = null,
    val total_stake: Int? = null,
    val wallet_balance: Number? = null,
    val bets: List<ColourBetPlacedLine>? = null
)

/** GET /api/colour/bets/ */
data class ColourBetHistoryItem(
    val id: Int? = null,
    val round_id: String? = null,
    val bet_on: String? = null,
    val number: Int? = null,
    val amount: Int? = null,
    val payout: Int? = null,
    val status: String? = null,
    val result: String? = null,
    val result_number: Int? = null,
    val created_at: String? = null,
    val settled_at: String? = null
)

data class ColourBetHistoryResponse(
    val bets: List<ColourBetHistoryItem>? = null
)

/** GET /api/colour/results/ — public recent round outcomes (no auth). */
data class ColourPublicResultItem(
    val round_id: String = "",
    val result: String = "",
    val number: Int = 0,
    val result_time: String? = null
)

data class ColourPublicResultsResponse(
    val results: List<ColourPublicResultItem>? = null
)
