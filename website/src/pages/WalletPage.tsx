import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiWallet, type Wallet } from '../api/endpoints';

/** Faint wallet icon on gold card — same as APK Icons.Default.AccountBalanceWallet, white 20% */
function WalletIconFaint() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="absolute right-4 top-2 h-[100px] w-[100px] text-white/20" aria-hidden>
      <path d="M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" />
    </svg>
  );
}

/** Dark wallet icon for Main wallet card — same as APK Icons.Default.AccountBalanceWallet */
function WalletIconDark() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="h-12 w-12 shrink-0 text-[#A9A9A9]" aria-hidden>
      <path d="M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" />
    </svg>
  );
}

function RefreshIcon({ className }: { className?: string }) {
  return (
    <svg className={className ?? 'h-7 w-7'} fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
    </svg>
  );
}

function DownArrowIcon({ className = 'h-5 w-5 shrink-0' }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 14l-7 7m0 0l-7-7m7 7V3" />
    </svg>
  );
}

function UpArrowIcon({ className = 'h-5 w-5 shrink-0' }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 10l7-7m0 0l7 7m-7-7v18" />
    </svg>
  );
}

export function WalletPage() {
  const nav = useNavigate();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    try {
      setLoading(true);
      const resp = await apiWallet();
      setWallet(resp.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const balance = wallet?.balance ?? '0.00';

  return (
    <div className="mobile-frame min-h-dvh bg-[#1A1A1A]">
      {/* Header: golden back + golden title to match APK */}
      {/* APK: header padding 16.dp, back 24.dp, title 20.sp Bold */}
      <header className="sticky top-0 z-40 bg-[#1A1A1A]/95 backdrop-blur">
        <div className="mx-auto flex max-w-[460px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="flex h-10 w-10 items-center justify-center rounded text-[#DAA520] transition-opacity hover:opacity-90"
            aria-label="Back"
          >
            <BackArrow className="h-6 w-6" />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-[#DAA520]">My wallet</h1>
          <div className="w-10" />
        </div>
      </header>

      <div className="px-4 py-4">
        {/* Total/INR card - APK: height 140.dp, rounded 24.dp, padding 20.dp; label 16.sp Medium, amount 36.sp Bold; refresh 28.dp */}
        <div className="relative flex h-[140px] overflow-hidden rounded-3xl bg-[#E6B84D] px-5 py-5">
          <WalletIconFaint />
          <div className="relative flex w-full items-center justify-between">
            <div className="flex flex-col justify-center">
              <div className="text-base font-medium text-[#6B6B6B]">Total/INR</div>
              <div className="mt-3 flex items-center gap-3">
                <span className="text-4xl font-bold text-black">₹{loading ? '—' : balance}</span>
                <button
                  type="button"
                  onClick={load}
                  disabled={loading}
                  className="rounded-full p-1.5 text-black transition-opacity hover:bg-black/10 disabled:opacity-50"
                  aria-label="Refresh balance"
                >
                  <RefreshIcon className="h-7 w-7" />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Main wallet card - APK: rounded 24.dp, padding 24.dp; title 18.sp Bold, amount 32.sp Bold; icon 48.dp; spacer 24.dp; divider; actions padding 16.dp; action text 16.sp Medium, icon 20.dp */}
        <div className="mt-5 flex flex-col overflow-hidden rounded-3xl bg-[#F0F0F0] px-6 py-6">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-lg font-bold text-black">Main wallet</div>
              <div className="mt-6 text-[32px] font-bold leading-none text-black">
                ₹{loading ? '—' : balance}
              </div>
            </div>
            <WalletIconDark />
          </div>

          <div className="mt-4 h-px bg-[#D3D3D3]" aria-hidden />
          <div className="mt-4 flex items-center">
            <Link
              to="/withdraw"
              className="flex flex-1 items-center justify-center gap-2 py-2 text-base font-medium text-black"
            >
              <DownArrowIcon />
              <span>Withdrawal</span>
            </Link>
            <div className="h-6 w-px bg-[#D3D3D3]" aria-hidden />
            <Link
              to="/deposit"
              className="flex flex-1 items-center justify-center gap-2 py-2 text-base font-medium text-black"
            >
              <UpArrowIcon />
              <span>Deposit</span>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
