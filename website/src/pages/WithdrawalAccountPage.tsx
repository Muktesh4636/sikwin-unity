import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiBankDetails, apiDeleteBankDetail, type BankDetail } from '../api/endpoints';

function maskAccountNumber(accountNumber: string): string {
  if (!accountNumber || accountNumber.length < 4) return '****';
  return '******** ' + accountNumber.slice(-4);
}

export function WithdrawalAccountPage() {
  const nav = useNavigate();
  const [banks, setBanks] = useState<BankDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [removingId, setRemovingId] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await apiBankDetails();
      setBanks(res.data ?? []);
    } catch {
      setBanks([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleRemove = async (id: number) => {
    if (removingId != null) return;
    setRemovingId(id);
    try {
      await apiDeleteBankDetail(id);
      await load();
    } catch {
      setRemovingId(null);
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <div className="mobile-frame min-h-dvh bg-[#1A1A1A]">
      {/* Header: yellow back, "My Withdrawal Account" in white centered */}
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
          <h1 className="flex-1 text-center text-xl font-bold text-white">My Withdrawal Account</h1>
          <div className="w-11" />
        </div>
      </header>

      <div className="px-4 pb-8 pt-2">
        {loading ? (
          <div className="flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-primaryYellow border-t-transparent" />
          </div>
        ) : (
          <>
            <div className="space-y-4">
              {banks.map((bank, index) => (
                <div
                  key={bank.id ?? index}
                  className="relative flex min-h-[200px] overflow-hidden rounded-2xl bg-gradient-to-b from-[#E6B84D] to-[#C48B22] px-6 py-6"
                >
                  {bank.id != null && (
                  <button
                    type="button"
                    onClick={() => handleRemove(bank.id!)}
                    disabled={removingId === bank.id}
                    className="absolute right-3 top-3 flex h-6 w-6 items-center justify-center rounded-full bg-white text-black transition-opacity hover:opacity-90 disabled:opacity-50"
                    aria-label="Remove account"
                  >
                    <span className="text-base font-bold leading-none">×</span>
                  </button>
                  )}
                  <div className={`flex flex-1 flex-col justify-between ${bank.id != null ? 'pr-8' : ''}`}>
                    <div>
                      <p className="text-lg font-bold text-white">{bank.bank_name}</p>
                      <p className="mt-0.5 text-base text-white/80">
                        {bank.account_holder_name ?? bank.bank_name}
                      </p>
                      <p className="mt-2 text-2xl font-medium text-white">{maskAccountNumber(bank.account_number)}</p>
                    </div>
                    <div className="mt-4 flex items-center justify-between text-sm text-white/80">
                      <span>{bank.is_default ? 'Set as default' : 'Not default'}</span>
                      <span>IFSC: {bank.ifsc_code}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* APK: height 56dp, rounded 8dp */}
            <button
              type="button"
              onClick={() => nav('/withdrawal-account/add')}
              className="mt-8 flex h-14 w-full items-center justify-center rounded-lg bg-[#DAA520] text-base font-bold text-black transition-opacity hover:opacity-95 active:opacity-90"
            >
              add bank account number
            </button>
          </>
        )}
      </div>
    </div>
  );
}
