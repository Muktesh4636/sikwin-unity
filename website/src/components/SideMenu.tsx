import { useEffect, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLoginSignupModal } from '../context/LoginSignupModalContext';
import { GAME_PAGE_HREF } from '../config';
import { prefetchGameAssets } from '../utils/prefetchGameAssets';

type SideMenuProps = {
  open: boolean;
  onClose: () => void;
};

function downloadApk() {
  const url = `/GunduAta.apk?t=${Date.now()}`;
  const a = document.createElement('a');
  a.href = url;
  a.download = 'GunduAta.apk';
  a.rel = 'noopener noreferrer';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

function IconMain({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M4 4h6v6H4V4zm10 0h6v6h-6V4zM4 14h6v6H4v-6zm10 0h6v6h-6v-6z" />
    </svg>
  );
}

function IconLive({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M8.5 8.5a5 5 0 017 7M6 6a8.5 8.5 0 0112 12M12 12.5a1.5 1.5 0 100-3 1.5 1.5 0 000 3z" />
    </svg>
  );
}

function IconCricket({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5 20l9-9M12 13l2.5 6.5M14 11l6.5 2.5" />
      <circle cx="17.5" cy="6.5" r="2.5" />
    </svg>
  );
}

function IconDice({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <rect x="4" y="4" width="16" height="16" rx="3" />
      <circle cx="9" cy="9" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="15" cy="9" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="9" cy="15" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="15" cy="15" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none" />
    </svg>
  );
}

function IconChitPat({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 3a7 7 0 00-7 7c0 2.2 1 4.1 2.6 5.4L6 21l4.2-1.6c.6.1 1.2.2 1.8.2a7 7 0 000-14z" />
      <path strokeLinecap="round" d="M12 9v4M12 15.5v.5" />
    </svg>
  );
}

function IconGift({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M20 12v8a1 1 0 01-1 1H5a1 1 0 01-1-1v-8M4 8h16v4H4V8zm8 0V4m0 4c-2 0-3.5-1-3.5-2.5S10 3 12 5c2-2 3.5-.5 3.5.5S14 8 12 8z" />
    </svg>
  );
}

function IconProfile({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M12 12a4 4 0 100-8 4 4 0 000 8zm0 2c-4 0-8 2-8 4v2h16v-2c0-2-4-4-8-4z" />
    </svg>
  );
}

function IconWallet({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 7a2 2 0 012-2h14a1 1 0 011 1v2H5a2 2 0 00-2 2v7a2 2 0 002 2h14a2 2 0 002-2v-5a1 1 0 00-1-1h-4a2 2 0 100 4h5" />
    </svg>
  );
}

function IconWithdraw({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <circle cx="12" cy="12" r="9" />
      <path strokeLinecap="round" d="M12 7v10M9.5 9.5c.5-.8 1.4-1.3 2.5-1.3 1.5 0 2.5.9 2.5 2.1S13.5 12.5 12 12.5 9.5 13.3 9.5 14.5c0 1.2 1.1 2.1 2.5 2.1 1.1 0 2-.5 2.5-1.3" />
    </svg>
  );
}

function IconHistory({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v5l3 2M3 12a9 9 0 109-9 9.2 9.2 0 00-6.3 2.5L3 8m0 0V3m0 5h5" />
    </svg>
  );
}

function IconApple({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M16.7 12.7c0-2.1 1.7-3.1 1.8-3.2-1-1.4-2.5-1.6-3-1.7-1.3-.1-2.5.8-3.1.8-.7 0-1.7-.7-2.8-.7-1.4 0-2.8.9-3.5 2.2-1.5 2.6-.4 6.5 1.1 8.6.7 1 1.6 2.2 2.7 2.1 1.1 0 1.5-.7 2.8-.7s1.6.7 2.8.7 1.9-1.1 2.6-2.1c.8-1.2 1.1-2.3 1.2-2.4-.1 0-2.2-.8-2.2-3.6zM14.5 5.6c.6-.7 1-1.7.9-2.6-.8 0-1.9.6-2.5 1.3-.5.6-1 1.6-.9 2.5 1 .1 1.9-.5 2.5-1.2z" />
    </svg>
  );
}

function IconAndroid({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M6.5 9.5a1 1 0 011 1V15a1 1 0 11-2 0v-4.5a1 1 0 011-1zm11 0a1 1 0 011 1V15a1 1 0 11-2 0v-4.5a1 1 0 011-1zM8 18.5V11h8v7.5a1.5 1.5 0 01-1.5 1.5h-5A1.5 1.5 0 018 18.5zM9.2 5.2L8 3.2a.5.5 0 01.8-.5l1.4 2.1a5.9 5.9 0 013.6 0L15.2 2.7a.5.5 0 01.8.5l-1.2 2A5.5 5.5 0 0117.5 10h-11a5.5 5.5 0 012.7-4.8zM10 8.2a.7.7 0 100-1.4.7.7 0 000 1.4zm4 0a.7.7 0 100-1.4.7.7 0 000 1.4z" />
    </svg>
  );
}

type MenuItem = {
  key: string;
  label: string;
  icon: ReactNode;
  highlight?: boolean;
  onClick: () => void;
};

export function SideMenu({ open, onClose }: SideMenuProps) {
  const nav = useNavigate();
  const auth = useAuth();
  const loggedIn = !!auth.user;
  const { showLoginSignupModal } = useLoginSignupModal();

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener('keydown', onKey);
    };
  }, [open, onClose]);

  const requireAuth = (action: () => void) => {
    if (!loggedIn) {
      onClose();
      showLoginSignupModal();
      return;
    }
    action();
  };

  const go = (path: string, authRequired = false) => {
    if (authRequired && !loggedIn) {
      onClose();
      showLoginSignupModal();
      return;
    }
    onClose();
    nav(path);
  };

  const openGame = () => {
    requireAuth(() => {
      onClose();
      prefetchGameAssets();
      window.setTimeout(() => {
        window.location.href = GAME_PAGE_HREF;
      }, 200);
    });
  };

  const items: MenuItem[] = [
    { key: 'main', label: 'Main', icon: <IconMain className="h-5 w-5" />, onClick: () => go('/') },
    { key: 'roulette', label: 'Auto Roulette', icon: <IconLive className="h-5 w-5" />, onClick: () => go('/roulette', true) },
    { key: 'trading', label: 'Stock Market', icon: <IconDice className="h-5 w-5" />, onClick: () => go('/trading', true) },
    { key: 'chicken', label: 'Chicken Road', icon: <IconDice className="h-5 w-5" />, onClick: () => go('/chicken-road', true) },
    { key: 'chicken2', label: 'Chicken Road 2', icon: <IconChitPat className="h-5 w-5" />, onClick: () => go('/chicken-road-2', true) },
    {
      key: 'gundu',
      label: 'Gundu Ata',
      icon: (
        <img
          src="/app_logo.jpg"
          alt=""
          className="h-6 w-6 rounded-full object-cover ring-1 ring-primaryYellow"
        />
      ),
      highlight: true,
      onClick: openGame,
    },
    { key: 'cricket', label: 'Cricket', icon: <IconCricket className="h-5 w-5" />, onClick: () => go('/coming-soon') },
    { key: 'rangu', label: 'Rangu', icon: <IconDice className="h-5 w-5" />, onClick: () => go('/coming-soon') },
    { key: 'chitpat', label: 'Chit Pat', icon: <IconChitPat className="h-5 w-5" />, onClick: () => go('/coming-soon') },
    { key: 'bonuses', label: 'Bonuses', icon: <IconGift className="h-5 w-5" />, onClick: () => go('/daily-reward', true) },
    { key: 'profile', label: 'Profile', icon: <IconProfile className="h-5 w-5" />, onClick: () => go('/me', true) },
    { key: 'deposit', label: 'Deposit', icon: <IconWallet className="h-5 w-5" />, onClick: () => go('/deposit', true) },
    { key: 'withdraw', label: 'Withdrawal', icon: <IconWithdraw className="h-5 w-5" />, onClick: () => go('/withdraw', true) },
    {
      key: 'history',
      label: 'Payments history',
      icon: <IconHistory className="h-5 w-5" />,
      onClick: () => go('/transactions', true),
    },
  ];

  return (
    <div
      className={`fixed inset-0 z-[10000] mx-auto w-full max-w-[460px] ${open ? 'pointer-events-auto' : 'pointer-events-none'}`}
      aria-hidden={!open}
    >
      <button
        type="button"
        className={`absolute inset-0 bg-black/60 transition-opacity duration-300 ${open ? 'opacity-100' : 'opacity-0'}`}
        aria-label="Close menu"
        onClick={onClose}
      />

      <aside
        className={`absolute inset-y-0 left-0 flex w-[78%] max-w-[320px] flex-col bg-[#0a0a0a] shadow-2xl transition-transform duration-300 ease-out ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
        role="dialog"
        aria-modal="true"
        aria-label="Menu"
      >
        <div className="flex items-center justify-between px-4 pb-2 pt-4">
          <h2 className="text-xl font-bold text-white">Menu</h2>
          <button
            type="button"
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded-full text-primaryYellow transition-opacity hover:opacity-80"
            aria-label="Close"
          >
            <svg className="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path strokeLinecap="round" d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-4 pb-6">
          <Link
            to="/deposit"
            onClick={(e) => {
              e.preventDefault();
              go('/deposit', true);
            }}
            className="mt-1 flex w-full items-center justify-center rounded-xl bg-primaryYellow py-3.5 text-base font-black uppercase tracking-wide text-black transition-opacity hover:opacity-95 active:opacity-90"
          >
            Deposit
          </Link>

          <div className="mt-5">
            <p className="text-[11px] font-semibold uppercase tracking-wider text-white/80">Our applications:</p>
            <div className="mt-2.5 flex items-center gap-3">
              <button
                type="button"
                onClick={() => go('/coming-soon')}
                className="flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-white transition-opacity hover:opacity-90"
                aria-label="iOS app"
              >
                <IconApple className="h-6 w-6" />
              </button>
              <button
                type="button"
                onClick={() => {
                  onClose();
                  downloadApk();
                }}
                className="flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-primaryYellow transition-opacity hover:opacity-90"
                aria-label="Download Android APK"
              >
                <IconAndroid className="h-6 w-6" />
              </button>
              <span className="rounded-md bg-primaryYellow px-2 py-0.5 text-[10px] font-black uppercase tracking-wide text-black">
                New
              </span>
            </div>
          </div>

          <nav className="mt-5 flex flex-col">
            {items.map((item) => (
              <button
                key={item.key}
                type="button"
                onClick={item.onClick}
                className="flex items-center gap-3.5 border-b border-white/5 py-3.5 text-left transition-opacity hover:opacity-90 active:opacity-80 last:border-b-0"
              >
                <span className={`flex h-6 w-6 shrink-0 items-center justify-center ${item.highlight ? 'text-primaryYellow' : 'text-primaryYellow'}`}>
                  {item.icon}
                </span>
                <span className={`text-[15px] font-semibold ${item.highlight ? 'text-primaryYellow' : 'text-white'}`}>
                  {item.label}
                </span>
              </button>
            ))}
          </nav>
        </div>
      </aside>
    </div>
  );
}
