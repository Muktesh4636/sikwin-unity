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
} & Record<string, any>;

export async function apiBankDetails() {
  return http.get<BankDetail[]>('auth/bank-details/');
}

export async function apiDeleteBankDetail(id: number) {
  return http.delete(`auth/bank-details/${id}/`);
}

export async function apiAddBankDetail(data: {
  account_holder_name: string;
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

export async function apiMyDeposits() {
  return http.get<DepositRequest[]>('auth/deposits/mine/');
}

export async function apiMyWithdrawals() {
  return http.get<WithdrawRequest[]>('auth/withdraws/mine/');
}

export async function apiBettingHistory() {
  return http.get<Bet[]>('game/betting-history/');
}

export async function apiLeaderboard() {
  return http.get<Record<string, any>>('auth/leaderboard/');
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
  return http.get<Record<string, any>>('maintenance/status/');
}

