import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';

type PaymentMethod = 'Bank' | 'UPI' | 'USDT';

export function DepositPage() {
  const nav = useNavigate();
  const [method, setMethod] = useState<PaymentMethod>('UPI');
  const [amount, setAmount] = useState('200');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    // Placeholder: in app this would show payment information
    setTimeout(() => {
      setSubmitting(false);
      alert('Enter the amount and click confirm — payment information will be displayed. (Integration coming soon.)');
    }, 500);
  };

  return (
    <div className="mobile-frame min-h-dvh bg-appBg">
      {/* Header: yellow back arrow + yellow "Deposit" title */}
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
          <h1 className="flex-1 text-center text-xl font-bold text-primaryYellow">Deposit</h1>
          <div className="w-11" />
        </div>
      </header>

      <div className="px-4 py-4">
        {/* Payment method */}
        <div>
          <div className="text-base font-medium text-textGrey">Payment method</div>
          <div className="mt-3 flex gap-2">
            {(['Bank', 'UPI', 'USDT'] as const).map((tab) => (
              <button
                key={tab}
                type="button"
                onClick={() => setMethod(tab)}
                className={`relative px-5 py-2.5 text-base font-medium transition-colors ${
                  method === tab ? 'text-primaryYellow' : 'text-textGrey'
                }`}
              >
                {tab}
                {method === tab && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primaryYellow" />
                )}
              </button>
            ))}
          </div>
          {/* Selected method card (yellow border) */}
          <div className="mt-4 rounded-xl border-2 border-primaryYellow bg-surface/80 p-5">
            <div className="flex flex-col items-center gap-3">
              <div className="flex h-14 w-14 items-center justify-center rounded-lg bg-white/10">
                <span className="text-3xl font-black text-white">UPI</span>
              </div>
              <span className="text-base font-medium text-textWhite lowercase">{method.toLowerCase()}</span>
            </div>
          </div>
        </div>

        {/* Deposit amount */}
        <div className="mt-6">
          <div className="text-base font-medium text-textGrey">Deposit amount</div>
          <p className="mt-2 text-base font-bold text-primaryYellow">
            Deposit ₹2000 or more to get a FREE MEGA SPIN!
          </p>
          <p className="mt-1 text-sm text-red-500">
            Enter the amount and click confirm, the payment information will be displayed.
          </p>
          <div className="mt-4">
            <label htmlFor="deposit-amount" className="sr-only">
              Amount
            </label>
            <div className="flex items-center gap-3 rounded-xl border border-border bg-surface px-5 py-4">
              <span className="text-xl font-bold text-textWhite">₹</span>
              <input
                id="deposit-amount"
                type="number"
                min="1"
                step="1"
                value={amount}
                onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, '').slice(0, 10))}
                className="min-w-0 flex-1 bg-transparent text-xl font-bold text-textWhite outline-none placeholder:text-textGrey"
                placeholder="0"
              />
            </div>
          </div>
          <div className="mt-3 flex items-center gap-2 text-base text-textGrey">
            <span>Current success deposit rate :</span>
            <span className="rounded-full bg-green-600 px-3 py-1 text-sm font-medium text-white">
              High
            </span>
          </div>
        </div>

        {/* Submit */}
        <form onSubmit={handleSubmit} className="mt-6">
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-xl bg-primaryYellow py-4 text-lg font-bold text-black transition-opacity hover:opacity-95 disabled:opacity-60"
          >
            {submitting ? 'Submitting…' : 'Submit'}
          </button>
        </form>

        {/* Reminder */}
        <div className="mt-8">
          <div className="text-base font-bold text-red-500">Reminder:</div>
          <p className="mt-2 text-sm leading-relaxed text-textWhite">
            Kindly refrain from saving previously used bank account details for your payments, as the
            receiving bank account changes frequently. Once a deposit is made to a frozen account, we
            cannot be held accountable for any resulting issues.
          </p>
        </div>
      </div>
    </div>
  );
}
