import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiRecentDiceResults, type RecentRoundResult } from '../api/endpoints';
import { useTranslations } from '../context/LocaleContext';

const RECENT_ROUNDS_COUNT = 20;

function formatTimestamp(ts: string | null | undefined): string {
  if (!ts) return '—';
  try {
    const d = new Date(ts);
    if (Number.isNaN(d.getTime())) return ts;
    const day = d.getDate();
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const month = months[d.getMonth()];
    const year = d.getFullYear();
    const h = d.getHours();
    const m = d.getMinutes();
    return `${day} ${month} ${year}, ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  } catch {
    return ts;
  }
}

function RefreshIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M23 4v6h-6" />
      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
    </svg>
  );
}

const DICE_KEYS = ['dice_1', 'dice_2', 'dice_3', 'dice_4', 'dice_5', 'dice_6'] as const;
const DICE_KEYS_CAMEL = ['dice1', 'dice2', 'dice3', 'dice4', 'dice5', 'dice6'] as const;

function getDiceValues(it: Record<string, unknown>): (number | null)[] {
  const fromArray = it.dice ?? it.dice_rolls ?? it.dice_values;
  if (Array.isArray(fromArray) && fromArray.length >= 6) {
    return fromArray.slice(0, 6).map((v) => {
      const n = typeof v === 'number' ? v : Number(v);
      return Number.isNaN(n) ? null : n;
    });
  }
  return DICE_KEYS.map((key, i) => {
    const v = it[key] ?? it[DICE_KEYS_CAMEL[i]];
    if (v == null) return null;
    const n = typeof v === 'number' ? v : Number(v);
    return Number.isNaN(n) ? null : n;
  });
}

export function DiceResultsPage() {
  const nav = useNavigate();
  const t = useTranslations();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<RecentRoundResult[]>([]);

  const load = async () => {
    try {
      setLoading(true);
      const resp = await apiRecentDiceResults(RECENT_ROUNDS_COUNT);
      const raw = resp.data;
      const list = Array.isArray(raw)
        ? raw
        : (raw && typeof raw === 'object' && 'results' in raw && Array.isArray((raw as { results: RecentRoundResult[] }).results))
          ? (raw as { results: RecentRoundResult[] }).results
          : [];
      setItems(list.slice(0, RECENT_ROUNDS_COUNT));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div className="mobile-frame min-h-dvh bg-[#121212]">
      <header className="sticky top-0 z-40 bg-[#121212]">
        <div className="mx-auto flex max-w-[460px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-white">{t('recent_dice_results')}</h1>
          <button
            type="button"
            onClick={load}
            disabled={loading}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-white transition-opacity hover:opacity-90 disabled:opacity-50"
            aria-label="Refresh"
          >
            <RefreshIcon className="h-6 w-6" />
          </button>
        </div>
      </header>

      <div className="mx-auto max-w-[460px] px-4 pb-24 pt-2">
        <p className="mb-3 text-center text-sm text-[#BDBDBD]">{t('last_round_dice_results')}</p>
        {loading ? (
          <div className="flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#FFCC00] border-t-transparent" />
          </div>
        ) : items.length === 0 ? (
          <div className="py-12 text-center text-[#BDBDBD]">{t('no_results_found')}</div>
        ) : (
          <div className="space-y-4">
            {items.map((it, index) => {
              const record = it as Record<string, unknown>;
              const diceValues = getDiceValues(record);
              const result = record.dice_result ?? it.dice_result ?? '—';
              return (
                <div
                  key={`round-${index}-${it.round_id}`}
                  className="rounded-2xl bg-[#1E1E1E] px-4 py-4"
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-white">Round: {it.round_id}</span>
                    <span className="text-[#BDBDBD] shrink-0">{formatTimestamp(it.timestamp)}</span>
                  </div>
                  <div className="mt-3 flex min-h-[52px] flex-wrap items-center gap-x-4 gap-y-2">
                    <div className="flex shrink-0 gap-1.5">
                      {diceValues.map((num, idx) => (
                        <div
                          key={idx}
                          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#3a3a3a] bg-[#2a2a2a] text-base font-bold text-white"
                        >
                          {num != null && num >= 1 && num <= 6 ? num : '?'}
                        </div>
                      ))}
                    </div>
                    <div className="shrink-0 text-right">
                      <div className="text-sm font-medium text-white">{t('result')}</div>
                      <div className="text-lg font-bold text-[#FFCC00]">
                        {String(result)}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
