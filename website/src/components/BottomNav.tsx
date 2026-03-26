import { NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTranslations } from '../context/LocaleContext';
import { useLoginSignupModal } from '../context/LoginSignupModalContext';
import { GAME_PAGE_HREF } from '../config';
import { prefetchGameAssets } from '../utils/prefetchGameAssets';

function HomeIcon({ active }: { active: boolean }) {
  return (
    <svg
      className="h-5 w-5"
      fill={active ? 'currentColor' : 'none'}
      stroke="currentColor"
      viewBox="0 0 24 24"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
      />
    </svg>
  );
}

function DiceIcon({ active }: { active: boolean }) {
  return (
    <img
      src="/dice_3d.png"
      alt=""
      className={`h-6 w-6 object-contain brightness-0 invert ${active ? 'opacity-100' : 'opacity-60'}`}
    />
  );
}

/** Me icon — same as Kotlin APK Icons.Default.AccountCircle (circle with person inside) */
function MeIcon({ active }: { active: boolean }) {
  return (
    <svg
      className={`h-5 w-5 ${active ? 'opacity-100' : 'opacity-60'}`}
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z" />
    </svg>
  );
}

export function BottomNav() {
  const t = useTranslations();
  const auth = useAuth();
  const { showLoginSignupModal } = useLoginSignupModal();
  const loggedIn = !!auth.user;
  const location = useLocation();
  const isMeActive = location.pathname === '/me';
  const handlePlayClick = () => {
    if (loggedIn) {
      prefetchGameAssets();
      window.setTimeout(() => {
        window.location.href = GAME_PAGE_HREF;
      }, 200);
    }
    else showLoginSignupModal();
  };
  const handleMeClick = (e: React.MouseEvent) => {
    if (!loggedIn) {
      e.preventDefault();
      showLoginSignupModal();
    }
  };
  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-[9999] mx-auto w-full max-w-[460px] border-t border-[#2a2a2a] bg-[#121212]"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      <div className="grid grid-cols-3 px-2 py-2">
        <NavLink
          to="/"
          className={({ isActive }) =>
            'flex flex-col items-center justify-center gap-1 rounded-xl px-2 py-1.5 text-xs font-semibold ' +
            (isActive ? 'text-[#FFCC00]' : 'text-[#BDBDBD]')
          }
        >
          {({ isActive }) => (
            <>
              <HomeIcon active={isActive} />
              <span>{t('home')}</span>
            </>
          )}
        </NavLink>
        <button
          type="button"
          onClick={handlePlayClick}
          className="flex flex-col items-center justify-center gap-1 rounded-xl px-2 py-1.5 text-xs font-semibold text-[#BDBDBD]"
        >
          <DiceIcon active={false} />
          <span>GUNDU ATA</span>
        </button>
        {loggedIn ? (
          <NavLink
            to="/me"
            className={({ isActive }) =>
              'flex flex-col items-center justify-center gap-1 rounded-xl px-2 py-1.5 text-xs font-semibold ' +
              (isActive ? 'text-[#FFCC00]' : 'text-[#BDBDBD]')
            }
          >
            {({ isActive }) => (
              <>
                <MeIcon active={isActive} />
                <span>{t('me')}</span>
              </>
            )}
          </NavLink>
        ) : (
          <button
            type="button"
            onClick={handleMeClick}
            className={
              'flex flex-col items-center justify-center gap-1 rounded-xl px-2 py-1.5 text-xs font-semibold ' +
              (isMeActive ? 'text-[#FFCC00]' : 'text-[#BDBDBD]')
            }
          >
            <MeIcon active={isMeActive} />
            <span>{t('me')}</span>
          </button>
        )}
      </div>
    </nav>
  );
}
