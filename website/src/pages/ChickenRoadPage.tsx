import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLoginSignupModal } from '../context/LoginSignupModalContext';
import { BackArrow } from '../components/BackArrow';
import { InternetIssueBar } from '../components/InternetIssueBar';
import { apiWallet } from '../api/endpoints';

const BG = '#0a0a0a';

type ChickenRoadVariant = 1 | 2;

const CONFIG: Record<
  ChickenRoadVariant,
  { title: string; url: string; path: string }
> = {
  1: {
    title: 'Chicken Road',
    url: 'https://gunduata.tech/chicken-road/',
    path: '/chicken-road',
  },
  2: {
    title: 'Chicken Road 2',
    url: 'https://gunduata.tech/chicken-road-2/',
    path: '/chicken-road-2',
  },
};

/**
 * Chicken Road / Chicken Road 2 — real Gundu wallet (same JWT pattern as Roulette / Trading).
 */
export function ChickenRoadPage({ variant }: { variant: ChickenRoadVariant }) {
  const auth = useAuth();
  const nav = useNavigate();
  const { showLoginSignupModal } = useLoginSignupModal();
  const token = auth.accessToken;
  const cfg = CONFIG[variant];

  const [progress, setProgress] = useState(0.02);
  const [status, setStatus] = useState(`Preparing ${cfg.title}…`);
  const [prefetchDone, setPrefetchDone] = useState(false);
  const [iframeReady, setIframeReady] = useState(false);
  const [showGame, setShowGame] = useState(false);
  const [networkError, setNetworkError] = useState(false);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    if (!token) return;
    try {
      localStorage.setItem('gundu_access_token', token);
    } catch {
      /* ignore */
    }
  }, [token]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    (async () => {
      setNetworkError(false);
      setPrefetchDone(false);
      setIframeReady(false);
      setShowGame(false);
      setProgress(0.02);
      setStatus('Fetching game…');

      if (typeof navigator !== 'undefined' && navigator.onLine === false) {
        if (!cancelled) {
          setNetworkError(true);
          setStatus('Internet issue');
        }
        return;
      }

      try {
        const probe = await fetch(cfg.url, { method: 'GET', cache: 'no-store' });
        if (!probe.ok) throw new Error('offline');
      } catch {
        if (!cancelled) {
          setNetworkError(true);
          setStatus('Internet issue');
        }
        return;
      }

      if (cancelled) return;
      setProgress(0.55);
      setStatus('Syncing wallet…');
      try {
        await apiWallet();
      } catch {
        /* soft */
      }
      if (cancelled) return;
      setProgress(0.85);
      setStatus(`Opening ${cfg.title}…`);
      setPrefetchDone(true);
    })();
    return () => {
      cancelled = true;
    };
  }, [token, retryToken, cfg.url, cfg.title]);

  useEffect(() => {
    if (!prefetchDone || networkError || showGame) return;
    if (iframeReady) {
      setProgress(1);
      setStatus('Ready');
      const t = window.setTimeout(() => setShowGame(true), 220);
      return () => window.clearTimeout(t);
    }
    const timeout = window.setTimeout(() => {
      setNetworkError(true);
      setStatus('Internet issue');
    }, 25000);
    return () => window.clearTimeout(timeout);
  }, [prefetchDone, iframeReady, showGame, networkError]);

  const src = useMemo(
    () => (token ? `${cfg.url}?token=${encodeURIComponent(token)}&r=${retryToken}` : ''),
    [token, retryToken, cfg.url]
  );

  if (!auth.ready) {
    return (
      <div className="mobile-frame flex min-h-dvh items-center justify-center bg-appBg text-textGrey">
        Loading…
      </div>
    );
  }

  if (!token || !auth.user) {
    return (
      <div className="mobile-frame flex min-h-dvh flex-col bg-appBg pb-24">
        <header className="flex items-center gap-3 px-4 py-3">
          <button type="button" onClick={() => nav(-1)} className="text-primaryYellow" aria-label="Back">
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-lg font-bold text-primaryYellow">{cfg.title}</h1>
          <div className="w-10" />
        </header>
        <div className="flex flex-1 flex-col items-center justify-center px-6 text-center">
          <p className="text-xl font-bold text-textWhite">Login required</p>
          <p className="mt-2 text-sm text-textGrey">
            {cfg.title} uses your real Gundu wallet. Please sign in to continue.
          </p>
          <button
            type="button"
            onClick={() => showLoginSignupModal()}
            className="mt-6 rounded-xl bg-primaryYellow px-6 py-3 font-bold text-black"
          >
            Login
          </button>
          <Link to="/" className="mt-4 text-sm text-primaryYellow">
            Back to Home
          </Link>
        </div>
      </div>
    );
  }

  const pct = Math.round(Math.min(100, progress * 100));

  return (
    <div className="mobile-frame relative flex min-h-dvh flex-col" style={{ background: BG }}>
      <header className="flex items-center gap-3 px-4 py-3" style={{ background: BG }}>
        <button type="button" onClick={() => nav('/')} className="text-primaryYellow" aria-label="Back">
          <BackArrow />
        </button>
        <h1 className="flex-1 text-center text-lg font-bold text-primaryYellow">{cfg.title}</h1>
        <div className="w-10" />
      </header>

      <div className="relative flex-1 pt-3" style={{ background: BG }}>
        <iframe
          key={retryToken}
          title={cfg.title}
          src={networkError ? undefined : src}
          className={`h-full w-full border-0 transition-opacity duration-300 ${showGame && !networkError ? 'opacity-100' : 'opacity-0'}`}
          style={{ background: BG }}
          allow="autoplay; fullscreen"
          onLoad={() => {
            if (networkError) return;
            setIframeReady(true);
            setProgress((p) => Math.max(p, 0.92));
          }}
        />

        {(!showGame || networkError) && (
          <div
            className="absolute inset-0 z-10 flex flex-col items-center justify-center px-8 pt-6"
            style={{ background: BG }}
          >
            <p className="text-xl font-black tracking-[0.12em] text-primaryYellow">
              {cfg.title.toUpperCase()}
            </p>
            <p className="mt-2 text-sm text-textGrey">{networkError ? 'Internet issue' : status}</p>
            {!networkError && (
              <>
                <div className="mt-10 h-[18px] w-full max-w-sm overflow-hidden rounded-full border-[1.5px] border-primaryYellow/85 bg-[#1a1a1a]">
                  <div
                    className="h-full rounded-full bg-primaryYellow transition-[width] duration-200"
                    style={{ width: `${Math.max(2, pct)}%` }}
                  />
                </div>
                <p className="mt-3 text-xl font-black text-primaryYellow">{pct}%</p>
              </>
            )}
            {networkError && <InternetIssueBar onRetry={() => setRetryToken((n) => n + 1)} />}
          </div>
        )}
      </div>
    </div>
  );
}
