import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { apiWallet, type Wallet } from '../api/endpoints';
import { useEffect } from 'react';

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
      className={`flex w-full items-center gap-5 rounded-none border-b border-border px-4 py-4 last:border-b-0 ${
        noBorderBelow ? 'border-b-0' : ''
      } ${highlighted ? 'bg-primaryYellow/15' : 'bg-surface'}`}
    >
      <span className={`[&>svg]:h-7 [&>svg]:w-7 [&>img]:h-7 [&>img]:w-7 ${highlighted ? 'text-primaryYellow' : 'text-textGrey [&>img]:grayscale [&>img]:opacity-80'}`}>{icon}</span>
      <span className={`flex-1 text-base font-medium ${highlighted ? 'text-primaryYellow' : 'text-textWhite'}`}>{label}</span>
      <span className={highlighted ? 'text-primaryYellow' : 'text-textGrey'}>
        <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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

function DocumentIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
    </svg>
  );
}

/** Wallet icon: rounded C-shape body, inner rounded card/flap, black dot, subtle outline - matches reference */
function WalletIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" className={className}>
      {/* C-shape body: bold rounded C opening right, with slightly rounded “prongs” at opening */}
      <path
        fill="currentColor"
        stroke="#0d0d0d"
        strokeWidth="0.6"
        strokeLinejoin="round"
        fillRule="evenodd"
        d="M15 6L8 6Q6 6 6 8L6 16Q6 18 8 18L15 18L15 6z M11.5 6v12h3.5V6h-3.5z"
      />
      {/* Inner flap/card: rounded rectangle nested in open part of C */}
      <path
        fill="currentColor"
        stroke="#0d0d0d"
        strokeWidth="0.5"
        strokeLinejoin="round"
        d="M14.5 9.5L16.5 9.5Q17.5 9.5 17.5 10.5L17.5 14.5Q17.5 15.5 16.5 15.5L14.5 15.5Q13.5 15.5 13.5 14.5L13.5 10.5Q13.5 9.5 14.5 9.5z"
      />
      {/* Black dot in center of card */}
      <circle cx="16" cy="12" r="1.2" fill="#0a0a0a" />
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

function ReferIcon() {
  return (
    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
    <div className="mobile-frame min-h-dvh bg-appBg">
      {/* Header: My Dashboard | ₹ balance + yellow + */}
      <header className="flex items-center justify-between bg-appBg px-4 py-2">
        <h1 className="text-lg font-bold text-textWhite">My Dashboard</h1>
        <button
          type="button"
          onClick={() => nav('/wallet')}
          className="flex items-center gap-2 text-lg text-textWhite"
        >
          <span className="font-bold">₹ {balance}</span>
          <span className="flex h-11 w-11 items-center justify-center rounded bg-primaryYellow text-2xl font-bold leading-none text-black">
            +
          </span>
        </button>
      </header>

      <div className="px-4 pt-2 pb-4">
        {/* Profile block: avatar + Hi~ username + VIP0 */}
        <div className="flex items-center gap-5">
          <div className="h-24 w-24 shrink-0 overflow-hidden rounded-full bg-card">
            <img
              src="/default_profile.jpg"
              alt=""
              className="h-full w-full object-cover"
            />
          </div>
          <div>
            <div className="text-xl font-bold text-textWhite">Hi~ {username}</div>
            <span className="mt-1 inline-block rounded bg-[#424242] px-2.5 py-1 text-xs font-bold text-textGrey">
              VIP0
            </span>
          </div>
        </div>

        {/* Total/INR + big balance + refresh */}
        <div className="mt-6">
          <div className="text-base text-textGrey">Total/INR</div>
          <div className="mt-2 flex items-center gap-2">
            <span className="text-2xl font-bold text-primaryYellow">₹</span>
            <span className="text-3xl font-bold text-textWhite">{balance}</span>
            <button
              type="button"
              onClick={fetchWallet}
              disabled={refreshing}
              className="ml-2 rounded-full p-2 text-primaryYellow transition-transform hover:opacity-90 disabled:animate-spin"
              aria-label="Refresh balance"
            >
              <RefreshIcon className="h-6 w-6" />
            </button>
          </div>
        </div>

        {/* REFER & EARN banner */}
        <Link
          to="/refer"
          className="mt-4 flex w-full items-center gap-3 rounded-xl bg-primaryYellow px-4 py-3.5 text-left"
        >
          <span className="flex h-11 w-11 items-center justify-center rounded-full bg-black/10 text-black">
            <ReferIcon className="h-6 w-6" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="text-sm font-black uppercase tracking-wide text-black">REFER & EARN</div>
            <div className="text-xs font-bold text-black/80">Invite friends and win up to ₹ 1 Lakh!</div>
          </div>
          <span className="text-black">
            <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </span>
        </Link>

        {/* Quick actions: My wallet, Withdrawal, Deposit (match app layout) */}
        <div className="mt-3 flex w-full gap-4">
          <Link
            to="/wallet"
            className="flex min-h-[100px] flex-1 flex-col items-center justify-center gap-3 rounded-xl bg-surface py-5 transition-opacity hover:opacity-90 active:opacity-95"
          >
            <WalletIcon className="h-7 w-7 shrink-0 text-primaryYellow" aria-hidden />
            <span className="text-center text-sm font-medium text-textWhite">My wallet</span>
          </Link>
          <Link
            to="/withdraw"
            className="flex min-h-[100px] flex-1 flex-col items-center justify-center gap-3 rounded-xl bg-surface py-5 transition-opacity hover:opacity-90 active:opacity-95"
          >
            <WithdrawIcon className="h-7 w-7 shrink-0 text-primaryYellow" aria-hidden />
            <span className="text-center text-sm font-medium text-textWhite">Withdrawal</span>
          </Link>
          <Link
            to="/deposit"
            className="flex min-h-[100px] flex-1 flex-col items-center justify-center gap-3 rounded-xl bg-surface py-5 transition-opacity hover:opacity-90 active:opacity-95"
          >
            <DepositIcon className="h-7 w-7 shrink-0 text-primaryYellow" aria-hidden />
            <span className="text-center text-sm font-medium text-textWhite">Deposit</span>
          </Link>
        </div>

        {/* Menu Section 1: Records & account */}
        <div className="mt-6 overflow-hidden rounded-xl bg-surface">
          <MenuItem icon={<ListIcon />} label="Transaction record" to="/transactions" />
          <MenuItem icon={<DiceNavIcon />} label="Betting History" to="/betting-record" noBorderBelow />
          <MenuItem icon={<DocumentIcon />} label="Deposit record" to="/deposit-record" />
          <MenuItem icon={<DocumentIcon />} label="Withdrawal record" to="/withdrawal-record" noBorderBelow />
          <MenuItem icon={<WalletIcon />} label="My Withdrawal Account" to="/withdrawal-account" />
          <MenuItem icon={<PersonIcon />} label="Personal data" to="/personal-info" />
          <MenuItem icon={<SecurityIcon />} label="Security" to="/security" />
          <MenuItem icon={<LanguagesIcon />} label="Languages" to="/languages" />
          <MenuItem icon={<HelpCenterIcon />} label="Help center" to="/help-center" />
          <MenuItem icon={<ReferIcon />} label="Refer a Friend" to="/refer" />
        </div>
        {/* Menu Section 2: Partner & game */}
        <div className="mt-3 overflow-hidden rounded-xl bg-surface">
          <MenuItem icon={<BusinessIcon />} label="Become a partner with us" to="/partner" highlighted />
          <MenuItem icon={<DiceNavIcon />} label="Dice Results" to="/dice-results" />
          <MenuItem icon={<BookIcon />} label="Game Guidelines" to="/game-guidelines" />
        </div>

        <button
          type="button"
          onClick={() => auth.logout('user_logout')}
          className="mt-6 w-full rounded-xl border border-border bg-surface py-4 text-base font-medium text-textWhite"
        >
          Log out
        </button>
      </div>
    </div>
  );
}
