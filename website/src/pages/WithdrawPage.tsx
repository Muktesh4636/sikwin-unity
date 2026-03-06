import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiBankDetails, apiInitiateWithdraw, apiWallet, type Wallet } from '../api/endpoints';

type Bank = {
  bank_name: string;
  account_number: string;
  ifsc_code: string;
  is_default?: boolean;
} & Record<string, any>;

export function WithdrawPage() {
  const nav = useNavigate();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [banks, setBanks] = useState<Bank[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [amount, setAmount] = useState('');
  const [selected, setSelected] = useState<Bank | null>(null);
  const [showBankDropdown, setShowBankDropdown] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  const load = async () => {
    setError(null);
    setOk(null);
    setLoading(true);
    try {
      const [w, b] = await Promise.all([apiWallet(), apiBankDetails()]);
      setWallet(w.data);
      const list = (b.data ?? []) as Bank[];
      setBanks(list);
      const def = list.find((x) => x.is_default) ?? list[0] ?? null;
      setSelected(def);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const withdrawable = useMemo(() => wallet?.withdrawable_balance ?? '0', [wallet]);
  const unavailable = useMemo(
    () => wallet?.unavailable_balance ?? wallet?.unavaliable_balance ?? '0',
    [wallet]
  );

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setOk(null);
    if (!amount || !selected) return;
    setSubmitting(true);
    try {
      const details = `Bank: ${selected.bank_name}, Acc: ${selected.account_number}, IFSC: ${selected.ifsc_code}`;
      await apiInitiateWithdraw(amount, 'Bank Account', details);
      setOk('Withdrawal request submitted.');
      setAmount('');
      await load();
    } catch (e: any) {
      const msg =
        e?.response?.data?.detail ||
        e?.response?.data?.message ||
        'Could not submit. Please try again.';
      setError(String(msg));
    } finally {
      setSubmitting(false);
    }
  };

  const bankLabel = selected
    ? `${selected.bank_name} (${selected.account_number.slice(-4)})`
    : '';

  return (
    <div className="mobile-frame min-h-dvh bg-appBg">
      {/* Header: yellow back + yellow "Online withdrawal" */}
      <header className="sticky top-0 z-40 border-b border-border bg-appBg/90 backdrop-blur">
        <div className="mx-auto flex max-w-[430px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="flex h-11 w-11 items-center justify-center rounded text-primaryYellow transition-opacity hover:opacity-90"
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-primaryYellow">
            Online withdrawal
          </h1>
          <div className="w-11" />
        </div>
      </header>

      <div className="px-4 py-4">
        {/* Withdrawal method: Bank Account (yellow + underline) */}
        <div className="mb-5">
          <span className="relative inline-block text-lg font-bold text-primaryYellow">
            Bank Account
            <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primaryYellow" />
          </span>
        </div>

        {/* Balance card: Available (yellow) | Unavailable (grey) */}
        <div className="rounded-xl border border-border bg-surface px-5 py-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="text-sm font-medium text-textGrey">Available Balance</div>
              <div className="mt-2 text-2xl font-bold text-primaryYellow">
                ₹{loading ? '—' : withdrawable}
              </div>
            </div>
            <div className="text-right">
              <div className="text-sm font-medium text-textGrey">Unavailable Balance</div>
              <div className="mt-2 text-2xl font-bold text-textGrey">
                ₹{loading ? '—' : unavailable}
              </div>
            </div>
          </div>
        </div>

        {loading ? (
          <div className="mt-6 text-center text-textGrey">Loading…</div>
        ) : banks.length === 0 ? (
          <div className="mt-6 rounded-xl border border-border bg-surface p-5">
            <div className="text-base font-bold text-primaryYellow">No bank account added</div>
            <p className="mt-2 text-base text-textGrey">
              Add a bank account in the app. Website add-bank will be added next.
            </p>
          </div>
        ) : (
          <form onSubmit={submit} className="mt-6 space-y-4">
            {/* Bank account dropdown (dark grey field + chevron) */}
            <div className="relative">
              <button
                type="button"
                onClick={() => setShowBankDropdown((v) => !v)}
                className="flex w-full items-center justify-between gap-2 rounded-xl border border-border bg-surface px-5 py-4 text-left text-base text-textWhite"
              >
                <span className={selected ? '' : 'text-textGrey'}>
                  {selected ? bankLabel : 'Select bank account'}
                </span>
                <svg
                  className={`h-5 w-5 shrink-0 text-textGrey transition-transform ${showBankDropdown ? 'rotate-180' : ''}`}
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              {showBankDropdown && (
                <>
                  <div
                    className="fixed inset-0 z-10"
                    aria-hidden
                    onClick={() => setShowBankDropdown(false)}
                  />
                  <ul className="absolute left-0 right-0 top-full z-20 mt-1 max-h-48 overflow-auto rounded-xl border border-border bg-surface py-1">
                    {banks.map((b) => (
                      <li key={b.account_number}>
                        <button
                          type="button"
                          onClick={() => {
                            setSelected(b);
                            setShowBankDropdown(false);
                          }}
                          className={`w-full px-5 py-3.5 text-left text-base ${
                            selected?.account_number === b.account_number
                              ? 'bg-primaryYellow/20 font-medium text-primaryYellow'
                              : 'text-textWhite'
                          }`}
                        >
                          {b.bank_name} ({b.account_number.slice(-4)})
                        </button>
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </div>

            {/* Amount input */}
            <div className="flex items-center gap-3 rounded-xl border border-border bg-surface px-5 py-4">
              <span className="text-xl font-bold text-textWhite">₹</span>
              <input
                type="text"
                inputMode="numeric"
                value={amount}
                onChange={(e) => setAmount(e.target.value.replace(/[^\d]/g, '').slice(0, 12))}
                placeholder="Please enter amount"
                className="min-w-0 flex-1 bg-transparent text-lg font-medium text-textWhite outline-none placeholder:text-textGrey"
              />
            </div>

            {error && (
              <div className="rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-2 text-sm text-red-400">
                {error}
              </div>
            )}
            {ok && (
              <div className="rounded-xl border border-green-500/40 bg-green-500/10 px-4 py-2 text-sm text-green-400">
                {ok}
              </div>
            )}

            {/* Submit */}
            <button
              type="submit"
              disabled={submitting || !amount || !selected}
              className="w-full rounded-xl bg-primaryYellow py-4 text-lg font-bold text-white transition-opacity hover:opacity-95 disabled:opacity-50"
            >
              {submitting ? 'Submitting…' : 'Submit'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
