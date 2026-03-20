import { Link, useNavigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { useAuth } from '../auth/AuthContext';
import { apiWallet, type Wallet } from '../api/endpoints';
import { useTranslations } from '../context/LocaleContext';

function RefreshIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  );
}

function MenuItem({
  icon,
  label,
  to,
  highlighted = false,
  noBorderBelow = false,
}: {
  icon: React.ReactNode;
  label: string;
  to: string;
  highlighted?: boolean;
  noBorderBelow?: boolean;
}) {
  return (
    <Link
      to={to}
      className={`flex w-full items-center gap-4 rounded-none border-b border-border px-4 py-3 last:border-b-0 ${
        noBorderBelow ? 'border-b-0' : ''
      } ${highlighted ? 'bg-primaryYellow/15' : 'bg-surface'}`}
    >
      <span className={`[&>svg]:h-7 [&>svg]:w-7 [&>img]:h-7 [&>img]:w-7 shrink-0 ${highlighted ? 'text-primaryYellow' : 'text-textGrey [&>img]:grayscale [&>img]:opacity-80'}`}>{icon}</span>
      <span className={`min-w-0 flex-1 text-base font-medium ${highlighted ? 'text-primaryYellow' : 'text-textWhite'}`}>{label}</span>
      <span className={`shrink-0 ${highlighted ? 'text-primaryYellow' : 'text-textGrey'}`}>
        <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
      </span>
    </Link>
  );
}

function ListIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 10h16M4 14h16M4 18h16" />
    </svg>
  );
}

/** 3D dice image for Betting History and Dice Results */
function DiceNavIcon() {
  return <img src="/dice_3d.png" alt="" className="h-7 w-7 object-contain" />;
}

/** APK: Icons.Default.Description — document with lines (deposit record) */
function DepositRecordIcon() {
  return (
    <svg fill="currentColor" viewBox="0 0 24 24">
      <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zM6 20V4h7v5h5v11H6zm2-4h8v-2H8v2zm0-4h8v-2H8v2zm0-4h5v-2H8v2z" />
    </svg>
  );
}

/** APK: Icons.Default.Receipt — receipt slip (withdrawal record) */
function WithdrawalRecordIcon() {
  return (
    <svg fill="currentColor" viewBox="0 0 24 24">
      <path d="M18 17H6v-2h12v2zm0-4H6v-2h12v2zm0-4H6V7h12v2zM3 22l1.5-1.5L6 22l1.5-1.5L9 22l1.5-1.5L12 22l1.5-1.5L15 22l1.5-1.5L18 22l1.5-1.5L21 22V2l-1.5 1.5L18 2l-1.5 1.5L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2 7.5 3.5 6 2 4.5 3.5 3 2v20z" />
    </svg>
  );
}

/** Wallet icon — same as Kotlin APK Icons.Default.AccountBalanceWallet */
function WalletIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className}>
      <path d="M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" />
    </svg>
  );
}

/** Bold downward arrow with V-shaped head - yellow, small like app */
function WithdrawIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M12 5v14M12 19l-4-4M12 19l4-4" />
    </svg>
  );
}

/** Bold upward arrow - yellow, small like app */
function DepositIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M12 19V5M12 5l-4 4M12 5l4 4" />
    </svg>
  );
}

function ReferIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
    </svg>
  );
}

function PersonIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
    </svg>
  );
}

function SecurityIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}

function LanguagesIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5h12M9 3v2m1.048 9.5A18.022 18.022 0 016.412 9m6.088 9h7M11 21l5-10 5 10M12.751 5C11.783 10.77 8.07 15.61 3 18.129" />
    </svg>
  );
}

function HelpCenterIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
    </svg>
  );
}

function BusinessIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
    </svg>
  );
}

function BookIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
    </svg>
  );
}

export function ProfilePage() {
  const auth = useAuth();
  const nav = useNavigate();
  const t = useTranslations();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const fetchWallet = () => {
    setRefreshing(true);
    apiWallet()
      .then((r) => setWallet(r.data))
      .catch(() => {})
      .finally(() => setRefreshing(false));
  };

  useEffect(() => {
    fetchWallet();
  }, []);

  const balance = wallet?.balance ?? '0.00';
  const username = auth.user?.username ?? 'User';

  return (
    <div className="mx-auto w-full max-w-[500px] min-h-dvh bg-appBg">
      {/* Header: My Dashboard | ₹ balance + (match APK 16dp, 24sp title) */}
      <header className="flex items-center justify-between bg-appBg px-4 py-3">
        <h1 className="text-base font-bold text-textWhite">{t('my_dashboard')}</h1>
        <button
          type="button"
          onClick={() => nav('/deposit')}
          className="flex items-center gap-1.5 text-sm"
        >
          <span className="font-bold" style={{ color: '#FFCC00' }}>₹ {balance}</span>
          <span className="flex h-8 w-8 items-center justify-center rounded text-lg font-bold leading-none text-black" style={{ backgroundColor: '#FFCC00' }}>
            +
          </span>
        </button>
      </header>

      <div className="px-4 pt-2 pb-24">
        {/* Profile block: avatar 80dp + Hi username + VIP0 (match APK) */}
        <div className="flex items-center gap-4">
          <div className="h-24 w-24 shrink-0 overflow-hidden rounded-full bg-card">
            <img
              src="/default_profile.jpg"
              alt=""
              className="h-full w-full object-cover"
            />
          </div>
          <div>
            <div className="text-xl font-bold text-textWhite">Hi~ {username}</div>
            <span className="mt-1 inline-block rounded bg-[#424242] px-2 py-1 text-xs font-bold text-textGrey">
              VIP0
            </span>
          </div>
        </div>

        {/* Total INR + balance + refresh (APK: 14sp label, 24sp ₹, 32sp balance, 20dp refresh) */}
        <div className="mt-5">
          <div className="text-base text-textGrey">{t('total_inr')}</div>
          <div className="mt-1.5 flex items-center gap-2">
            <span className="text-2xl font-bold" style={{ color: '#FFCC00' }}>₹</span>
            <span className="text-3xl font-bold text-textWhite">{balance}</span>
            <button
              type="button"
              onClick={fetchWallet}
              disabled={refreshing}
              className="ml-1 rounded-full p-1 transition-transform hover:opacity-90 disabled:animate-spin"
              style={{ color: '#FFCC00' }}
              aria-label="Refresh balance"
            >
              <RefreshIcon className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* REFER & EARN banner (APK: 12dp radius, 16dp padding, 48dp icon, 18sp/12sp) */}
        <Link
          to="/refer"
          className="mt-4 flex w-full items-center gap-3 rounded-xl bg-primaryYellow px-4 py-3 text-left"
        >
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-black/10 text-black">
            <ReferIcon className="h-6 w-6" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="text-base font-black uppercase tracking-wide text-black">{t('refer_earn_title')}</div>
            <div className="text-xs font-bold text-black/80">{t('refer_earn_subtitle')}</div>
          </div>
          <span className="shrink-0 text-black">
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </span>
        </Link>

        {/* Quick actions (APK: 12dp gap, 16dp vertical padding, 28dp icon, 11sp text) */}
        <div className="mt-3 flex w-full gap-3">
          <Link
            to="/wallet"
            className="flex flex-1 flex-col items-center justify-center gap-2 rounded-xl bg-surface py-4 transition-opacity hover:opacity-90 active:opacity-95"
          >
            <WalletIcon className="h-7 w-7 shrink-0 text-primaryYellow" aria-hidden />
            <span className="text-center text-sm font-medium text-textWhite">{t('my_wallet')}</span>
          </Link>
          <Link
            to="/withdraw"
            className="flex flex-1 flex-col items-center justify-center gap-2 rounded-xl bg-surface py-4 transition-opacity hover:opacity-90 active:opacity-95"
          >
            <WithdrawIcon className="h-7 w-7 shrink-0 text-primaryYellow" aria-hidden />
            <span className="text-center text-sm font-medium text-textWhite">{t('withdrawal')}</span>
          </Link>
          <Link
            to="/deposit"
            className="flex flex-1 flex-col items-center justify-center gap-2 rounded-xl bg-surface py-4 transition-opacity hover:opacity-90 active:opacity-95"
          >
            <DepositIcon className="h-7 w-7 shrink-0 text-primaryYellow" aria-hidden />
            <span className="text-center text-sm font-medium text-textWhite">{t('deposit')}</span>
          </Link>
        </div>

        {/* Menu Section 1: Records (APK: 16dp padding, 12dp radius, 24dp icon, 16sp text) */}
        <div className="mt-5 overflow-hidden rounded-xl bg-surface">
          <MenuItem icon={<ListIcon />} label={t('transaction_record')} to="/transactions" />
          <MenuItem icon={<DiceNavIcon />} label={t('betting_history')} to="/betting-record" />
          <MenuItem icon={<DepositRecordIcon />} label={t('deposit_record')} to="/deposit-record" />
          <MenuItem icon={<WithdrawalRecordIcon />} label={t('withdrawal_record')} to="/withdrawal-record" noBorderBelow />
        </div>
        <div className="mt-3 overflow-hidden rounded-xl bg-surface">
          <MenuItem icon={<WalletIcon />} label={t('my_withdrawal_account')} to="/withdrawal-account" />
          <MenuItem icon={<PersonIcon />} label={t('personal_data')} to="/personal-info" />
          <MenuItem icon={<SecurityIcon />} label={t('security')} to="/security" />
          <MenuItem icon={<LanguagesIcon />} label={t('languages')} to="/languages" />
          <MenuItem icon={<HelpCenterIcon />} label={t('help_center')} to="/help-center" />
          <MenuItem icon={<ReferIcon />} label={t('refer_a_friend')} to="/refer" noBorderBelow />
        </div>
        <div className="mt-3 overflow-hidden rounded-xl bg-surface">
          <MenuItem icon={<BusinessIcon />} label={t('become_partner')} to="/partner" highlighted />
          <MenuItem icon={<DiceNavIcon />} label={t('dice_results')} to="/dice-results" />
          <MenuItem icon={<BookIcon />} label={t('game_guidelines')} to="/game-guidelines" />
        </div>

        {/* Logout (APK: 48dp height, 8dp radius) */}
        <button
          type="button"
          onClick={() => {
            auth.logout('user_logout');
            nav('/', { replace: true });
          }}
          className="mt-5 flex h-14 w-full items-center justify-center gap-2 rounded-xl border border-border bg-surface text-base font-medium text-textWhite active:opacity-80"
        >
          <span className="shrink-0" aria-hidden>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" stroke="#FFFFFF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
          </span>
          <span>{t('log_out')}</span>
        </button>
      </div>
    </div>
  );
}
