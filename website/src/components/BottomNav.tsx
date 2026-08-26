import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLoginSignupModal } from '../context/LoginSignupModalContext';
import { prefetchCasinoPage } from '../utils/prefetchGameAssets';

/** Dual Cards bottom bar — matches APK DualCardsBottomBar */
const GOLD_MID = '#FFD54F';
const GOLD_DEEP = '#C9A227';

function HomeIcon({ active }: { active: boolean }) {
  return (
    <svg className="h-7 w-7" viewBox="0 0 24 24" fill={active ? 'currentColor' : 'currentColor'}>
      <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z" />
    </svg>
  );
}

function BoltIcon() {
  return (
    <svg className="h-7 w-7" viewBox="0 0 24 24" fill="currentColor">
      <path d="M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.02-.05.04-.1.07-.14L13 3h1l-1 7h3.5c.49 0 .56.33.47.67l-.04.13L11 21z" />
    </svg>
  );
}

function WalletIcon() {
  return (
    <svg className="h-7 w-7" viewBox="0 0 24 24" fill="currentColor">
      <path d="M21 7.28V5c0-1.1-.9-2-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h14c1.1 0 2-.9 2-2v-2.28A2 2 0 0022 15V9c0-.74-.4-1.39-1-1.72zM20 9v6h-7V9h7zM5 19V5h14v2h-6c-1.1 0-2 .9-2 2v6c0 1.1.9 2 2 2h6v2H5z" />
      <circle cx="16" cy="12" r="1.5" />
    </svg>
  );
}

function ProfileIcon() {
  return (
    <svg className="h-7 w-7" viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
    </svg>
  );
}

function NavItem({
  label,
  active,
  onClick,
  to,
  icon,
}: {
  label: string;
  active: boolean;
  onClick?: () => void;
  to?: string;
  icon: React.ReactNode;
}) {
  const color = active ? GOLD_MID : `${GOLD_DEEP}B3`;
  const className =
    'flex flex-col items-center justify-center gap-0.5 px-2 py-1.5 text-[11px] font-bold';
  const style = { color };
  const content = (
    <>
      {icon}
      <span>{label}</span>
    </>
  );
  if (to) {
    return (
      <NavLink to={to} className={className} style={style}>
        {content}
      </NavLink>
    );
  }
  return (
    <button type="button" onClick={onClick} className={className} style={style}>
      {content}
    </button>
  );
}

export function BottomNav() {
  const auth = useAuth();
  const nav = useNavigate();
  const { showLoginSignupModal } = useLoginSignupModal();
  const loggedIn = !!auth.user;
  const location = useLocation();
  const path = location.pathname;

  const requireAuth = (action: () => void) => {
    if (!loggedIn) showLoginSignupModal();
    else action();
  };

  const openCasino = () => {
    prefetchCasinoPage();
    window.location.href = '/casino/';
  };

  const isHome = path === '/';
  const isLive = path === '/coming-soon' || path.startsWith('/sports');
  const isWallet = path === '/wallet' || path === '/deposit' || path === '/withdraw';
  const isProfile = path === '/me' || path.startsWith('/personal') || path === '/security';

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-[9999] mx-auto flex h-[78px] w-full max-w-[460px] items-center justify-evenly bg-[#0A0A0A] px-1"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      <NavItem label="HOME" active={isHome} to="/" icon={<HomeIcon active={isHome} />} />
      <NavItem
        label="LIVE"
        active={isLive}
        onClick={() => requireAuth(() => nav('/coming-soon'))}
        icon={<BoltIcon />}
      />

      <button
        type="button"
        onClick={openCasino}
        className="flex flex-col items-center justify-center gap-0.5 px-2 py-1.5 text-[11px] font-bold"
        style={{ color: GOLD_MID }}
        aria-label="Casino"
      >
        <img src="/ic_casino_chip.png" alt="" className="h-[34px] w-[34px] object-contain" />
        <span>CASINO</span>
      </button>

      <NavItem
        label="WALLET"
        active={isWallet}
        onClick={() => requireAuth(() => nav('/wallet'))}
        icon={<WalletIcon />}
      />
      <NavItem
        label="PROFILE"
        active={isProfile}
        onClick={() => {
          if (!loggedIn) showLoginSignupModal();
          else nav('/me');
        }}
        icon={<ProfileIcon />}
      />
    </nav>
  );
}
