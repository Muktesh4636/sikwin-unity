import { useEffect, useState } from 'react';

const STORAGE_KEY = 'gundu_apk_prompt_dismissed_at';
/** Show again after 3 days if dismissed */
const RESHOW_MS = 3 * 24 * 60 * 60 * 1000;

const GOLD_MID = '#FFD54F';
const GOLD_DEEP = '#C9A227';
const GOLD_BRUSH = `linear-gradient(180deg, #FFE082, ${GOLD_MID}, ${GOLD_DEEP})`;

function shouldShowPrompt(): boolean {
  try {
    // Already inside the Android app WebView
    if ((window as any).AndroidBridge) return false;
  } catch (_) {}

  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const at = Number(raw);
      if (Number.isFinite(at) && Date.now() - at < RESHOW_MS) return false;
    }
  } catch (_) {}

  return true;
}

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

function dismissPrompt() {
  try {
    localStorage.setItem(STORAGE_KEY, String(Date.now()));
  } catch (_) {}
}

/** First-visit (and periodic) prompt to install the Android APK. */
export function ApkDownloadPrompt() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!shouldShowPrompt()) return;
    const t = window.setTimeout(() => setOpen(true), 600);
    return () => window.clearTimeout(t);
  }, []);

  if (!open) return null;

  const close = () => {
    dismissPrompt();
    setOpen(false);
  };

  const onDownload = () => {
    downloadApk();
    dismissPrompt();
    setOpen(false);
  };

  return (
    <div
      className="fixed inset-0 z-[10050] flex items-end justify-center bg-black/70 p-4 sm:items-center"
      role="dialog"
      aria-modal="true"
      aria-labelledby="apk-download-title"
      onClick={close}
    >
      <div
        className="w-full max-w-sm overflow-hidden rounded-2xl border bg-[#141414] shadow-2xl"
        style={{ borderColor: `${GOLD_DEEP}66` }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="relative px-5 pb-5 pt-6 text-center">
          <button
            type="button"
            onClick={close}
            className="absolute right-3 top-3 flex h-8 w-8 items-center justify-center rounded-full text-white/70 hover:bg-white/10"
            aria-label="Close"
          >
            ×
          </button>

          <img
            src="/gundu_ata_logo_gold.png"
            alt=""
            className="mx-auto h-14 w-auto object-contain"
          />

          <h2 id="apk-download-title" className="mt-4 text-xl font-extrabold text-white">
            Get the Gundu Ata App
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-white/70">
            Install our Android app for the best experience — faster play, smoother games, and easy access anytime.
          </p>

          <button
            type="button"
            onClick={onDownload}
            className="mt-5 w-full rounded-xl py-3.5 text-[15px] font-extrabold text-black"
            style={{ background: GOLD_BRUSH }}
          >
            Download APK
          </button>

          <button
            type="button"
            onClick={close}
            className="mt-3 w-full rounded-xl bg-[#1E1E1E] py-3 text-[14px] font-semibold text-white/85"
          >
            Continue on web
          </button>

          <p className="mt-3 text-[11px] text-white/45">
            Android only · Allow install from this source if asked
          </p>
        </div>
      </div>
    </div>
  );
}
