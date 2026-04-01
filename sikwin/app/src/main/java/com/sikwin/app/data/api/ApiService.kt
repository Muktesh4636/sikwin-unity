package com.sikwin.app.data.api

import com.sikwin.app.data.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("auth/login/")
    suspend fun login(@Body credentials: Map<String, String>): Response<AuthResponse>

    @POST("auth/otp/send/")
    suspend fun sendOtp(@Body data: Map<String, String>): Response<Map<String, Any>>

    @POST("auth/otp/verify-login/")
    suspend fun verifyOtpLogin(@Body data: Map<String, String>): Response<AuthResponse>

    @POST("auth/register/")
    suspend fun register(@Body data: Map<String, String>): Response<AuthResponse>

    @POST("auth/password/reset/")
    suspend fun resetPassword(@Body data: Map<String, String>): Response<Map<String, Any>>

    @POST("auth/token/refresh/")
    suspend fun refreshToken(@Body data: Map<String, String>): Response<Map<String, String>>

    @GET("auth/profile/")
    suspend fun getProfile(): Response<User>

    @POST("auth/profile/")
    suspend fun updateProfile(@Body data: Map<String, String>): Response<User>

    @Multipart
    @POST("auth/profile/photo/")
    suspend fun updateProfilePhoto(@Part photo: MultipartBody.Part): Response<User>

    @GET("maintenance/status/")
    suspend fun getMaintenanceStatus(): Response<Map<String, Any>>

    @GET("auth/wallet/")
    suspend fun getWallet(): Response<Wallet>

    /** Coin flip — POST body: `toss` ("heads"|"tails"), `bet_amount` (number). */
    @POST("coin/")
    suspend fun postCoinFlip(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<CoinFlipResponse>

    @GET("auth/transactions/")
    suspend fun getTransactions(): Response<List<Transaction>>

    @GET("auth/deposits/mine/")
    suspend fun getMyDeposits(): Response<List<DepositRequest>>

    @Multipart
    @POST("auth/deposits/upload-proof/")
    suspend fun uploadDepositProof(
        @Part("amount") amount: String,
        @Part screenshot: MultipartBody.Part
    ): Response<DepositRequest>

    @POST("auth/deposits/submit-utr/")
    suspend fun submitUtr(@Body data: Map<String, String>): Response<DepositRequest>

    @POST("auth/withdraws/initiate/")
    suspend fun initiateWithdraw(@Body data: Map<String, String>): Response<WithdrawRequest>

    @GET("auth/withdraws/mine/")
    suspend fun getMyWithdrawals(): Response<List<WithdrawRequest>>
    
    @GET("auth/payment-methods/")
    suspend fun getPaymentMethods(): Response<List<PaymentMethod>>

    @GET("auth/bank-details/")
    suspend fun getBankDetails(): Response<List<UserBankDetail>>

    @POST("auth/bank-details/")
    suspend fun addBankDetail(@Body data: @JvmSuppressWildcards Map<String, Any>): Response<UserBankDetail>
    
    @GET("auth/referral-data/")
    suspend fun getReferralData(): Response<ReferralData>

    @GET("auth/daily-reward/")
    suspend fun checkDailyRewardStatus(): Response<Map<String, Any>>

    @POST("auth/daily-reward/")
    suspend fun claimDailyReward(): Response<Map<String, Any>>
    
    @GET("auth/lucky-draw/")
    suspend fun checkLuckyDrawStatus(): Response<Map<String, Any>>
    
    @POST("auth/lucky-draw/")
    suspend fun claimLuckyDraw(): Response<Map<String, Any>>

    @GET("auth/leaderboard/")
    suspend fun getLeaderboard(): Response<Map<String, Any>>

    @POST("auth/register-fcm-token/")
    suspend fun registerFcmToken(@Body data: Map<String, String>): Response<Map<String, Any>>

    @GET("game/round/")
    suspend fun getCurrentRound(): Response<Map<String, Any>>

    @GET("game/betting-history/")
    suspend fun getBettingHistory(): Response<List<Bet>>

    @GET("game/version/")
    suspend fun getAppVersion(): Response<Map<String, Any>>

    @GET("game/recent-round-results/")
    suspend fun getRecentRoundResults(@Query("count") count: Int): Response<List<RecentRoundResult>>

    @DELETE("auth/bank-details/{id}/")
    suspend fun deleteBankDetail(@Path("id") id: Int): Response<Unit>

    /** Live event + markets + odds — see [CricketLiveResponse]. */
    @GET("cricket/live/")
    suspend fun getCricketLive(): Response<CricketLiveResponse>

    @POST("cricket/bet/")
    suspend fun postCricketBet(@Body body: CricketBetRequest): Response<CricketBetResponse>

    /** User's cricket/IPL bets only (not dice game history). */
    /** GET /api/cricket/bets/ — returns `{ "bets": [ ... ] }` */
    @GET("cricket/bets/")
    suspend fun getCricketBettingHistory(): Response<CricketBetListWrapper>

    @GET("support/contacts/")
    suspend fun getSupportContacts(@Query("package") packageName: String): Response<SupportContacts>

    @POST("whitelabel/lead/")
    suspend fun submitWhitelabelLead(@Body data: Map<String, String>): Response<Map<String, Any>>

    /** Current colour round — no auth. */
    @GET("colour/round/")
    suspend fun getColourRound(): Response<ColourRoundResponse>

    /** Place one or more colour bets — auth required. Body: single bet, or `{ "bets": [...] }`. */
    @POST("colour/bet/")
    suspend fun postColourBet(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ColourBetPlaceResponse>

    /** Round result — no auth. */
    @GET("colour/round/{roundId}/result/")
    suspend fun getColourRoundResult(@Path("roundId") roundId: String): Response<ColourRoundResultResponse>

    /** User's colour bet history — auth required. */
    @GET("colour/bets/")
    suspend fun getColourBets(): Response<ColourBetHistoryResponse>

    /** Public recent colour round results — no auth. */
    @GET("colour/results/")
    suspend fun getColourPublicResults(): Response<ColourPublicResultsResponse>

    // Optional: Send contacts to backend (uncomment if you want to sync contacts)
    // @POST("auth/contacts/")
    // suspend fun syncContacts(@Body data: Map<String, String>): Response<Map<String, Any>>
}
