import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLoginSignupModal } from '../context/LoginSignupModalContext';
import { BackArrow } from '../components/BackArrow';
import { InternetIssueBar } from '../components/InternetIssueBar';
import { apiWallet } from '../api/endpoints';
import { prefetchCasinoPage } from '../utils/prefetchGameAssets';

const TRADING_URL = 'https://gunduata.tech/trading/';
const TRADING_API = 'https://gunduata.tech/api/trading';
const TRADING_BG = '#0b1220';

const PREFETCH_URLS = [
  TRADING_URL,
  `${TRADING_URL}styles.css`,
  `${TRADING_URL}game.js?v=27`,
];

/**
 * Stock Market — real Gundu wallet only (same JWT pattern as Auto Roulette).
 */
export function TradingPage() {
  const auth = useAuth();
  const nav = useNavigate();
  const { showLoginSignupModal } = useLoginSignupModal();
  const token = auth.accessToken;

  const [progress, setProgress] = useState(0.02);
  const [status, setStatus] = useState('Preparing Stock Market…');
  const [prefetchDone, setPrefetchDone] = useState(false);
  const [iframeReady, setIframeReady] = useState(false);
  const [showGame, setShowGame] = useState(false);
  const [networkError, setNetworkError] = useState(false);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    if (!token) return;
    try {
      localStorage.setItem('gundu_access_token', token);
      localStorage.setItem('trading_api', TRADING_API);
    } catch {
      /* ignore */
    }
  }, [token]);

  useEffect(() => {
    prefetchCasinoPage();
  }, []);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    (async () => {
      setNetworkError(false);
      setPrefetchDone(false);
      setIframeReady(false);
      setShowGame(false);
      setProgress(0.02);
      setStatus('Fetching market…');

      if (typeof navigator !== 'undefined' && navigator.onLine === false) {
        if (!cancelled) {
          setNetworkError(true);
          setStatus('Internet issue');
        }
        return;
      }

      try {
        const probe = await fetch(TRADING_URL, { method: 'GET', cache: 'no-store' });
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
          /* soft */
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
        /* soft */
      }
      if (cancelled) return;
      setProgress(0.85);
      setStatus('Opening chart…');
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
    () => (token ? `${TRADING_URL}?token=${encodeURIComponent(token)}&r=${retryToken}` : ''),
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
          <h1 className="flex-1 text-center text-lg font-bold text-primaryYellow">Stock Market</h1>
          <div className="w-10" />
        </header>
        <div className="flex flex-1 flex-col items-center justify-center px-6 text-center">
          <p className="text-xl font-bold text-textWhite">Login required</p>
          <p className="mt-2 text-sm text-textGrey">
            Stock Market uses your real Gundu wallet. Please sign in to continue.
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
    <div className="mobile-frame relative flex min-h-dvh flex-col" style={{ background: TRADING_BG }}>
      <header className="flex items-center gap-3 px-4 py-3" style={{ background: TRADING_BG }}>
        <button type="button" onClick={() => nav('/')} className="text-primaryYellow" aria-label="Back">
          <BackArrow />
        </button>
        <h1 className="flex-1 text-center text-lg font-bold text-primaryYellow">Stock Market</h1>
        <div className="w-10" />
      </header>

      <div className="relative flex-1 pt-3" style={{ background: TRADING_BG }}>
        <iframe
          key={retryToken}
          title="Stock Market"
          src={networkError ? undefined : src}
          className={`h-full w-full border-0 transition-opacity duration-300 ${showGame && !networkError ? 'opacity-100' : 'opacity-0'}`}
          style={{ background: TRADING_BG }}
          allow="autoplay; fullscreen"
          onLoad={() => {
            if (networkError) return;
            setIframeReady(true);
            setProgress((p) => Math.max(p, 0.92));
          }}
        />

        {(!showGame || networkError) && (
          <div
            className="absolute inset-0 z-10 flex flex-col items-center justify-center px-6 pt-6"
            style={{ background: '#000' }}
          >
            <img
              src="/stock-market-loading.png"
              alt="Loading Stock Market"
              className="w-full max-w-sm object-cover object-top rounded-xl"
              style={{ maxHeight: '52vh' }}
            />
            <p className="mt-3 text-sm text-textGrey">{networkError ? 'Internet issue' : status}</p>
            {!networkError && (
              <>
                <div className="mt-5 h-[18px] w-full max-w-sm overflow-hidden rounded-full border-[1.5px] border-primaryYellow/85 bg-[#1a1a1a]">
                  <div
                    className="h-full rounded-full transition-[width] duration-200"
                    style={{
                      width: `${Math.max(2, pct)}%`,
                      background: 'linear-gradient(90deg, #1B5E20, #43A047, #76FF03)',
                    }}
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
