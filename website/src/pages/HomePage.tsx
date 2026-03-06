import { useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { apiWallet, type Wallet } from '../api/endpoints';

function SearchBar() {
  const [query, setQuery] = useState('');
  return (
    <div className="relative mx-4 mt-2">
      <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-textGrey">
        <SearchIcon />
      </span>
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search games..."
        className="w-full rounded-xl border-0 bg-surface py-3 pl-11 pr-4 text-textWhite placeholder:text-textGrey focus:ring-2 focus:ring-primaryYellow"
      />
    </div>
  );
}

function SearchIcon() {
  return (
    <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
    </svg>
  );
}

// Same 5 banners as Android app: DAILY REWARD, REFER & EARN, MEGA SPIN, USDT SPECIAL, FRANCHISE
const BANNERS = [
  {
    title: 'DAILY REWARD',
    subtitle: 'SPIN THE WHEEL FOR BONUS!',
    buttonText: 'SPIN NOW',
    gradient: 'from-[#F9A825] to-[#F57F17]',
    route: '/gundu-ata',
  },
  {
    title: 'REFER & EARN',
    subtitle: 'Earn up to ₹1 Lakh!',
    buttonText: 'INVITE',
    gradient: 'from-[#455A64] to-[#263238]',
    route: '/refer',
  },
  {
    title: 'MEGA SPIN',
    subtitle: 'Deposit ₹2000 or more to spin the wheel!',
    buttonText: 'SPIN NOW',
    gradient: 'from-[#4A148C] to-[#880E4F]',
    route: '/gundu-ata',
  },
  {
    title: 'USDT SPECIAL ₮',
    subtitle: 'Get 5% EXTRA CASHBACK on all USDT deposits!',
    buttonText: 'DEPOSIT NOW',
    gradient: 'from-[#00897B] to-[#004D40]',
    route: '/deposit?method=USDT',
  },
  {
    title: 'FRANCHISE',
    subtitle: 'Get Gundu Ata franchise at 50% off — Get in touch today!',
    buttonText: 'LEARN MORE',
    gradient: 'from-[#795548] to-[#5D4037]',
    route: '/partner',
  },
];

function PromotionalBanners() {
  const nav = useNavigate();
  const [page, setPage] = useState(0);
  const [lastClickTime, setLastClickTime] = useState(0);
  const CLICK_COOLDOWN = 1000;

  useEffect(() => {
    const id = setInterval(() => {
      setPage((p) => (p + 1) % BANNERS.length);
    }, 4000);
    return () => clearInterval(id);
  }, []);

  const handleBannerClick = (route: string) => {
    const now = Date.now();
    if (now - lastClickTime > CLICK_COOLDOWN) {
      setLastClickTime(now);
      nav(route);
    }
  };

  const banner = BANNERS[page];

  return (
    <div className="mx-4 mt-4 overflow-hidden rounded-2xl text-center">
      <div
        className={`min-h-[190px] rounded-2xl bg-gradient-to-r ${banner.gradient} px-5 py-6 flex flex-col items-center justify-center`}
      >
        <div className="text-2xl font-extrabold uppercase tracking-wide text-primaryYellow">
          {banner.title}
        </div>
        <div className="mt-1.5 text-sm font-bold text-white">{banner.subtitle}</div>
        <button
          type="button"
          onClick={() => handleBannerClick(banner.route)}
          className="mt-3 rounded-xl bg-primaryYellow px-6 py-2.5 text-base font-black uppercase text-black"
        >
          {banner.buttonText}
        </button>
      </div>
      <div className="mt-2.5 flex justify-center gap-1.5">
        {BANNERS.map((_, i) => (
          <span
            key={i}
            className={`h-2 w-2 rounded-full ${page === i ? 'bg-primaryYellow' : 'bg-white/50'}`}
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

function HotGamesSection() {
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

  return (
    <div className="relative mt-6 px-4">
      <h2 className="text-lg font-bold text-textWhite">Hot games</h2>
      <div className="relative mt-3 flex justify-center">
        <div className="mx-auto w-full max-w-[200px] overflow-hidden rounded-2xl shadow-lg">
          <div className="aspect-[3/4] w-full overflow-hidden bg-[#1565C0]">
            <video
              src="/gundu_ata_video.mp4"
              className="h-full w-full object-cover object-center"
              autoPlay
              muted
              loop
              playsInline
              aria-label="Gundu Ata game"
            />
          </div>
        </div>
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
          <img
            src="/treasure_chest.png"
            alt=""
            className="h-auto w-full max-w-[64px] object-contain"
          />
        </div>
      </div>
    </div>
  );
}

export function HomePage() {
  const nav = useNavigate();
  const [wallet, setWallet] = useState<Wallet | null>(null);

  useEffect(() => {
    let alive = true;
    apiWallet()
      .then((r) => { if (alive) setWallet(r.data); })
      .catch(() => {});
    return () => { alive = false; };
  }, []);

  const balance = wallet?.balance ?? '0.00';

  return (
    <div className="mobile-frame min-h-dvh bg-appBg pb-4">
      {/* Header: profile (left) + GUNDU ATA (left) | balance pill + yellow + (right) */}
      <header className="flex items-center justify-between bg-appBg px-3 py-3">
        <div className="flex min-w-0 flex-1 items-center gap-2">
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
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <div className="flex items-center gap-2 rounded-full bg-surface px-3 py-1.5">
            <span className="font-bold text-primaryYellow">₹</span>
            <span className="font-bold text-textWhite">{balance}</span>
          </div>
          <button
            type="button"
            onClick={() => nav('/wallet')}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primaryYellow text-black"
            aria-label="Add money"
          >
            <span className="text-xl font-bold leading-none">+</span>
          </button>
        </div>
      </header>

      <SearchBar />
      <PromotionalBanners />
      <HotGamesSection />
    </div>
  );
}
