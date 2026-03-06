import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiMyDeposits, type DepositRequest } from '../api/endpoints';

type Filter = 'All' | 'Success' | 'Failed';

export function DepositRecordPage() {
  const nav = useNavigate();
  const [list, setList] = useState<DepositRequest[]>([]);
  const [filter, setFilter] = useState<Filter>('All');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    apiMyDeposits()
      .then((r) => setList(r.data ?? []))
      .catch(() => setList([]))
      .finally(() => setLoading(false));
  }, []);

  const filtered =
    filter === 'Success'
      ? list.filter((d) => d.status === 'APPROVED')
      : filter === 'Failed'
        ? list.filter((d) => d.status === 'REJECTED')
        : list;

  return (
    <div className="mobile-frame min-h-dvh bg-[#1A1A1A]">
      {/* Header: yellow back, "Deposit Record" center */}
      <header className="sticky top-0 z-40 bg-[#1A1A1A]/95 backdrop-blur">
        <div className="mx-auto flex max-w-[430px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-primaryYellow transition-opacity hover:opacity-90"
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-primaryYellow">Deposit Record</h1>
          <div className="w-11" />
        </div>
      </header>

      <div className="px-4 py-4">
        {/* Filter tabs: All, Success, Failed */}
        <div className="flex gap-3">
          {(['All', 'Success', 'Failed'] as const).map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              className={`rounded-xl border px-4 py-2 text-sm font-medium ${
                filter === f
                  ? 'border-primaryYellow bg-primaryYellow text-black'
                  : 'border-[#555] bg-[#2a2a2a] text-textGrey'
              }`}
            >
              {f}
            </button>
          ))}
        </div>

        {/* Summary :N in green (right) */}
        <div className="mt-4 flex justify-end gap-1 text-sm">
          <span className="text-textWhite">Summary</span>
          <span className="font-bold text-green-500">:{filtered.length}</span>
        </div>

        {loading ? (
          <div className="mt-6 flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-primaryYellow border-t-transparent" />
          </div>
        ) : (
          <div className="mt-4 space-y-3 pb-8">
            {filtered.map((d) => {
              const isRejected = d.status === 'REJECTED';
              return (
                <div key={d.id} className="rounded-xl bg-[#2a2a2a] p-4">
                  <div className="flex justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <p className="font-bold">
                        <span className={isRejected ? 'text-red-500' : 'text-white'}>Deposit #{d.id}</span>
                        <span className={isRejected ? ' text-red-500' : ' text-white'}>
                          {' '}({d.status})
                        </span>
                      </p>
                      <p className="mt-1 text-xs text-textGrey">{d.created_at}</p>
                      {isRejected && d.admin_note && (
                        <p className="mt-2 text-sm text-textGrey">{d.admin_note}</p>
                      )}
                    </div>
                    <p className="shrink-0 font-bold text-primaryYellow">₹ {d.amount}</p>
                  </div>
                </div>
              );
            })}
            {filtered.length === 0 && <p className="py-8 text-center text-textGrey">No data available</p>}
          </div>
        )}
      </div>
    </div>
  );
}
