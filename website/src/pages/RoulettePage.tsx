import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLoginSignupModal } from '../context/LoginSignupModalContext';
import { BackArrow } from '../components/BackArrow';
import { InternetIssueBar } from '../components/InternetIssueBar';
import { apiWallet } from '../api/endpoints';

const ROULETTE_URL = 'https://gunduata.tech/roulette/';
const ROULETTE_API = 'https://gunduata.tech/roulette/api';
/** Same as in-game wheel Three.js background */
const ROULETTE_BG = '#0c0406';

const PREFETCH_URLS = [
  ROULETTE_URL,
  `${ROULETTE_URL}styles.css?v=157`,
  `${ROULETTE_URL}app.js?v=157`,
  'https://unpkg.com/three@0.160.0/build/three.module.js',
  'https://unpkg.com/three@0.160.0/examples/jsm/controls/OrbitControls.js',
];

/**
 * Roulette — real Gundu wallet only.
 * Shows ball + progress while prefetching assets/wallet, then reveals the game.
 */
export function RoulettePage() {
  const auth = useAuth();
  const nav = useNavigate();
  const { showLoginSignupModal } = useLoginSignupModal();
  const token = auth.accessToken;

  const [progress, setProgress] = useState(0.02);
  const [status, setStatus] = useState('Preparing Auto Roulette…');
  const [prefetchDone, setPrefetchDone] = useState(false);
  const [iframeReady, setIframeReady] = useState(false);
  const [showGame, setShowGame] = useState(false);
  const [networkError, setNetworkError] = useState(false);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    if (!token) return;
    try {
      localStorage.setItem('gundu_access_token', token);
      localStorage.setItem('roultee_api', ROULETTE_API);
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
        const probe = await fetch(ROULETTE_URL, { method: 'GET', cache: 'no-store' });
        if (!probe.ok) throw new Error('offline');
      } catch {
        if (!cancelled) {
          setNetworkError(true);
          setStatus('Internet issue');
        }
        return;
      }

      for (let i = 0; i < PREFETCH_URLS.length; i++) {
        if (cancelled) return;
        try {
          await fetch(PREFETCH_URLS[i], { mode: 'cors', cache: 'force-cache' }).catch(() =>
            fetch(PREFETCH_URLS[i], { mode: 'no-cors', cache: 'force-cache' })
          );
        } catch {
          /* soft fail */
        }
        if (!cancelled) {
          setProgress(0.1 + (0.55 * (i + 1)) / PREFETCH_URLS.length);
          setStatus(`Prefetching ${i + 1}/${PREFETCH_URLS.length}…`);
        }
      }
      if (cancelled) return;
      setStatus('Syncing wallet…');
      try {
        await apiWallet();
      } catch {
        /* soft fail */
      }
      if (cancelled) return;
      setProgress(0.85);
      setStatus('Opening table…');
      setPrefetchDone(true);
    })();

    return () => {
      cancelled = true;
    };
  }, [token, retryToken]);

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
    () => (token ? `${ROULETTE_URL}?token=${encodeURIComponent(token)}&r=${retryToken}` : ''),
    [token, retryToken]
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
          <h1 className="flex-1 text-center text-lg font-bold text-primaryYellow">Auto Roulette</h1>
          <div className="w-10" />
        </header>
        <div className="flex flex-1 flex-col items-center justify-center px-6 text-center">
          <p className="text-xl font-bold text-textWhite">Login required</p>
          <p className="mt-2 text-sm text-textGrey">
            Auto Roulette uses your real Gundu wallet. Please sign in to continue.
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
    <div className="mobile-frame relative flex min-h-dvh flex-col" style={{ background: ROULETTE_BG }}>
      <header className="flex items-center gap-3 px-4 py-3" style={{ background: ROULETTE_BG }}>
        <button type="button" onClick={() => nav('/')} className="text-primaryYellow" aria-label="Back">
          <BackArrow />
        </button>
        <h1 className="flex-1 text-center text-lg font-bold text-primaryYellow">Auto Roulette</h1>
        <div className="w-10" />
      </header>

      <div className="relative flex-1 pt-3" style={{ background: ROULETTE_BG }}>
        <iframe
          key={retryToken}
          title="Auto Roulette"
          src={networkError ? undefined : src}
          className={`h-full w-full border-0 transition-opacity duration-300 ${showGame && !networkError ? 'opacity-100' : 'opacity-0'}`}
          style={{ background: ROULETTE_BG }}
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
            style={{ background: ROULETTE_BG }}
          >
            <p className="text-xl font-black tracking-[0.12em] text-primaryYellow">AUTO ROULETTE</p>
            <p className="mt-2 text-sm text-textGrey">{networkError ? 'Internet issue' : status}</p>

            <div className="roulette-wheel-load relative mt-10 h-[220px] w-[220px]">
              <div className="roulette-wheel-rim absolute inset-0 rounded-full" />
              <div className="roulette-wheel-felt absolute inset-[14%] rounded-full" />
              <div className="absolute inset-0 flex items-center justify-center">
                <span className="text-3xl font-black text-primaryYellow">
                  {networkError ? '!' : `${pct}%`}
                </span>
              </div>
              {!networkError && (
                <div
                  className="roulette-load-ball absolute h-7 w-7 rounded-full"
                  style={{
                    left: '50%',
                    top: '50%',
                    transform: `rotate(${pct * 3.2}deg) translateY(-96px) rotate(${pct * -3.2}deg)`,
                    marginLeft: '-14px',
                    marginTop: '-14px',
                  }}
                />
              )}
            </div>
            <p className="mt-6 text-sm text-textWhite/70">{networkError ? 'Connection failed' : 'Ball in play…'}</p>
            {networkError && <InternetIssueBar onRetry={() => setRetryToken((n) => n + 1)} />}
          </div>
        )}
      </div>

      <style>{`
        .roulette-wheel-rim {
          background: conic-gradient(
            from 0deg,
            #ffe082,
            #c9a227,
            #ffcc00,
            #8d6e00,
            #e53935,
            #212121,
            #e53935,
            #212121,
            #ffe082
          );
          box-shadow: 0 0 24px rgba(255, 204, 0, 0.25);
        }
        .roulette-wheel-felt {
          background: radial-gradient(circle at 40% 35%, #1a0a0e 0%, #0c0406 70%);
          border: 2px solid rgba(255, 204, 0, 0.7);
        }
        .roulette-load-ball {
          background: radial-gradient(circle at 35% 30%, #fff 0%, #e8e8e8 45%, #9e9e9e 100%);
          box-shadow: 0 2px 8px rgba(0,0,0,0.5);
          animation: roulette-ball-spin 0.7s linear infinite;
          z-index: 2;
          transition: transform 0.2s linear;
        }
        .roulette-load-ball::after {
          content: '';
          position: absolute;
          left: 50%;
          top: 50%;
          width: 18%;
          height: 18%;
          transform: translate(-50%, -50%);
          border-radius: 50%;
          background: #1a1a1a;
        }
        @keyframes roulette-ball-spin {
          to { filter: brightness(1.05); }
        }
      `}</style>
    </div>
  );
}
