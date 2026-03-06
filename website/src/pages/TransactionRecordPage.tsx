import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import {
  apiBettingHistory,
  apiMyDeposits,
  apiMyWithdrawals,
  type Bet,
  type DepositRequest,
  type WithdrawRequest,
} from '../api/endpoints';

type Tab = 'Deposit' | 'Withdraw' | 'Betting';
type Filter = 'All' | 'Success' | 'Failed';

export function TransactionRecordPage() {
  const nav = useNavigate();
  const [tab, setTab] = useState<Tab>('Deposit');
  const [filter, setFilter] = useState<Filter>('All');
  const [deposits, setDeposits] = useState<DepositRequest[]>([]);
  const [withdrawals, setWithdrawals] = useState<WithdrawRequest[]>([]);
  const [betting, setBetting] = useState<Bet[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    if (tab === 'Deposit') {
      apiMyDeposits()
        .then((r) => setDeposits(r.data ?? []))
        .catch(() => setDeposits([]))
        .finally(() => setLoading(false));
    } else if (tab === 'Withdraw') {
      apiMyWithdrawals()
        .then((r) => setWithdrawals(r.data ?? []))
        .catch(() => setWithdrawals([]))
        .finally(() => setLoading(false));
    } else {
      apiBettingHistory()
        .then((r) => setBetting(r.data ?? []))
        .catch(() => setBetting([]))
        .finally(() => setLoading(false));
    }
  }, [tab]);

  const filteredDeposits =
    filter === 'Success' ? deposits.filter((d) => d.status === 'APPROVED') : filter === 'Failed' ? deposits.filter((d) => d.status === 'REJECTED') : deposits;
  const filteredWithdrawals =
    filter === 'Success' ? withdrawals.filter((w) => w.status === 'COMPLETED') : filter === 'Failed' ? withdrawals.filter((w) => w.status === 'REJECTED') : withdrawals;

  const count =
    tab === 'Deposit' ? filteredDeposits.length : tab === 'Withdraw' ? filteredWithdrawals.length : betting.length;

  return (
    <div className="mobile-frame min-h-dvh bg-[#1A1A1A]">
      {/* Header: yellow back + yellow title */}
      <header className="sticky top-0 z-40 bg-[#1A1A1A]/95 backdrop-blur">
        <div className="mx-auto flex max-w-[430px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="flex h-11 w-11 items-center justify-center rounded text-primaryYellow transition-opacity hover:opacity-90"
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-primaryYellow">Transaction Record</h1>
          <div className="w-11" />
        </div>
      </header>

      <div className="px-4 py-4">
        {/* Tabs: Deposit, Withdraw, Betting */}
        <div className="flex justify-between gap-2">
          {(['Deposit', 'Withdraw', 'Betting'] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => {
                setTab(t);
                setFilter('All');
              }}
              className={`flex flex-col items-center pb-2 text-base font-medium ${tab === t ? 'text-primaryYellow' : 'text-textGrey'}`}
            >
              {t}
              {tab === t && <span className="mt-1 block h-0.5 w-10 rounded bg-primaryYellow" />}
            </button>
          ))}
        </div>

        {/* Filters: All, Success, Failed (only for Deposit & Withdraw) */}
        {tab !== 'Betting' && (
          <div className="mt-4 flex gap-3">
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
        )}

        {/* Summary */}
        <div className="mt-4 flex justify-end gap-1 text-sm">
          <span className="text-textWhite">Summary</span>
          <span className="font-bold text-green-500">:{count}</span>
        </div>

        {/* List */}
        {loading ? (
          <div className="mt-6 flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-primaryYellow border-t-transparent" />
          </div>
        ) : (
          <div className="mt-4 space-y-3 pb-8">
            {tab === 'Deposit' &&
              filteredDeposits.map((d) => (
                <div
                  key={d.id}
                  className="rounded-xl bg-[#2a2a2a] p-4"
                >
                  <div className="flex justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <p className={`font-bold ${d.status === 'REJECTED' ? 'text-red-500' : 'text-white'}`}>
                        Deposit #{d.id} ({d.status})
                      </p>
                      <p className="mt-1 text-xs text-textGrey">{d.created_at}</p>
                      {d.status === 'REJECTED' && d.admin_note && (
                        <p className="mt-2 text-sm text-textGrey">{d.admin_note}</p>
                      )}
                    </div>
                    <p className="shrink-0 font-bold text-primaryYellow">₹ {d.amount}</p>
                  </div>
                </div>
              ))}
            {tab === 'Withdraw' &&
              filteredWithdrawals.map((w) => (
                <div
                  key={w.id}
                  className="rounded-xl bg-[#2a2a2a] p-4"
                >
                  <div className="flex justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <p className={`font-bold ${w.status === 'REJECTED' ? 'text-red-500' : 'text-white'}`}>
                        Withdrawal #{w.id} ({w.status})
                      </p>
                      <p className="mt-1 text-xs text-textGrey">{w.created_at}</p>
                      {w.status === 'REJECTED' && w.admin_note && (
                        <p className="mt-2 text-sm text-textGrey">{w.admin_note}</p>
                      )}
                    </div>
                    <p className="shrink-0 font-bold text-primaryYellow">₹ {w.amount}</p>
                  </div>
                </div>
              ))}
            {tab === 'Betting' &&
              betting.map((b) => (
                <div
                  key={b.id}
                  className="rounded-xl bg-[#2a2a2a] p-4"
                >
                  <div className="flex justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <p className="font-bold text-white">Round: {b.round.round_id}</p>
                      <p className="mt-1 text-sm text-primaryYellow">Bet on Number: {b.number}</p>
                      <p className="mt-1 text-xs text-textGrey">{b.created_at}</p>
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="font-bold text-white">₹ {b.chip_amount}</p>
                      <p className={`text-sm font-bold ${b.is_winner ? 'text-green-500' : 'text-red-500'}`}>
                        {b.is_winner ? `WIN (₹${b.payout_amount})` : 'LOSE'}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            {count === 0 && (
              <p className="py-8 text-center text-textGrey">No data available</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
