import { http } from './http';

export type User = {
  id: number;
  username: string;
  email?: string | null;
  phone_number?: string | null;
  referral_code?: string | null;
  wallet_balance?: string | null;
  gender?: string | null;
  telegram?: string | null;
  date_of_birth?: string | null;
};

export type AuthResponse = {
  user: User;
  access: string;
  refresh: string;
};

export type Wallet = {
  id: number;
  balance: string;
  withdrawable_balance: string;
  unavaliable_balance?: string; // backend typo exists
  unavailable_balance?: string;
};

export type SupportContacts = {
  /** API field from gunduata.club (matches Android `SupportContacts.whatsapp_number`) */
  whatsapp_number?: string | null;
  /** Legacy/alternate key if backend ever sends camelCase */
  whatsapp?: string | null;
  telegram?: string | null;
  phone?: string | null;
  email?: string | null;
} & Record<string, any>;

export type RecentRoundResult = {
  round_id: string;
  dice_result?: string | null;
  timestamp?: string | null;
  dice_1?: number | null;
  dice_2?: number | null;
  dice_3?: number | null;
  dice_4?: number | null;
  dice_5?: number | null;
  dice_6?: number | null;
} & Record<string, any>;

export async function apiLogin(username: string, password: string) {
  return http.post<AuthResponse>('auth/login/', { username, password });
}

export async function apiSendOtp(phoneNumber: string) {
  return http.post<Record<string, unknown>>('auth/otp/send/', { phone_number: phoneNumber });
}

export async function apiRegister(data: Record<string, string>) {
  return http.post<AuthResponse>('auth/register/', data);
}

export async function apiProfile() {
  return http.get<User>('auth/profile/');
}

export async function apiUpdateProfile(data: Record<string, string>) {
  return http.post<User>('auth/profile/', data);
}

export async function apiWallet() {
  return http.get<Wallet>('auth/wallet/');
}

export type BankDetail = {
  id?: number;
  bank_name: string;
  account_number: string;
  ifsc_code: string;
  is_default?: boolean;
  account_holder_name?: string | null;
  account_name?: string | null;
} & Record<string, any>;

export async function apiBankDetails() {
  return http.get<BankDetail[]>('auth/bank-details/');
}

export async function apiDeleteBankDetail(id: number) {
  return http.delete(`auth/bank-details/${id}/`);
}

export async function apiAddBankDetail(data: {
  account_name: string;
  account_number: string;
  bank_name: string;
  ifsc_code: string;
  is_default?: boolean;
}) {
  return http.post<BankDetail>('auth/bank-details/', data);
}

export async function apiInitiateWithdraw(amount: string, withdrawal_method: string, withdrawal_details: string) {
  return http.post('auth/withdraws/initiate/', { amount, withdrawal_method, withdrawal_details });
}

export type DepositRequest = {
  id: number;
  amount: string;
  status: string;
  screenshot_url?: string | null;
  admin_note?: string | null;
  created_at: string;
};

export type WithdrawRequest = {
  id: number;
  amount: string;
  status: string;
  withdrawal_method: string;
  withdrawal_details: string;
  admin_note?: string | null;
  created_at: string;
};

export type GameRound = {
  round_id: string;
  status: string;
  dice_result?: string | null;
  created_at?: string | null;
};

export type Bet = {
  id: number;
  round: GameRound;
  number: number;
  chip_amount: string;
  payout_amount: string;
  is_winner: boolean;
  created_at: string;
};

/** Normalize API response: either a direct array or paginated { results: [] }. */
export function normalizeListResponse<T>(
  data: T[] | { results: T[] } | null | undefined
): T[] {
  if (data == null) return [];
  if (Array.isArray(data)) return data;
  if ('results' in data && Array.isArray(data.results)) return data.results;
  return [];
}

/** Optional limit (default backend may be 10–20). Use a high value to get all records. */
export async function apiMyDeposits(params?: { limit?: number; offset?: number }) {
  return http.get<DepositRequest[] | { results: DepositRequest[]; count?: number; next?: string }>(
    'auth/deposits/mine/',
    { params: params ?? { limit: 1000 } }
  );
}

export type PaymentMethod = {
  id: number;
  name: string;
  method_type: string;
  bank_name?: string | null;
  account_number?: string | null;
  account_name?: string | null;
  ifsc_code?: string | null;
  upi_id?: string | null;
  usdt_wallet_address?: string | null;
  qr_image?: string | null;
  is_active: boolean;
};

export async function apiPaymentMethods() {
  return http.get<PaymentMethod[]>('auth/payment-methods/');
}

export async function apiUploadDepositProof(amount: string, file: File) {
  const form = new FormData();
  form.append('amount', amount);
  form.append('screenshot', file);
  return http.post<DepositRequest>('auth/deposits/upload-proof/', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60_000,
  });
}

export async function apiSubmitUtr(data: { amount: string; utr: string; [key: string]: string }) {
  return http.post<DepositRequest>('auth/deposits/submit-utr/', data);
}

/** Optional limit to get all records. */
export async function apiMyWithdrawals(params?: { limit?: number; offset?: number }) {
  return http.get<WithdrawRequest[] | { results: WithdrawRequest[]; count?: number; next?: string }>(
    'auth/withdraws/mine/',
    { params: params ?? { limit: 1000 } }
  );
}

/** Optional limit (default: last 100 records). */
export async function apiBettingHistory(params?: { limit?: number; offset?: number }) {
  return http.get<Bet[] | { results: Bet[]; count?: number; next?: string }>(
    'game/betting-history/',
    { params: params ?? { limit: 100 } }
  );
}

export async function apiLeaderboard() {
  return http.get<Record<string, any>>('auth/leaderboard/');
}

export type ReferralData = {
  referral_code: string;
  total_referrals: number;
  active_referrals: number;
  total_earnings: string;
  current_milestone_bonus?: string;
  next_milestone?: {
    next_milestone?: number;
    next_bonus: number;
    next_bonus_display?: string;
    current_progress: number;
    progress_percentage: number;
    target?: number;
    tier?: number;
  };
  milestones?: Array<{
    count: number;
    bonus: number;
    bonus_display?: string;
    achieved?: boolean;
    progress_current?: number;
    target?: number;
  }>;
  referrals?: Array<{ id: number; username: string; has_deposit?: boolean }>;
};

export async function apiReferralData() {
  return http.get<ReferralData>('auth/referral-data/');
}

export type DailyRewardStatus = {
  claimed?: boolean;
  message?: string;
  reward?: { amount?: number; type?: string };
};

export async function apiDailyRewardStatus() {
  return http.get<DailyRewardStatus>('auth/daily-reward/');
}

export async function apiClaimDailyReward() {
  return http.post<{
    daily_reward?: { amount?: number; type?: string };
    reward?: { amount?: number; type?: string };
    message?: string;
  }>('auth/daily-reward/');
}

export type LuckyDrawStatus = {
  claimed?: boolean;
  message?: string;
  /** Wallet/ledger amount (prefer when present). */
  reward?: { amount?: number };
  /** Some APIs duplicate this; may disagree with reward — clients should reconcile. */
  lucky_draw?: { amount?: number };
  deposit_amount?: number;
  /** If backend sends the actual credited rupees, use for UI. */
  credited_amount?: number;
};

export async function apiCheckLuckyDrawStatus() {
  return http.get<LuckyDrawStatus>('auth/lucky-draw/');
}

export async function apiClaimLuckyDraw() {
  return http.post<{
    lucky_draw?: { amount?: number };
    reward?: { amount?: number };
    credited_amount?: number;
    message?: string;
  }>('auth/lucky-draw/');
}

export async function apiRecentDiceResults(count: number) {
  return http.get<RecentRoundResult[]>('game/recent-round-results/', { params: { count } });
}

export async function apiSupportContacts() {
  return http.get<SupportContacts>('support/contacts/');
}

export async function apiPartnerRequest(data: { name: string; phone_number: string; message?: string }) {
  return http.post<{ detail?: string }>('partner/request/', data);
}

export async function apiMaintenanceStatus() {
  return http.get<Record<string, any>>('maintenance/status/', { timeout: 5000 });
}

