import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { apiWallet, type Wallet } from '../api/endpoints';
import { useTranslations } from '../context/LocaleContext';
import { useLoginSignupModal } from '../context/LoginSignupModalContext';
import { GAME_PAGE_HREF } from '../config';
import { prefetchGameAssets } from '../utils/prefetchGameAssets';

function openGame() {
  // Start downloading Unity assets immediately, then navigate a moment later.
  prefetchGameAssets();
  window.setTimeout(() => {
    window.location.href = GAME_PAGE_HREF;
  }, 200);
}

function SearchBar({ placeholder, onSearch }: { placeholder: string; onSearch?: (query: string) => void }) {
  const [query, setQuery] = useState('');
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const q = query.trim().toLowerCase();
    if (q && onSearch) onSearch(q);
    else if (onSearch) onSearch('');
  };
  return (
    <form onSubmit={handleSubmit} className="relative mx-4 mt-2">
      <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-textGrey">
        <SearchIcon />
      </span>
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={placeholder}
        className="w-full rounded-xl border-0 bg-surface py-3 pl-11 pr-4 text-textWhite placeholder:text-textGrey focus:ring-2 focus:ring-primaryYellow"
        aria-label="Search games"
      />
    </form>
  );
}

function SearchIcon() {
  return (
    <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
    </svg>
  );
}

/** Customer support / headset icon - same as APK Icons.Default.SupportAgent */
function SupportAgentIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 18v-6a9 9 0 0 1 18 0v6" />
      <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z" />
    </svg>
  );
}

// Same 5 banners as Android app: colors from HomeScreen.kt (Brush.horizontalGradient)
const BANNERS: { title: string; subtitle: string; buttonText: string; colorStart: string; colorEnd: string; route: string }[] = [
  {
    title: 'DAILY REWARD',
    subtitle: 'SPIN THE WHEEL FOR BONUS!',
    buttonText: 'SPIN NOW',
    colorStart: '#F9A825',
    colorEnd: '#F57F17',
    route: '/daily-reward',
  },
  {
    title: 'REFER & EARN',
    subtitle: 'Earn up to ₹1 Lakh!',
    buttonText: 'INVITE',
    colorStart: '#455A64',
    colorEnd: '#263238',
    route: '/refer',
  },
  {
    title: 'MEGA SPIN',
    subtitle: 'Deposit ₹2000 or more to spin the wheel!',
    buttonText: 'SPIN NOW',
    colorStart: '#4A148C',
    colorEnd: '#880E4F',
    route: '/lucky-draw',
  },
  {
    title: 'USDT SPECIAL ₮',
    subtitle: 'Get 5% EXTRA CASHBACK on all USDT deposits!',
    buttonText: 'DEPOSIT NOW',
    colorStart: '#00897B',
    colorEnd: '#004D40',
    route: '/deposit?method=USDT',
  },
  {
    title: 'FRANCHISE',
    subtitle: 'Get Gundu Ata franchise at 50% off — Get in touch today!',
    buttonText: 'LEARN MORE',
    colorStart: '#795548',
    colorEnd: '#5D4037',
    route: '/partner',
  },
];

const BANNER_COUNT = BANNERS.length;
// Three copies so we can always scroll right: 0-4, 5-9, 10-14. When at 14 we jump to 9 (same banner) then advance to 10.
const COPIES = 3;
const TOTAL_SLIDES = BANNER_COUNT * COPIES;

function PromotionalBanners() {
  const nav = useNavigate();
  // Start in middle set so we can scroll right for a full cycle before needing to jump
  const [scrollIndex, setScrollIndex] = useState(BANNER_COUNT);
  const [lastClickTime, setLastClickTime] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);
  const isProgrammaticScroll = useRef(false);
  const scrollEndTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const jumpTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const skipNextSmoothScroll = useRef(false);
  const CLICK_COOLDOWN = 1000;
  const page = scrollIndex % BANNER_COUNT; // for dots

  // Auto-advance: always move right from current scroll position (never jump back after user scroll)
  useEffect(() => {
    const id = setInterval(() => {
      const el = scrollRef.current;
      if (!el) return;
      const w = el.clientWidth;
      if (w <= 0) return;
      const currentIndex = Math.round(el.scrollLeft / w);
      const nextIndex = Math.min(currentIndex + 1, TOTAL_SLIDES - 1);
      isProgrammaticScroll.current = true;
      setScrollIndex(nextIndex);
    }, 4000);
    return () => clearInterval(id);
  }, []);

  // When at the very last slide, instantly jump to same banner in previous set (no visual change)
  useEffect(() => {
    if (scrollIndex !== TOTAL_SLIDES - 1) return;
    const el = scrollRef.current;
    if (!el) return;
    if (jumpTimeout.current) clearTimeout(jumpTimeout.current);
    jumpTimeout.current = setTimeout(() => {
      jumpTimeout.current = null;
      const w = el.clientWidth;
      const sameBannerIndex = TOTAL_SLIDES - 1 - BANNER_COUNT; // 14 - 5 = 9 (same content as 14)
      skipNextSmoothScroll.current = true; // so sync effect does instant scroll, not smooth
      isProgrammaticScroll.current = true;
      el.scrollLeft = w * sameBannerIndex;
      setScrollIndex(sameBannerIndex);
    }, 450); // after smooth scroll to last slide finishes
  }, [scrollIndex]);

  useEffect(() => {
    if (!isProgrammaticScroll.current) return;
    if (scrollIndex >= TOTAL_SLIDES) return;
    const el = scrollRef.current;
    if (!el) return;
    const targetLeft = scrollIndex * el.clientWidth;
    if (skipNextSmoothScroll.current) {
      skipNextSmoothScroll.current = false;
      el.scrollLeft = targetLeft; // instant jump, no smooth scroll (avoids visible left scroll)
    } else {
      el.scrollTo({ left: targetLeft, behavior: 'smooth' });
    }
    isProgrammaticScroll.current = false;
  }, [scrollIndex]);

  const handleScroll = () => {
    if (isProgrammaticScroll.current) return;
    const el = scrollRef.current;
    if (!el) return;
    const w = el.clientWidth;
    const maxScroll = w * (TOTAL_SLIDES - 1);
    // User scrolled past end: wrap to same banner in previous set so next advance scrolls right
    if (el.scrollLeft > maxScroll - 2) {
      const sameBannerIndex = TOTAL_SLIDES - 1 - BANNER_COUNT;
      el.scrollLeft = w * sameBannerIndex;
      setScrollIndex(sameBannerIndex);
      return;
    }
    if (scrollEndTimer.current) clearTimeout(scrollEndTimer.current);
    scrollEndTimer.current = setTimeout(() => {
      scrollEndTimer.current = null;
      const rawIndex = Math.round(el.scrollLeft / w);
      const index = Math.max(0, Math.min(rawIndex, TOTAL_SLIDES - 1));
      setScrollIndex(index);
    }, 50);
  };

  const handleBannerClick = (route: string) => {
    const now = Date.now();
    if (now - lastClickTime < CLICK_COOLDOWN) return;
    setLastClickTime(now);
    if (route === '/gundu-ata') {
      openGame();
    } else if (route.startsWith('/')) {
      nav(route);
    }
  };

  return (
    <div className="mx-4 mt-4 overflow-hidden rounded-2xl text-center">
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex overflow-x-auto snap-x snap-proximity overscroll-x-contain scroll-smooth rounded-2xl scrollbar-hide"
      >
        {/* Three copies for circular loop: always scroll right, jump from end to same banner in previous set */}
        {Array.from({ length: COPIES }, () => BANNERS).flat().map((banner, i) => (
          <div
            key={i}
            className="min-w-full shrink-0 snap-center snap-always"
          >
            <div
              className="min-h-[190px] rounded-2xl px-5 py-6 flex flex-col items-center justify-center cursor-pointer active:opacity-95"
              style={{
                background: `linear-gradient(to right, ${banner.colorStart}, ${banner.colorEnd})`,
              }}
            >
              <div
                className="text-2xl font-extrabold uppercase tracking-wide text-center"
                style={{ color: '#FFCC00' }}
              >
                {banner.title}
              </div>
              <div
                className="mt-1.5 text-sm font-bold text-center w-full max-w-[280px]"
                style={{ color: '#FFFFFF' }}
              >
                {banner.subtitle}
              </div>
              <button
                type="button"
                onClick={() => handleBannerClick(banner.route)}
                className="mt-3 rounded-xl px-6 py-2.5 text-base font-black uppercase"
                style={{ backgroundColor: '#FFCC00', color: '#121212' }}
              >
                {banner.buttonText}
              </button>
            </div>
          </div>
        ))}
      </div>
      <div className="mt-2.5 flex justify-center gap-1.5">
        {BANNERS.map((_, i) => (
          <button
            key={i}
            type="button"
            onClick={() => {
              isProgrammaticScroll.current = true;
              setScrollIndex(i);
            }}
            aria-label={`Go to banner ${i + 1}`}
            className={`h-2 w-2 rounded-full transition-colors ${page === i ? 'bg-[#FFCC00]' : 'bg-[#BDBDBD]'}`}
          />
        ))}
      </div>
    </div>
  );
}

// Names list for continuous winnings (same as APK)
const WINNING_NAMES = [
  'Teresa', 'Sandra', 'Ishaan', 'Kali', 'Muktesh', 'Rahul', 'Priya', 'Vikram', 'Anjali', 'Suresh',
  'Kiran', 'Deepak', 'Amit', 'Sneha', 'Rohan', 'Neha', 'Arjun', 'Pooja', 'Karan', 'Ishita',
  'Sanjay', 'Ritu', 'Vijay', 'Anita', 'Rajesh', 'Sunita', 'Manoj', 'Kavita', 'Vinay', 'Meena',
  'Sandeep', 'Rekha', 'Abhishek', 'Swati', 'Prashant', 'Aarti', 'Alok', 'Shweta', 'Vivek', 'Jyoti',
  'Ayushmann', 'Taapsee', 'Rajkummar', 'Bhumi', 'Vicky', 'Kriti', 'Kartik', 'Kiara', 'Sara',
  'Aditya', 'Janhvi', 'Ananya', 'Tara', 'Rakul', 'Rashmika', 'Dulquer', 'Sai Pallavi',
];

type WinningParticle = { id: number; text: string };

function WinningParticleItem({
  text,
  onAnimationEnd,
}: {
  text: string;
  onAnimationEnd: () => void;
}) {
  return (
    <span
      className="absolute bottom-0 left-1/2 z-10 whitespace-nowrap text-xs font-bold text-primaryYellow drop-shadow-[0_0_2px_rgba(0,0,0,0.8)]"
      style={{ animation: 'winning-particle 3s linear forwards' }}
      onAnimationEnd={onAnimationEnd}
    >
      {text}
    </span>
  );
}

function HotGamesSection({ onPlayGame }: { onPlayGame: () => void }) {
  const [particles, setParticles] = useState<WinningParticle[]>([]);
  const nextIdRef = useRef(0);

  useEffect(() => {
    const id = setInterval(() => {
      const name = WINNING_NAMES[Math.floor(Math.random() * WINNING_NAMES.length)];
      const text = `${name} +${Math.floor(100 + Math.random() * 4900)}`;
      setParticles((prev) => [...prev, { id: nextIdRef.current++, text }]);
    }, 1200);
    return () => clearInterval(id);
  }, []);

  const removeParticle = (id: number) => {
    setParticles((prev) => prev.filter((p) => p.id !== id));
  };

  const t = useTranslations();
  return (
    <div className="relative mt-6 px-4">
      <h2 className="text-lg font-bold text-textWhite">{t('hot_games')}</h2>
      <div className="relative mt-3 flex justify-center">
        {/* Customer support icon - left of video, same as APK (SupportAgent/headset) */}
        <Link
          to="/help-center"
          className="absolute -left-1 top-1/2 flex h-12 w-12 -translate-y-1/2 items-center justify-center rounded-full bg-surface text-primaryYellow shadow-md transition-opacity hover:opacity-90 active:opacity-80"
          aria-label={t('help_center')}
        >
          <SupportAgentIcon className="h-7 w-7" />
        </Link>
        <button
          type="button"
          onClick={onPlayGame}
          className="mx-auto w-full max-w-[165px] overflow-hidden rounded-2xl shadow-lg focus:outline-none focus:ring-2 focus:ring-primaryYellow"
          aria-label="Play Gundu Ata"
        >
          <div className="aspect-[3/4] w-full overflow-hidden bg-[#1565C0]">
            <video
              src="/gundu_ata_video.mp4"
              className="h-full w-full object-cover object-center pointer-events-none"
              autoPlay
              muted
              loop
              playsInline
              aria-hidden
            />
          </div>
        </button>
        <div className="absolute -right-4 top-1/2 flex w-32 min-w-[120px] -translate-y-1/2 flex-col items-center justify-end">
          <div className="relative h-32 w-full overflow-visible">
            {particles.map((p) => (
              <WinningParticleItem
                key={p.id}
                text={p.text}
                onAnimationEnd={() => removeParticle(p.id)}
              />
            ))}
          </div>
          <Link
            to="/leaderboard"
            className="block w-full max-w-[64px] cursor-pointer transition-opacity hover:opacity-90 active:opacity-80"
            aria-label="Leaderboard"
          >
            <img
              src="/treasure_chest.png"
              alt=""
              className="h-auto w-full max-w-[64px] object-contain"
            />
          </Link>
        </div>
      </div>
    </div>
  );
}

export function HomePage() {
  const auth = useAuth();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [showDownloadApk, setShowDownloadApk] = useState(false);

  useEffect(() => {
    let alive = true;
    apiWallet()
      .then((r) => { if (alive) setWallet(r.data); })
      .catch(() => {});
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    const id = setTimeout(() => setShowDownloadApk(true), 5000);
    return () => clearTimeout(id);
  }, []);

  const t = useTranslations();
  const balance = wallet?.balance ?? '0.00';
  const loggedIn = !!auth.user;
  const { showLoginSignupModal } = useLoginSignupModal();
  const openGameOrShowLogin = useCallback(() => {
    if (loggedIn) openGame();
    else showLoginSignupModal();
  }, [loggedIn, showLoginSignupModal]);

  return (
    <div className="mobile-frame min-h-dvh bg-appBg pb-20">
      {/* Header: logo + GUNDU ATA (left) | Login + Register or balance + (right) */}
      <header className="flex items-center justify-between bg-appBg px-3 py-3">
        <button
          type="button"
          onClick={openGameOrShowLogin}
          className="flex min-w-0 flex-1 items-center gap-2 text-left"
          aria-label="Play Gundu Ata"
        >
          <img
            src="/app_logo.jpg"
            alt="Gundu Ata"
            className="h-10 w-10 shrink-0 rounded-full object-cover ring-2 ring-border"
          />
          <span
            className="gundu-ata-shimmer truncate text-xl font-black tracking-wide"
            style={{
              background: 'linear-gradient(90deg, #FFCC00 0%, #FFCC00 35%, #ffffff 50%, #FFCC00 65%, #FFCC00 100%)',
              backgroundSize: '400% 100%',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              backgroundClip: 'text',
              animation: 'gundu-ata-shimmer 2.5s linear infinite',
            }}
          >
            GUNDU ATA
          </span>
        </button>
        <div className="flex shrink-0 items-center gap-2">
          {loggedIn ? (
            <>
              <div className="flex items-center gap-2 rounded-full bg-surface px-3 py-1.5">
                <span className="font-bold text-primaryYellow">₹</span>
                <span className="font-bold text-textWhite">{balance}</span>
              </div>
              <Link
                to="/deposit"
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primaryYellow text-black"
                aria-label="Add money"
              >
                <span className="text-xl font-bold leading-none">+</span>
              </Link>
            </>
          ) : (
            <>
              <Link to="/login" className="text-base font-semibold text-textWhite">{t('login')}</Link>
              <Link
                to="/signup"
                className="rounded-full bg-[#FFCC00] px-4 py-2 text-base font-semibold text-black"
              >
                {t('register')}
              </Link>
            </>
          )}
        </div>
      </header>

      <SearchBar placeholder={t('search_games')} onSearch={() => openGameOrShowLogin()} />
      <PromotionalBanners />
      {showDownloadApk && (
        <button
          type="button"
          onClick={() => {
            const url = `/GunduAta.apk?t=${Date.now()}`;
            const a = document.createElement('a');
            a.href = url;
            a.download = 'GunduAta.apk';
            a.rel = 'noopener noreferrer';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
          }}
          className="mx-4 mt-4 flex w-[calc(100%-2rem)] items-center justify-center gap-2 rounded-2xl bg-surface px-4 py-4 font-semibold text-textWhite transition-opacity hover:opacity-95 active:opacity-90"
          aria-label={t('download_apk')}
        >
          <svg className="h-6 w-6 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          <span>{t('download_apk')}</span>
        </button>
      )}
      <HotGamesSection onPlayGame={openGameOrShowLogin} />
    </div>
  );
}
