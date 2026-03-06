import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiAddBankDetail } from '../api/endpoints';

const inputClass =
  'mt-1.5 w-full rounded-xl border-0 bg-[#2a2a2a] px-4 py-3 text-white placeholder:text-textGrey focus:ring-2 focus:ring-primaryYellow';
const labelClass = 'text-sm font-medium text-primaryYellow';

export function AddBankAccountPage() {
  const nav = useNavigate();
  const [accountHolderName, setAccountHolderName] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [bankName, setBankName] = useState('');
  const [ifsc, setIfsc] = useState('');
  const [isDefault, setIsDefault] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!accountHolderName.trim() || !accountNumber.trim() || !bankName.trim() || !ifsc.trim()) {
      setError('Please fill all fields.');
      return;
    }
    setSubmitting(true);
    try {
      await apiAddBankDetail({
        account_holder_name: accountHolderName.trim(),
        account_number: accountNumber.trim(),
        bank_name: bankName.trim(),
        ifsc_code: ifsc.trim(),
        is_default: isDefault,
      });
      nav(-1);
    } catch (err: any) {
      const msg =
        err?.response?.data?.detail ||
        err?.response?.data?.message ||
        'Could not add account. Please try again.';
      setError(String(msg));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mobile-frame min-h-dvh bg-[#1A1A1A]">
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
          <h1 className="flex-1 text-center text-xl font-bold text-primaryYellow">Add bank account</h1>
          <div className="w-11" />
        </div>
      </header>

      <form onSubmit={handleSubmit} className="px-4 py-6">
        <div className="space-y-5">
          <div>
            <label className={labelClass}>Account holder name</label>
            <input
              type="text"
              value={accountHolderName}
              onChange={(e) => setAccountHolderName(e.target.value)}
              placeholder="Please fill the Account holder name"
              className={inputClass}
              autoComplete="name"
            />
          </div>
          <div>
            <label className={labelClass}>Account Number</label>
            <input
              type="text"
              inputMode="numeric"
              value={accountNumber}
              onChange={(e) => setAccountNumber(e.target.value.replace(/\D/g, ''))}
              placeholder="Please enter the bank account number"
              className={inputClass}
              autoComplete="off"
            />
          </div>
          <div>
            <label className={labelClass}>Bank name</label>
            <input
              type="text"
              value={bankName}
              onChange={(e) => setBankName(e.target.value)}
              placeholder="Please select bank"
              className={inputClass}
              autoComplete="organization"
            />
          </div>
          <div>
            <label className={labelClass}>IFSC</label>
            <input
              type="text"
              value={ifsc}
              onChange={(e) => setIfsc(e.target.value.toUpperCase())}
              placeholder="Please fill in the open account bank address"
              className={inputClass}
              autoComplete="off"
            />
          </div>
        </div>

        <div className="mt-6 flex items-center justify-between">
          <span className="text-sm font-medium text-white">Set as default :</span>
          <button
            type="button"
            role="switch"
            aria-checked={isDefault}
            onClick={() => setIsDefault((v) => !v)}
            className={`relative h-7 w-12 shrink-0 rounded-full transition-colors ${
              isDefault ? 'bg-primaryYellow' : 'bg-[#555]'
            }`}
          >
            <span
              className={`absolute top-1 h-5 w-5 rounded-full bg-white shadow transition-all ${
                isDefault ? 'left-6' : 'left-1'
              }`}
            />
          </button>
        </div>

        {error && (
          <p className="mt-4 text-sm text-red-400">{error}</p>
        )}

        {/* APK: Submit button height 56dp, rounded 8dp */}
        <button
          type="submit"
          disabled={submitting}
          className="mt-8 flex h-14 w-full items-center justify-center rounded-lg bg-primaryYellow text-base font-semibold text-white transition-opacity hover:opacity-95 disabled:opacity-60"
        >
          Submit
        </button>
      </form>
    </div>
  );
}
