import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiSupportContacts, type SupportContacts } from '../api/endpoints';
import { STORAGE_KEYS } from '../config';

function loadCached(): SupportContacts | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.contactsCache);
    return raw ? (JSON.parse(raw) as SupportContacts) : null;
  } catch {
    return null;
  }
}

function HeadsetIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 18v-6a9 9 0 0 1 18 0v6" />
      <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z" />
    </svg>
  );
}

function WhatsAppIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z" />
    </svg>
  );
}

function TelegramIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path d="M11.944 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0a12 12 0 0 0-.056 0zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 0 1 .171.325c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91.177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024c-.106.024-1.793 1.14-5.061 3.345-.48.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014 3.332-1.386 4.025-1.627 4.476-1.635z" />
    </svg>
  );
}

function ChevronRightIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
    </svg>
  );
}

export function HelpCenterPage() {
  const nav = useNavigate();
  const [contacts, setContacts] = useState<SupportContacts | null>(() => loadCached());
  const [loading, setLoading] = useState(false);

  const refresh = async () => {
    try {
      setLoading(true);
      const resp = await apiSupportContacts();
      setContacts(resp.data);
      localStorage.setItem(STORAGE_KEYS.contactsCache, JSON.stringify(resp.data));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, []);

  const whatsappRaw = (contacts?.whatsapp_number ?? contacts?.whatsapp ?? '').trim();
  const telegram = contacts?.telegram ?? '';

  const whatsappDisplay = whatsappRaw
    ? whatsappRaw.startsWith('+')
      ? whatsappRaw
      : `+${whatsappRaw}`
    : '';

  const openWhatsApp = () => {
    const num = whatsappRaw.replace(/\D/g, '');
    if (num) window.open(`https://wa.me/${num}`, '_blank', 'noopener');
  };

  const openTelegram = () => {
    const handle = telegram.replace(/^@/, '').replace(/^\+/, '');
    if (handle) window.open(`https://t.me/${handle}`, '_blank', 'noopener');
  };

  return (
    <div className="mobile-frame min-h-dvh bg-[#121212]">
      <header className="sticky top-0 z-40 bg-[#121212]">
        <div className="mx-auto flex max-w-[460px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            className="flex h-11 w-11 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
            onClick={() => nav(-1)}
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-white">Help Center</h1>
          <div className="w-11" />
        </div>
      </header>

      <div className="mx-auto max-w-[460px] px-4 pb-8 pt-2">
        {loading && (
          <div className="mb-4 h-1 w-full overflow-hidden rounded-full bg-[#1E1E1E]">
            <div className="h-full w-1/2 animate-pulse bg-[#DAA520]" />
          </div>
        )}

        {/* Need Help? card - taller, SurfaceColor */}
        <div className="rounded-2xl bg-[#1E1E1E] px-4 py-8">
          <div className="flex flex-col items-center text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-full border-2 border-[#DAA520] bg-[#1E1E1E] text-[#DAA520]">
              <HeadsetIcon className="h-7 w-7" />
            </div>
            <h2 className="mt-4 text-lg font-bold text-white">Need Help?</h2>
            <p className="mt-1 text-sm text-[#BDBDBD]">
              Contact our support team via WhatsApp or Telegram
            </p>
          </div>
        </div>

        {/* WhatsApp Support card */}
        <button
          type="button"
          onClick={openWhatsApp}
          className="mt-4 flex w-full items-center gap-4 rounded-2xl bg-[#1B3F30] px-4 py-4 text-left transition-opacity active:opacity-90 disabled:opacity-60"
          disabled={!whatsappRaw}
        >
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[#255F43] text-white">
            <WhatsAppIcon className="h-6 w-6" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="font-bold text-white">WhatsApp Support</div>
            <div className="mt-0.5 text-sm font-medium text-[#4CAF50]">{whatsappDisplay || '—'}</div>
            <div className="mt-0.5 text-xs text-[#BDBDBD]">Get instant help from our support team</div>
          </div>
          <ChevronRightIcon className="h-6 w-6 shrink-0 text-[#4CAF50]" />
        </button>

        {/* Telegram Support card */}
        <button
          type="button"
          onClick={openTelegram}
          className="mt-4 flex w-full items-center gap-4 rounded-2xl bg-[#1A3143] px-4 py-4 text-left transition-opacity active:opacity-90 disabled:opacity-60"
          disabled={!telegram}
        >
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[#27567A] text-white">
            <TelegramIcon className="h-6 w-6" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="font-bold text-white">Telegram Support</div>
            <div className="mt-0.5 text-sm font-medium text-[#2196F3]">{telegram || '—'}</div>
            <div className="mt-0.5 text-xs text-[#BDBDBD]">Chat with us on Telegram</div>
          </div>
          <ChevronRightIcon className="h-6 w-6 shrink-0 text-[#2196F3]" />
        </button>

        {/* Support Hours card */}
        <div className="mt-4 flex w-full items-center gap-4 rounded-2xl bg-[#1E1E1E] px-4 py-4">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[#DAA520] text-white">
            <span className="text-xl font-bold">!</span>
          </div>
          <div className="min-w-0 flex-1">
            <div className="font-bold text-white">Support Hours</div>
            <div className="mt-0.5 text-xs text-[#BDBDBD]">
              Our support team is available 24/7 to assist
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
