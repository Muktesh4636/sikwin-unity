import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiBettingHistory, type Bet } from '../api/endpoints';

/** 3D dice image for each betting card */
function DiceIcon() {
  return <img src="/dice_3d.png" alt="" className="h-7 w-7 shrink-0 object-contain" />;
}

export function BettingRecordPage() {
  const nav = useNavigate();
  const [list, setList] = useState<Bet[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    apiBettingHistory()
      .then((r) => {
        const data = r.data;
        setList(Array.isArray(data) ? data : (data?.results ?? []));
      })
      .catch(() => setList([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mobile-frame min-h-dvh bg-[#121212]">
      {/* APK Color.kt: BlackBackground #121212, SurfaceColor #1E1E1E, PrimaryYellow #FFCC00, TextWhite, TextGrey #BDBDBD, GreenSuccess #4CAF50, RedError #F44336 */}
      <header className="sticky top-0 z-40 bg-[#121212]/95 backdrop-blur">
        <div className="mx-auto max-w-[460px] px-4 py-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => nav(-1)}
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
              aria-label="Back"
            >
              <BackArrow />
            </button>
            <h1 className="flex-1 text-center text-xl font-bold text-[#FFCC00]">Betting Record</h1>
            <div className="w-11" />
          </div>
          <div className="mt-2 flex justify-end gap-1 text-sm">
            <span className="text-[#FFFFFF]">Summary :</span>
            <span className="font-bold text-[#4CAF50]">{list.length}</span>
          </div>
        </div>
      </header>

      <div className="px-4 pt-4 pb-24">
        {loading ? (
          <div className="flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#FFCC00] border-t-transparent" />
          </div>
        ) : (
          <div className="space-y-2">
            {list.map((b, index) => (
              <div key={`bet-${index}-${b.id}`} className="flex flex-wrap gap-3 rounded-lg bg-[#1E1E1E] p-4">
                <DiceIcon />
                <div className="min-w-0 flex-1">
                  <p className="font-bold text-[#FFFFFF]">Round: {b.round.round_id}</p>
                  <p className="mt-1 text-sm font-medium text-[#FFCC00]">Bet on Number: {b.number}</p>
                  <p className="mt-1 text-xs text-[#BDBDBD]">{b.created_at}</p>
                </div>
                <div className="shrink-0 text-right">
                  <p className="font-bold text-[#FFFFFF]">₹ {b.chip_amount}</p>
                  <p className={`text-sm font-bold ${b.is_winner ? 'text-[#4CAF50]' : 'text-[#F44336]'}`}>
                    {b.is_winner ? `WIN (₹${b.payout_amount})` : 'LOSE'}
                  </p>
                </div>
              </div>
            ))}
            {list.length === 0 && <p className="py-8 text-center text-[#BDBDBD]">No data available</p>}
          </div>
        )}
      </div>
    </div>
  );
}
