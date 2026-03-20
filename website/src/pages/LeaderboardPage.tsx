import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiLeaderboard } from '../api/endpoints';

const BG = '#121212';
const SURFACE = '#1E1E1E';
const CARD_BORDER = 'rgba(255, 204, 0, 0.5)';

type LeaderItem = {
  rank: number;
  username: string;
  turnover: number;
  prize?: string | null;
};

export function LeaderboardPage() {
  const nav = useNavigate();
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
          (rank === 1 ? (apiPrizes?.['1st'] ?? prizes['1st']) : rank === 2 ? (apiPrizes?.['2nd'] ?? prizes['2nd']) : rank === 3 ? (apiPrizes?.['3rd'] ?? prizes['3rd']) : null);
        return { rank, username, turnover, prize };
      });

      setItems(normalized);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const showUserCard = userTurnover > 50;
  const prizeLine = useMemo(() => `1st: ${prizes['1st']} | 2nd: ${prizes['2nd']} | 3rd: ${prizes['3rd']}`, [prizes]);

  return (
    <div className="mobile-frame min-h-dvh pb-10" style={{ backgroundColor: BG }}>
      {/* Header: back (yellow) + Leaderboard (white) — match APK */}
      <header className="sticky top-0 z-40 flex items-center gap-3 px-4 py-4" style={{ backgroundColor: BG }}>
        <button
          type="button"
          onClick={() => nav(-1)}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
          aria-label="Back"
        >
          <BackArrow />
        </button>
        <h1 className="flex-1 text-center text-xl font-bold text-white">Leaderboard</h1>
        <div className="w-11" />
      </header>

      <div className="mx-auto max-w-[460px] px-4 pt-2">
        {/* YOUR RANKING card — only when turnover > 50, dark grey + gold border */}
        {showUserCard && (
          <div
            className="mb-4 rounded-2xl p-4"
            style={{ backgroundColor: SURFACE, border: `1px solid ${CARD_BORDER}` }}
          >
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs font-bold uppercase tracking-wider text-[#BDBDBD]">YOUR RANKING</div>
                <div className="mt-1 text-2xl font-black text-[#FFCC00]">
                  {userRank > 0 ? `#${userRank}` : 'Unranked'}
                </div>
              </div>
              <div className="text-right">
                <div className="text-xs font-bold uppercase tracking-wider text-[#BDBDBD]">YOUR DAILY TURNOVER</div>
                <div className="mt-1 text-lg font-bold text-white">₹{userTurnover.toFixed(2)}</div>
              </div>
            </div>
          </div>
        )}

        {/* DAILY CHAMPIONS card — match APK */}
        <div
          className="mb-6 rounded-2xl p-4 text-center"
          style={{ backgroundColor: SURFACE }}
        >
          <div className="text-sm font-black tracking-widest text-[#FFCC00]">DAILY CHAMPIONS</div>
          <div className="mt-2 text-base font-bold text-white">{prizeLine}</div>
          <div className="mt-1 text-xs text-[#BDBDBD]">Daily turnover based prizes!</div>
          <div className="mt-0.5 text-xs text-[#BDBDBD]">Results will be announced daily 11:00 PM night</div>
        </div>

        {loading ? (
          <div className="flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#FFCC00] border-t-transparent" />
          </div>
        ) : items.length === 0 ? (
          <div className="py-12 text-center text-[#BDBDBD]">No data available</div>
        ) : (
          <div className="space-y-3 pb-6">
            {items.map((it) => (
              <LeaderboardItem key={`${it.rank}-${it.username}`} item={it} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function LeaderboardItem({ item }: { item: LeaderItem }) {
  const { rank, username, turnover, prize } = item;
  const isTopThree = rank <= 3;
  const rankBg =
    rank === 1 ? '#FFD700' : rank === 2 ? '#C0C0C0' : rank === 3 ? '#CD7F32' : 'transparent';
  const rankTextColor = isTopThree ? '#121212' : '#BDBDBD';

  return (
    <div
      className="flex items-center rounded-xl px-4 py-4"
      style={{
        backgroundColor: SURFACE,
        border: rank === 1 ? '1px solid rgba(255, 215, 0, 0.3)' : undefined,
      }}
    >
      <div
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-base font-bold"
        style={{ backgroundColor: rankBg, color: rankTextColor }}
      >
        {rank}
      </div>
      <div className="ml-4 min-w-0 flex-1">
        <div className="text-lg font-bold text-white">{username}</div>
        <div className="text-sm text-[#BDBDBD]">Daily Turnover: ₹{turnover.toFixed(2)}</div>
      </div>
      {prize && (
        <div className="shrink-0 text-right">
          <div className="text-[10px] font-black uppercase text-[#BDBDBD]">PRIZE</div>
          <div className="text-base font-extrabold text-[#FFCC00]">{prize}</div>
        </div>
      )}
    </div>
  );
}
