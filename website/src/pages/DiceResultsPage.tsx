import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiRecentDiceResults, type RecentRoundResult } from '../api/endpoints';

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

const DICE_VALUES = ['dice_1', 'dice_2', 'dice_3', 'dice_4', 'dice_5', 'dice_6'] as const;

export function DiceResultsPage() {
  const nav = useNavigate();
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<RecentRoundResult[]>([]);

  const load = async () => {
    try {
      setLoading(true);
      const resp = await apiRecentDiceResults(20);
      setItems(resp.data ?? []);
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
        <div className="mx-auto flex max-w-[430px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-white">Recent Dice Results</h1>
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

      <div className="mx-auto max-w-[430px] px-4 pb-10 pt-2">
        {loading ? (
          <div className="flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#FFCC00] border-t-transparent" />
          </div>
        ) : items.length === 0 ? (
          <div className="py-12 text-center text-[#BDBDBD]">No results found</div>
        ) : (
          <div className="space-y-4">
            {items.map((it) => (
              <div
                key={it.round_id}
                className="rounded-2xl bg-[#1E1E1E] px-4 py-4"
              >
                <div className="flex items-center justify-between text-xs">
                  <span className="text-white">Round: {it.round_id}</span>
                  <span className="text-[#BDBDBD]">{formatTimestamp(it.timestamp)}</span>
                </div>
                <div className="mt-3 flex items-center justify-between gap-4">
                  <div className="flex gap-1.5">
                    {DICE_VALUES.map((key, idx) => {
                      const v = (it as Record<string, unknown>)[key];
                      const num = v != null ? Number(v) : null;
                      return (
                        <div
                          key={idx}
                          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#3a3a3a] bg-[#2a2a2a] text-base font-bold text-white"
                        >
                          {num >= 1 && num <= 6 ? num : '?'}
                        </div>
                      );
                    })}
                  </div>
                  <div className="text-right">
                    <div className="text-sm font-medium text-white">Result</div>
                    <div className="text-lg font-bold text-[#FFCC00]">
                      {it.dice_result ?? '—'}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
