import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { apiWallet, type Wallet } from '../api/endpoints';
import { useTranslations } from '../context/LocaleContext';
import { useLoginSignupModal } from '../context/LoginSignupModalContext';
import { GAME_PAGE_HREF } from '../config';
import { prefetchGameAssets, prefetchCasinoPage } from '../utils/prefetchGameAssets';
import { formatIndian } from '../utils/formatMoney';
import { SideMenu } from '../components/SideMenu';

/** Dual Cards theme — APK default (ThemePreferences.THEME_DUAL_CARDS) */
const GOLD_LIGHT = '#FFE082';
const GOLD_MID = '#FFD54F';
const GOLD_DEEP = '#C9A227';
const GOLD_BRUSH = `linear-gradient(180deg, ${GOLD_LIGHT}, ${GOLD_MID}, ${GOLD_DEEP})`;

type Banner = {
  src: string;
  alt: string;
  onPlay: () => void;
};

function openUnityGame() {
  prefetchGameAssets();
  window.setTimeout(() => {
    window.location.href = GAME_PAGE_HREF;
  }, 200);
}

function SearchBar({ placeholder, onSearch }: { placeholder: string; onSearch?: (query: string) => void }) {
  const [query, setQuery] = useState('');
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSearch?.(query.trim().toLowerCase());
  };
  return (
    <form onSubmit={handleSubmit} className="relative mx-4 mt-1">
      <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-white">
        <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
      </span>
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={placeholder}
        className="w-full rounded-xl border-0 bg-[#1E1E1E] py-3.5 pl-11 pr-4 text-white placeholder:text-[#BDBDBD] outline-none focus:ring-2 focus:ring-[#FFD54F]"
        aria-label="Search games"
      />
    </form>
  );
}

function PromoBannerCarousel({ banners }: { banners: Banner[] }) {
  const [index, setIndex] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const id = setInterval(() => {
      setIndex((i) => (i + 1) % banners.length);
    }, 4000);
    return () => clearInterval(id);
  }, [banners.length]);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTo({ left: index * el.clientWidth, behavior: 'smooth' });
  }, [index]);

  return (
    <div className="mx-4 mt-4 overflow-hidden">
      <div
        ref={scrollRef}
        className="flex snap-x snap-mandatory overflow-x-auto scroll-smooth scrollbar-hide"
        onScroll={() => {
          const el = scrollRef.current;
          if (!el?.clientWidth) return;
          setIndex(Math.round(el.scrollLeft / el.clientWidth));
        }}
      >
        {banners.map((b) => (
          <button
            key={b.alt}
            type="button"
            onClick={b.onPlay}
            className="relative min-w-full shrink-0 snap-center overflow-hidden rounded-2xl"
            aria-label={b.alt}
          >
            <img src={b.src} alt={b.alt} className="h-[190px] w-full object-cover" />
          </button>
        ))}
      </div>
      <div className="mt-2.5 flex justify-center gap-1.5">
        {banners.map((_, i) => (
          <button
            key={i}
            type="button"
            aria-label={`Go to banner ${i + 1}`}
            onClick={() => setIndex(i)}
            className={`h-2 rounded-full transition-all ${i === index ? 'w-5 bg-[#FFD54F]' : 'w-2 bg-[#BDBDBD]'}`}
          />
        ))}
      </div>
    </div>
  );
}

function CategoryCircle({
  label,
  icon,
  imageSrc,
  onClick,
}: {
  label: string;
  icon?: React.ReactNode;
  imageSrc?: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-[68px] shrink-0 flex-col items-center"
      aria-label={label}
    >
      <span
        className="flex h-[54px] w-[54px] items-center justify-center rounded-full border-[1.5px] bg-[#121212]"
        style={{ borderColor: GOLD_MID }}
      >
        {imageSrc ? (
          <img src={imageSrc} alt="" className="h-10 w-10 object-contain" />
        ) : (
          <span style={{ color: GOLD_MID }}>{icon}</span>
        )}
      </span>
      <span
        className="mt-1.5 max-w-full truncate text-center font-bold"
        style={{ color: GOLD_MID, fontSize: label.length > 6 ? 8 : 10 }}
      >
        {label}
      </span>
    </button>
  );
}

function SvgIcon({ d, className = 'h-6 w-6' }: { d: string; className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d={d} />
    </svg>
  );
}

export function HomePage() {
  const auth = useAuth();
  const nav = useNavigate();
  const t = useTranslations();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const { showLoginSignupModal } = useLoginSignupModal();
  const loggedIn = !!auth.user;
  const balance = formatIndian(wallet?.balance ?? '0.00');

  useEffect(() => {
    prefetchCasinoPage();
  }, []);

  useEffect(() => {
    let alive = true;
    apiWallet()
      .then((r) => {
        if (alive) setWallet(r.data);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  const requireLoginOr = useCallback(
    (action: () => void) => {
      if (!loggedIn) showLoginSignupModal();
      else action();
    },
    [loggedIn, showLoginSignupModal]
  );

  const goGame = () => requireLoginOr(openUnityGame);
  /** Browse games freely; login is required only when placing bets inside a game. */
  const goPath = (path: string) => nav(path);
  const goPathAuth = (path: string) => requireLoginOr(() => nav(path));

  const goCockFight = () => goPath('/coming-soon');

  const banners: Banner[] = [
    {
      src: '/live_casino_banner.png',
      alt: 'Live Casino',
      onPlay: goGame,
    },
    {
      src: '/cock_fight_banner.jpg',
      alt: 'Cock Fight',
      onPlay: goCockFight,
    },
    {
      src: '/auto_roulette_banner.png',
      alt: 'Auto Roulette',
      onPlay: () => goPath('/roulette'),
    },
    {
      src: '/referral_banner.jpg',
      alt: 'Refer & Earn',
      onPlay: () => goPathAuth('/refer'),
    },
    {
      src: '/vortex_banner.jpg',
      alt: 'Vortex',
      onPlay: () => goPath('/vortex'),
    },
  ];

  return (
    <div className="mobile-frame min-h-dvh bg-black pb-24">
      <SideMenu open={menuOpen} onClose={() => setMenuOpen(false)} />

      {/* DualCardsTopBar */}
      <header className="flex items-center bg-black px-3 py-1">
        <button
          type="button"
          onClick={() => setMenuOpen(true)}
          className="flex h-10 w-10 items-center justify-center"
          aria-label="Open menu"
          style={{ color: GOLD_MID }}
        >
          <svg className="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
            <path strokeLinecap="round" d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>

        <img
          src="/gundu_ata_logo_gold.png"
          alt="Gundu Ata"
          className="mx-1 h-12 flex-1 object-contain object-left"
        />

        {loggedIn ? (
          <Link
            to="/deposit"
            className="flex items-center gap-1.5 rounded-full border bg-[#1A1A1A] py-1.5 pl-3 pr-1.5"
            style={{ borderColor: `${GOLD_DEEP}80` }}
          >
            <svg className="h-[18px] w-[18px]" viewBox="0 0 24 24" fill={GOLD_MID} aria-hidden>
              <path d="M21 7H3a1 1 0 00-1 1v9a2 2 0 002 2h16a2 2 0 002-2V8a1 1 0 00-1-1zm-1 6h-3a1.5 1.5 0 110-3h3v3zM3 5h18v1H3V5z" />
            </svg>
            <span className="text-[15px] font-bold text-white">₹{balance}</span>
            <span
              className="flex h-[30px] w-[30px] items-center justify-center rounded-full text-lg font-bold text-black"
              style={{ background: GOLD_BRUSH }}
            >
              +
            </span>
          </Link>
        ) : (
          <Link to="/login" className="px-2 text-sm font-bold" style={{ color: GOLD_MID }}>
            {t('login')}
          </Link>
        )}
      </header>

      <SearchBar placeholder={t('search_games')} onSearch={() => goGame()} />
      <PromoBannerCarousel banners={banners} />

      {/* Category circles — same order as APK DualCardsHomeScreen */}
      <div className="mt-[18px] flex gap-3.5 overflow-x-auto px-3 scrollbar-hide">
        <CategoryCircle label="GA" imageSrc="/gundu_ata_logo_gold.png" onClick={goGame} />
        <CategoryCircle
          label="CRICKET"
          onClick={() => goPath('/coming-soon')}
          icon={<SvgIcon d="M5.5 20.5l9-9M12 13l2.5 6.5M14 11l6.5 2.5M17.5 6.5a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z" />}
        />
        <CategoryCircle
          label="Soccer"
          onClick={() => goPath('/coming-soon')}
          icon={<SvgIcon d="M12 2a10 10 0 100 20 10 10 0 000-20zm1 2.07A8 8 0 0120 12h-3.1A5.01 5.01 0 0013 7.1V4.07zM11 4.07V7.1A5.01 5.01 0 007.1 12H4a8 8 0 017-7.93zM4 13h3.1A5.01 5.01 0 0011 16.9v3.03A8 8 0 014 13zm9 6.93V16.9A5.01 5.01 0 0016.9 13H20a8 8 0 01-7 6.93z" />}
        />
        <CategoryCircle
          label="Tennis"
          onClick={() => goPath('/coming-soon')}
          icon={<SvgIcon d="M12 2a10 10 0 100 20 10 10 0 000-20zm-1.5 2.2A8 8 0 014.2 13.5 10.1 10.1 0 0110.5 4.2zm3 0A10.1 10.1 0 0119.8 13.5 8 8 0 0013.5 4.2zM4.2 10.5A8 8 0 0110.5 19.8 10.1 10.1 0 014.2 10.5zm15.6 0A10.1 10.1 0 0113.5 19.8 8 8 0 0019.8 10.5z" />}
        />
        <CategoryCircle
          label="Rangu"
          onClick={() => goPath('/coming-soon')}
          icon={<SvgIcon d="M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a2 2 0 110 4 2 2 0 010-4zm-4 7a2 2 0 110 4 2 2 0 010-4zm8 0a2 2 0 110 4 2 2 0 010-4z" />}
        />
        <CategoryCircle
          label="Auto Roulette"
          onClick={() => goPath('/roulette')}
          icon={<SvgIcon d="M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a7 7 0 110 14 7 7 0 010-14zm0 3a4 4 0 100 8 4 4 0 000-8z" />}
        />
        <CategoryCircle
          label="Stock Market"
          onClick={() => goPath('/trading')}
          icon={<SvgIcon d="M3.5 18.5l5-6 4 3 6.5-9M14 6.5h5.5V12" />}
        />
        <CategoryCircle
          label="Chicken Road"
          onClick={() => goPath('/chicken-road')}
          icon={<SvgIcon d="M12 4c2 0 4 1.5 4 4 0 1-.3 1.8-.8 2.5L17 13h-2l-.5-1.5C13.8 12 13 12.2 12 12.2S10.2 12 9.5 11.5L9 13H7l1.8-2.5C8.3 9.8 8 9 8 8c0-2.5 2-4 4-4zm-1 9.5v5h2v-5" />}
        />
        <CategoryCircle
          label="Chicken Road 2"
          onClick={() => goPath('/chicken-road-2')}
          icon={<SvgIcon d="M12 3c-2.5 2-4 4.5-4 7a4 4 0 008 0c0-2.5-1.5-5-4-7zm0 11c-3 0-5.5 1.5-5.5 3.5V20h11v-2.5c0-2-2.5-3.5-5.5-3.5z" />}
        />
        <CategoryCircle
          label="Vortex"
          onClick={() => goPath('/vortex')}
          icon={<SvgIcon d="M12 2a10 10 0 100 20 10 10 0 000-20zm0 3a7 7 0 110 14V5z" />}
        />
        <CategoryCircle
          label="Chit Pat"
          onClick={() => goPath('/coming-soon')}
          icon={<SvgIcon d="M12 2C7.6 2 4 5.1 4 9c0 2.5 1.3 4.7 3.3 6.1L6 21l4.5-1.8c.5.1 1 .2 1.5.2 4.4 0 8-3.1 8-7s-3.6-7-8-7z" />}
        />
      </div>

      {/* Featured: left Gundu Ata | right Cock Fight + Roulette (APK Dual Cards) */}
      <div className="mt-[18px] flex h-[260px] gap-3 px-4">
        <button
          type="button"
          onClick={goGame}
          className="relative h-full flex-1 overflow-hidden rounded-2xl border bg-[#0D0D0D]"
          style={{ borderColor: `${GOLD_DEEP}73` }}
          aria-label="Play Gundu Ata"
        >
          <video
            src="/gundu_ata_video.mp4"
            className="h-full w-full object-cover"
            autoPlay
            muted
            loop
            playsInline
            aria-hidden
          />
        </button>

        <div className="flex h-full flex-1 flex-col gap-3">
          <button
            type="button"
            onClick={goCockFight}
            className="relative min-h-0 flex-1 overflow-hidden rounded-2xl border bg-[#0D0D0D]"
            style={{ borderColor: `${GOLD_DEEP}73` }}
            aria-label="Cock Fight"
          >
            <video
              src="/cock_fight.mp4"
              className="h-full w-full object-cover"
              autoPlay
              muted
              loop
              playsInline
              aria-hidden
            />
            <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 to-transparent px-2 py-2 text-center text-[13px] font-bold" style={{ color: GOLD_MID }}>
              COCK FIGHT
            </span>
          </button>
          <button
            type="button"
            onClick={() => goPath('/roulette')}
            className="relative min-h-0 flex-1 overflow-hidden rounded-2xl border bg-[#0D0D0D]"
            style={{ borderColor: `${GOLD_DEEP}73` }}
            aria-label="Roulette"
          >
            <img src="/card_roulette_home.jpg" alt="" className="h-full w-full object-cover" />
            <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 to-transparent px-2 py-2 text-center text-[13px] font-bold" style={{ color: GOLD_MID }}>
              ROULETTE
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}
