import { useEffect, useMemo, useState } from 'react';
import { TopBar } from '../components/TopBar';
import { apiLeaderboard } from '../api/endpoints';

type LeaderItem = {
  rank: number;
  username: string;
  turnover: number;
  prize?: string | null;
};

export function LeaderboardPage() {
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<LeaderItem[]>([]);
  const [userRank, setUserRank] = useState<number>(0);
  const [userTurnover, setUserTurnover] = useState<number>(0);
  const [prizes, setPrizes] = useState<Record<string, string>>({ '1st': '₹1,000', '2nd': '₹500', '3rd': '₹100' });

  const load = async () => {
    try {
      setLoading(true);
      const resp = await apiLeaderboard();
      const data = resp.data ?? {};
      const leaderboard = (data.leaderboard as any[]) ?? [];
      const userStats = (data.user_stats as any) ?? {};
      const apiPrizes = (data.prizes as any) ?? null;
      if (apiPrizes && typeof apiPrizes === 'object') setPrizes(apiPrizes);

      setUserRank(Number(userStats.rank ?? 0) || 0);
      setUserTurnover(Number(userStats.turnover ?? 0) || 0);

      const normalized: LeaderItem[] = leaderboard.map((row, idx) => {
        const rankFromApi = Number(row?.rank ?? 0) || 0;
        const rank = rankFromApi > 0 ? rankFromApi : idx + 1;
        const username = String(row?.username ?? 'Unknown');
        const turnover = Number(row?.turnover ?? 0) || 0;
        const prize =
          row?.prize ??
          (rank === 1 ? prizes['1st'] : rank === 2 ? prizes['2nd'] : rank === 3 ? prizes['3rd'] : null);
        return { rank, username, turnover, prize };
      });

      // If prizes were updated after mapping, recompute top-3 prize
      setItems(
        normalized.map((it) => ({
          ...it,
          prize:
            it.prize ??
            (it.rank === 1 ? apiPrizes?.['1st'] : it.rank === 2 ? apiPrizes?.['2nd'] : it.rank === 3 ? apiPrizes?.['3rd'] : null),
        }))
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const showUser = userTurnover > 50;
  const prizeLine = useMemo(() => `1st: ${prizes['1st']} | 2nd: ${prizes['2nd']} | 3rd: ${prizes['3rd']}`, [prizes]);

  return (
    <div className="mobile-frame app-shell">
      <TopBar title="Leaderboard" back />
      <div className="px-4 pt-4">
        {showUser ? (
          <div className="card p-4">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs font-black text-textGrey tracking-widest">YOUR RANKING</div>
                <div className="mt-1 text-3xl font-black text-primaryYellow">{userRank > 0 ? `#${userRank}` : 'Unranked'}</div>
              </div>
              <div className="text-right">
                <div className="text-xs font-black text-textGrey tracking-widest">YOUR DAILY TURNOVER</div>
                <div className="mt-1 text-lg font-extrabold">₹{userTurnover.toFixed(2)}</div>
              </div>
            </div>
          </div>
        ) : null}

        <div className="mt-4 card p-4 text-center">
          <div className="text-primaryYellow text-sm font-black tracking-widest">DAILY CHAMPIONS</div>
          <div className="mt-2 text-base font-extrabold">{prizeLine}</div>
          <div className="mt-2 text-xs text-textGrey">Results will be announced daily 11:00 PM night</div>
          <button className="btn-ghost mt-3 w-full" onClick={load} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>

        {loading ? (
          <div className="mt-6 grid place-items-center text-textGrey">Loading…</div>
        ) : items.length === 0 ? (
          <div className="mt-6 grid place-items-center text-textGrey">No data available</div>
        ) : (
          <div className="mt-4 space-y-3 pb-6">
            {items.map((it) => (
              <div key={`${it.rank}-${it.username}`} className="card p-4">
                <div className="flex items-center gap-3">
                  <div
                    className={[
                      'grid h-9 w-9 place-items-center rounded-full text-sm font-black',
                      it.rank === 1
                        ? 'bg-primaryYellow text-black'
                        : it.rank === 2
                          ? 'bg-white/20 text-textWhite'
                          : it.rank === 3
                            ? 'bg-orange-400/20 text-textWhite'
                            : 'bg-transparent text-textGrey border border-border',
                    ].join(' ')}
                  >
                    {it.rank}
                  </div>
                  <div className="flex-1">
                    <div className="text-lg font-extrabold">{it.username}</div>
                    <div className="text-sm text-textGrey">Daily turnover: ₹{it.turnover.toFixed(2)}</div>
                  </div>
                  {it.prize ? (
                    <div className="text-right">
                      <div className="text-[10px] font-black text-textGrey">PRIZE</div>
                      <div className="text-base font-black text-primaryYellow">{it.prize}</div>
                    </div>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

