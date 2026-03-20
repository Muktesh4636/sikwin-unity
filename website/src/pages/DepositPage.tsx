import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import {
  apiPaymentMethods,
  apiUploadDepositProof,
  type PaymentMethod as ApiPaymentMethod,
} from '../api/endpoints';

type DepositMethod = 'Bank' | 'UPI' | 'USDT';
type PayApp = 'google_pay' | 'paytm' | 'phone_pe' | 'upi';

// Match Kotlin APK PaymentScreen colors (0xFF3F51B5)
const PAYMENT_HEADER_BG = '#3F51B5';
const APK_BG = '#F5F5F5';

function BackArrowWhite({ className = 'h-7 w-7' }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="white" viewBox="0 0 24 24" aria-hidden>
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
    </svg>
  );
}

function CopyIcon({ className = 'h-5 w-5' }: { className?: string }) {
  return (
    <svg className={className} style={{ color: PAYMENT_HEADER_BG }} fill="currentColor" viewBox="0 0 24 24" aria-hidden>
      <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" />
    </svg>
  );
}

async function copyToClipboard(_label: string, value: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(value);
    return true;
  } catch {
    return false;
  }
}

function BankDetailRow({
  label,
  value,
  onCopy,
}: {
  label: string;
  value: string;
  onCopy: (label: string, value: string) => void;
}) {
  return (
    <div className="flex w-full items-center justify-between py-2">
      <div className="min-w-0 flex-1">
        <p className="text-xs text-gray-500">{label}</p>
        <p className="text-base font-medium text-black">{value}</p>
      </div>
      <button
        type="button"
        onClick={() => onCopy(label, value)}
        className="ml-2 shrink-0 rounded p-1 transition-opacity hover:opacity-80"
        aria-label={`Copy ${label}`}
      >
        <CopyIcon />
      </button>
    </div>
  );
}

const PAYEE_NAME = 'Gundu Ata';
const TRANSACTION_NOTE = 'Deposit';

function buildUpiParams(upiId: string, amount: string, payeeName: string, note: string): string {
  return new URLSearchParams({
    pa: upiId,
    pn: payeeName.replace(/ /g, '%20'),
    am: amount,
    cu: 'INR',
    tn: note.replace(/ /g, '%20'),
  }).toString();
}

function buildUpiLink(upiId: string, amount: string, payeeName: string, note: string): string {
  return `upi://pay?${buildUpiParams(upiId, amount, payeeName, note)}`;
}

/** Android intent URL to open a specific UPI app (PhonePe, Paytm, Google Pay) from browser. */
function buildUpiIntentLink(
  upiId: string,
  amount: string,
  payeeName: string,
  note: string,
  packageName: string
): string {
  const path = `pay?${buildUpiParams(upiId, amount, payeeName, note)}`;
  return `intent://${path}#Intent;scheme=upi;package=${packageName};end`;
}

const UPI_APP_PACKAGES: Record<PayApp, string | null> = {
  phone_pe: 'com.phonepe.app',
  paytm: 'net.one97.paytm',
  google_pay: 'com.google.android.apps.nbu.paisa.user',
  upi: null, // generic UPI chooser
};

function matchPayAppToMethod(payApp: PayApp, methods: ApiPaymentMethod[]): ApiPaymentMethod | null {
  const lower = (s: string) => s.toLowerCase().replace(/\s/g, '');
  for (const m of methods) {
    const name = lower(m.name);
    if (payApp === 'google_pay' && (name.includes('google') || name.includes('gpay'))) return m;
    if (payApp === 'paytm' && name.includes('paytm')) return m;
    if (payApp === 'phone_pe' && (name.includes('phonepe') || name.includes('phone'))) return m;
    if (payApp === 'upi' && (name.includes('upi') || name.includes('bhim') || m.method_type?.toLowerCase().includes('upi'))) return m;
  }
  return methods[0] ?? null;
}

const USDT_EXCHANGE_RATE = 95;
const USDT_MIN_DEPOSIT = 500;
const NORMAL_MIN_DEPOSIT = 200;
const USDT_BONUS_PERCENT = 0.05;

const REMINDER_FULL = 'Kindly refrain from saving previously used bank account details for your payments, as the receiving bank account changes frequently. Once a deposit is made to a frozen account, we cannot be held accountable for any resulting issues.';

export function DepositPage() {
  const nav = useNavigate();
  const [method, setMethod] = useState<DepositMethod>('UPI');
  const [usdtNetwork, setUsdtNetwork] = useState<'usdt_trc20' | 'usdt_bep20'>('usdt_trc20');
  const [amount, setAmount] = useState('200');
  const [submitting, setSubmitting] = useState(false);
  const [step, setStep] = useState<1 | 2>(1);
  const [selectedPayApp, setSelectedPayApp] = useState<PayApp>('paytm');
  const [screenshotFile, setScreenshotFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [paymentMethods, setPaymentMethods] = useState<ApiPaymentMethod[]>([]);
  const [loadingMethods, setLoadingMethods] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [paymentCountdown, setPaymentCountdown] = useState(600); // 10 minutes in seconds

  const amountNum = parseInt(amount, 10) || 0;
  const usdtAmount = method === 'USDT' && amountNum > 0 ? amountNum / USDT_EXCHANGE_RATE : 0;
  const bonusAmount = method === 'USDT' && amountNum > 0 ? amountNum * USDT_BONUS_PERCENT : 0;

  // 10-minute countdown on payment step; at 0 go back to step 1
  useEffect(() => {
    if (step !== 2) return;
    setPaymentCountdown(600);
    const t = setInterval(() => {
      setPaymentCountdown((prev) => {
        if (prev <= 0) {
          clearInterval(t);
          setStep(1);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(t);
  }, [step]);

  useEffect(() => {
    if (step === 2) {
      setLoadingMethods(true);
      apiPaymentMethods()
        .then((r) => setPaymentMethods(r.data ?? []))
        .catch(() => setPaymentMethods([]))
        .finally(() => setLoadingMethods(false));
    }
  }, [step]);

  const handleSubmitAmount = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setTimeout(() => {
      setSubmitting(false);
      setStep(2);
    }, 400);
  };

  const handleSavePayment = () => {
    // Save stays on same screen; upload section is below payment methods (APK layout)
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f && f.size <= 10 * 1024 * 1024 && /\.(jpg|jpeg|png)$/i.test(f.name)) {
      setScreenshotFile(f);
    }
    e.target.value = '';
  };

  const openUpiApp = (payApp: PayApp) => {
    const apiMethod = matchPayAppToMethod(payApp, paymentMethods);
    const upiId = apiMethod?.upi_id?.trim() ?? paymentMethods.find((m) => m.upi_id?.trim())?.upi_id?.trim();
    if (!upiId) return;
    const isAndroid = /Android/i.test(navigator.userAgent);
    const packageName = UPI_APP_PACKAGES[payApp];
    const link =
      isAndroid && packageName
        ? buildUpiIntentLink(upiId, amount, PAYEE_NAME, TRANSACTION_NOTE, packageName)
        : buildUpiLink(upiId, amount, PAYEE_NAME, TRANSACTION_NOTE);
    window.location.href = link;
  };

  const handleSubmitProof = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!screenshotFile) return;
    setUploadError(null);
    setSubmitting(true);
    try {
      await apiUploadDepositProof(amount, screenshotFile);
      setStep(1);
      setScreenshotFile(null);
    } catch (err: unknown) {
      const msg = err && typeof err === 'object' && 'response' in err
        ? (err as { response?: { data?: { detail?: string } } }).response?.data?.detail
        : null;
      setUploadError(msg ?? 'Upload failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const qrImage = paymentMethods.find((m) => m.qr_image && (m.method_type?.toLowerCase().includes('upi') || m.method_type?.toLowerCase().includes('qr') || m.name?.toLowerCase().includes('upi')))?.qr_image ?? paymentMethods.find((m) => m.qr_image)?.qr_image;

  // ——— Step 1: Amount form ———
  if (step === 1) {
    return (
      <div className="mx-auto w-full max-w-[500px] min-h-dvh bg-appBg">
        <header className="sticky top-0 z-40 border-b border-border bg-appBg/90 backdrop-blur">
          <div className="mx-auto flex max-w-[500px] items-center gap-2 px-4 py-3">
            <button
              type="button"
              onClick={() => nav(-1)}
              className="flex h-9 w-9 items-center justify-center rounded text-primaryYellow transition-opacity hover:opacity-90"
              aria-label="Back"
            >
              <BackArrow className="h-6 w-6" />
            </button>
            <h1 className="flex-1 text-center text-xl font-bold text-primaryYellow">Deposit</h1>
            <div className="w-9" />
          </div>
        </header>

        <div className="px-4 py-4 pb-28">
          <div>
            <div className="text-base font-medium text-textGrey">Payment method</div>
            <div className="mt-3 flex gap-3">
              {(['Bank', 'UPI', 'USDT'] as const).map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => {
                    setMethod(tab);
                    const min = tab === 'USDT' ? USDT_MIN_DEPOSIT : NORMAL_MIN_DEPOSIT;
                    if (amountNum < min) setAmount(String(min));
                  }}
                  className={`relative px-4 py-2 text-base font-medium transition-colors ${
                    method === tab ? 'text-primaryYellow' : 'text-textGrey'
                  }`}
                >
                  {tab}
                  {method === tab && <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primaryYellow" />}
                </button>
              ))}
            </div>

            {/* Option cards: match APK 120×80dp, 8dp radius, 8dp padding, 32dp icon, 10sp text */}
            <div className="mt-4 flex gap-3">
              {method === 'Bank' && (
                <div className="w-[110px] rounded-xl border-2 border-primaryYellow bg-surface/80 p-2.5">
                  <div className="flex flex-col items-center gap-1.5">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded bg-white">
                      <img src="/ic_bank.jpg" alt="Bank" className="h-6 w-6 object-contain" />
                    </div>
                    <span className="text-xs font-bold leading-tight text-textWhite">BANK</span>
                  </div>
                </div>
              )}
              {method === 'UPI' && (
                <div className="w-[110px] rounded-xl border-2 border-primaryYellow bg-surface/80 p-2.5">
                  <div className="flex flex-col items-center gap-1.5">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded bg-white">
                      <img src="/ic_upi.jpg" alt="UPI" className="h-6 w-6 object-contain" />
                    </div>
                    <span className="text-xs font-medium leading-tight text-textWhite lowercase">upi</span>
                  </div>
                </div>
              )}
              {method === 'USDT' && (
                <>
                  <button
                    type="button"
                    onClick={() => setUsdtNetwork('usdt_trc20')}
                    className={`w-[110px] rounded-xl border-2 p-2.5 transition-colors ${
                      usdtNetwork === 'usdt_trc20' ? 'border-primaryYellow bg-surface/80' : 'border-border bg-surface/60 opacity-80'
                    }`}
                  >
                    <div className="flex flex-col items-center gap-1.5">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-green-600/80">
                        <span className="text-sm font-bold text-white">T</span>
                      </div>
                      <span className="text-xs font-medium leading-tight text-textWhite">USDT (TRC20)</span>
                    </div>
                  </button>
                  <button
                    type="button"
                    onClick={() => setUsdtNetwork('usdt_bep20')}
                    className={`w-[110px] rounded-xl border-2 p-2.5 transition-colors ${
                      usdtNetwork === 'usdt_bep20' ? 'border-primaryYellow bg-surface/80' : 'border-border bg-surface/60 opacity-80'
                    }`}
                  >
                    <div className="flex flex-col items-center gap-1.5">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-green-600/80">
                        <span className="text-sm font-bold text-white">T</span>
                      </div>
                      <span className="text-xs font-medium leading-tight text-textWhite">USDT (BEP20)</span>
                    </div>
                  </button>
                </>
              )}
            </div>
          </div>

          <div className="mt-5">
            <div className="text-base font-medium text-textGrey">Deposit amount</div>
            <p className="mt-2 text-base font-bold text-primaryYellow">Deposit ₹2000 or more to get a FREE MEGA SPIN!</p>
            {method === 'USDT' ? (
              <div className="mt-2 rounded-xl border border-primaryYellow bg-primaryYellow/10 p-3.5">
                <p className="text-sm font-bold text-primaryYellow">USDT Deposit Info</p>
                <p className="mt-1 text-sm text-textWhite">Exchange Rate: 1 USDT = ₹{USDT_EXCHANGE_RATE}</p>
                <p className="text-sm text-textWhite">Minimum Deposit: ₹{USDT_MIN_DEPOSIT}</p>
                <p className="text-sm font-bold text-green-500">Bonus: 5% Extra Cashback</p>
              </div>
            ) : (
              <p className="mt-1.5 text-sm text-red-500">Enter the amount and click confirm, the payment information will be displayed.</p>
            )}
            <div className="mt-4">
              <label htmlFor="deposit-amount" className="sr-only">Amount</label>
              <div className="flex items-center gap-2 rounded-xl border border-border bg-surface px-4 py-3">
                <span className="text-lg font-bold text-textWhite">₹</span>
                <input
                  id="deposit-amount"
                  type="number"
                  min="1"
                  step="1"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, '').slice(0, 10))}
                  className="min-w-0 flex-1 bg-transparent text-lg font-bold text-textWhite outline-none placeholder:text-textGrey"
                  placeholder={method === 'USDT' ? '500' : '0'}
                />
                {method === 'USDT' && usdtAmount > 0 && (
                  <span className="text-xs font-medium text-primaryYellow">≈ {usdtAmount.toFixed(2)} USDT</span>
                )}
              </div>
            </div>
            {method === 'USDT' && bonusAmount > 0 && (
              <p className="mt-1.5 text-xs font-medium text-green-500">You will receive extra ₹{bonusAmount.toFixed(2)} cashback!</p>
            )}
            <div className="mt-2.5 flex items-center gap-2 text-sm text-textGrey">
              <span>Current success deposit rate :</span>
              <span className="rounded bg-green-600 px-2 py-0.5 text-[10px] font-medium text-white">High</span>
            </div>
          </div>

          <form onSubmit={handleSubmitAmount} className="mt-5">
            <button
              type="submit"
              disabled={submitting}
              className="h-14 w-full rounded-xl bg-primaryYellow text-lg font-bold text-black transition-opacity hover:opacity-95 disabled:opacity-60"
            >
              {submitting ? 'Submitting…' : 'Submit'}
            </button>
          </form>

          <div className="mt-6">
            <div className="text-base font-bold text-red-500">Reminder:</div>
            <p className="mt-1.5 text-sm leading-snug text-textWhite">
              {REMINDER_FULL}
            </p>
            <div className="mt-4 h-8" aria-hidden />
          </div>
        </div>
      </div>
    );
  }

  // ——— Step 2: Payment — match Kotlin APK PaymentScreen (Bank Transfer Details or QR + payment method) ———
  const bankMethod = paymentMethods.find((m) => m.method_type?.toLowerCase().includes('bank'));

  if (step === 2) {
    return (
      <div className="mx-auto w-full max-w-[500px] min-h-dvh" style={{ backgroundColor: APK_BG }}>
        <header className="sticky top-0 z-40" style={{ backgroundColor: PAYMENT_HEADER_BG }}>
          <div className="mx-auto flex max-w-[500px] items-center gap-2 px-4 py-3">
            <button
              type="button"
              onClick={() => setStep(1)}
              className="flex h-9 w-9 items-center justify-center rounded text-white transition-opacity hover:opacity-90"
              aria-label="Back"
            >
              <BackArrowWhite className="h-6 w-6" />
            </button>
            <h1 className="flex-1 text-center text-xl font-bold text-white">Payment</h1>
            <span className="min-w-[3.5rem] text-right text-sm font-mono font-semibold text-white" aria-live="polite">
              {Math.floor(paymentCountdown / 60)}:{(paymentCountdown % 60).toString().padStart(2, '0')}
            </span>
          </div>
        </header>

        <div className="mx-auto max-w-[500px] px-4 py-4 pb-24">
          <div className="text-center">
            <p className="text-base font-medium text-black">Amount Payable</p>
            <p className="mt-1 text-2xl font-bold" style={{ color: PAYMENT_HEADER_BG }}>₹{amount}</p>
            {method === 'USDT' ? null : (
              <p className="mt-1 text-sm text-black">Please fill in UTR after successful payment</p>
            )}
            <p className="mt-1 text-base font-bold text-black">
              {method === 'Bank' && 'Transfer to the account below'}
              {method === 'UPI' && 'Use Mobile Scan QR To Pay'}
              {method === 'USDT' && 'Please transfer USDT to the address below'}
            </p>
            {method === 'USDT' && (
              <p className="mt-1 text-base font-bold text-black">Use Mobile Scan QR To Pay</p>
            )}
          </div>

          {method === 'USDT' ? (
            /* USDT payment: green info box + wallet card (QR + address + Copy) — match APK padding/size */
            <>
              <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
                <p className="text-sm font-medium text-black">USDT Amount to Pay:</p>
                <p className="mt-1 text-xl font-bold text-emerald-800">
                  {usdtAmount > 0 ? `${usdtAmount.toFixed(2)} USDT` : '—'}
                </p>
                <p className="mt-1.5 text-sm text-gray-600">Exchange Rate: 1 USDT = ₹{USDT_EXCHANGE_RATE}</p>
                {bonusAmount > 0 && (
                  <p className="mt-1 text-sm font-medium text-emerald-800">
                    Bonus: ₹{bonusAmount.toFixed(2)} (5%) will be added!
                  </p>
                )}
              </div>
              {(() => {
                const networkLabel = usdtNetwork === 'usdt_trc20' ? 'TRC20' : 'BEP20';
                const usdtMethod = paymentMethods.find(
                  (m) =>
                    m.is_active &&
                    m.method_type?.toLowerCase().includes('usdt') &&
                    m.method_type?.toLowerCase().includes(usdtNetwork === 'usdt_trc20' ? 'trc20' : 'bep20')
                );
                const walletAddress = usdtMethod?.usdt_wallet_address?.trim() || usdtMethod?.upi_id?.trim() || '';
                return (
                  <div className="mt-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
                    <p className="text-sm font-medium text-gray-600">Network: {networkLabel}</p>
                    <div className="mt-3 flex justify-center">
                      {loadingMethods ? (
                        <span className="text-sm text-gray-500">Loading…</span>
                      ) : usdtMethod?.qr_image ? (
                        <img
                          src={usdtMethod.qr_image}
                          alt="USDT QR Code"
                          className="h-36 w-36 object-contain"
                        />
                      ) : (
                        <div className="flex h-36 w-36 items-center justify-center rounded bg-gray-100 text-gray-400 text-sm">
                          QR
                        </div>
                      )}
                    </div>
                    <p className="mt-3 text-sm text-gray-600">Wallet Address:</p>
                    <p className="mt-1 break-all font-mono text-sm font-medium text-black">
                      {walletAddress || '—'}
                    </p>
                    <button
                      type="button"
                      onClick={() => walletAddress && copyToClipboard('Wallet Address', walletAddress)}
                      disabled={!walletAddress}
                      className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl border border-gray-300 bg-gray-100 py-2.5 text-sm font-medium text-black transition-opacity disabled:opacity-50"
                    >
                      <CopyIcon className="h-5 w-5" />
                      Copy Address
                    </button>
                  </div>
                );
              })()}
            </>
          ) : method === 'Bank' ? (
            /* Bank Transfer Details card — match APK 16dp padding */
            <div className="mt-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              {loadingMethods ? (
                <p className="text-center text-sm text-gray-500">Loading…</p>
              ) : bankMethod ? (
                <>
                  <h2 className="text-lg font-bold text-black">Bank Transfer Details</h2>
                  <div className="mt-3">
                    <BankDetailRow
                      label="Bank Name"
                      value={bankMethod.bank_name ?? 'N/A'}
                      onCopy={(_, v) => copyToClipboard('Bank Name', v)}
                    />
                    <BankDetailRow
                      label="Account Name"
                      value={bankMethod.account_name ?? 'N/A'}
                      onCopy={(_, v) => copyToClipboard('Account Name', v)}
                    />
                    <BankDetailRow
                      label="Account Number"
                      value={bankMethod.account_number ?? 'N/A'}
                      onCopy={(_, v) => copyToClipboard('Account Number', v)}
                    />
                    <BankDetailRow
                      label="IFSC Code"
                      value={bankMethod.ifsc_code ?? 'N/A'}
                      onCopy={(_, v) => copyToClipboard('IFSC Code', v)}
                    />
                  </div>
                </>
              ) : (
                <p className="text-red-600 font-medium">Bank details unavailable</p>
              )}
            </div>
          ) : (
            /* UPI: QR + Save */
            <div className="mt-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              <div className="flex flex-col items-center">
                <div className="flex h-40 w-40 items-center justify-center overflow-hidden rounded-xl bg-[#374151]">
                  {loadingMethods ? (
                    <span className="text-sm text-white">Loading…</span>
                  ) : qrImage ? (
                    <img src={qrImage} alt="Scan to pay" className="h-full w-full object-contain" />
                  ) : (
                    <div className="h-24 w-24 rounded bg-emerald-400/80" aria-hidden />
                  )}
                </div>
                <button
                  type="button"
                  onClick={handleSavePayment}
                  className="mt-3 w-full rounded-xl py-3 text-base font-semibold text-white transition-opacity hover:opacity-95"
                  style={{ backgroundColor: PAYMENT_HEADER_BG }}
                >
                  Save
                </button>
              </div>
            </div>
          )}

          {/* Choose a payment method to pay — only for UPI */}
          {method === 'UPI' && (
            <div className="mt-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              <h2 className="text-base font-bold text-black">Choose a payment method to pay</h2>
              <div className="mt-3 space-y-1">
                {[
                  { id: 'google_pay' as const, label: 'Google Pay', icon: '/ic_gpay_custom.jpg' },
                  { id: 'paytm' as const, label: 'Paytm', icon: '/ic_paytm_custom.jpg' },
                  { id: 'phone_pe' as const, label: 'Phone Pe', icon: '/ic_phonepe_custom.jpg' },
                  { id: 'upi' as const, label: 'UPI', icon: '/ic_upi.jpg' },
                ].map((opt) => (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => { setSelectedPayApp(opt.id); openUpiApp(opt.id); }}
                    className={`flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-left ${
                      selectedPayApp === opt.id ? 'border-[#3F51B5] bg-[#E8F0FE]' : 'border-transparent bg-white'
                    }`}
                  >
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded bg-white shadow-sm">
                      <img src={opt.icon} alt="" className="h-7 w-7 object-contain" />
                    </span>
                    <span className="flex-1 text-base font-medium text-black">{opt.label}</span>
                    <span className="text-blue-500 text-sm" aria-hidden>↓</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Paid? Upload Payment Screenshot */}
          <div className="mt-4">
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-black">Paid? Upload Payment Screenshot</h2>
              <svg className="h-5 w-5 shrink-0" style={{ color: PAYMENT_HEADER_BG }} fill="currentColor" viewBox="0 0 24 24" aria-hidden>
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z"/>
              </svg>
              <a href="#" className="text-sm font-medium" style={{ color: PAYMENT_HEADER_BG }}>Guide</a>
            </div>
            <p className="mt-1.5 text-sm text-gray-500">Max file size: 10MB. JPG, PNG</p>
            <input
              ref={fileInputRef}
              type="file"
              accept=".jpg,.jpeg,.png"
              className="hidden"
              onChange={handleFileChange}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="mt-3 flex h-28 w-full flex-col items-center justify-center rounded-xl border border-gray-300 bg-white transition-colors hover:bg-gray-50"
            >
              {screenshotFile ? (
                <span className="text-sm font-medium text-gray-700">{screenshotFile.name}</span>
              ) : (
                <>
                  <svg className="mb-1.5 h-10 w-10 text-gray-400" fill="currentColor" viewBox="0 0 24 24" aria-hidden>
                    <path d="M19 7v2.99s-1.99.01-2 0V7h-3s.01-1.99 0-2h3V2h2v3h3v2h-3zm-3 4V8h-3V5H5c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-8h-3zM5 19l3-4 2 3 3-4 4 5H5z"/>
                  </svg>
                  <span className="text-sm text-gray-500">Click to select screenshot</span>
                </>
              )}
            </button>
          </div>

          {uploadError && (
            <p className="mt-2 text-sm text-red-600">{uploadError}</p>
          )}

          <form onSubmit={handleSubmitProof} className="mt-5">
            <button
              type="submit"
              disabled={!screenshotFile || submitting}
              className="h-14 w-full rounded-xl text-base font-bold transition-opacity disabled:opacity-60"
              style={{
                backgroundColor: screenshotFile ? PAYMENT_HEADER_BG : '#E5E7EB',
                color: screenshotFile ? 'white' : '#9CA3AF',
              }}
            >
              {submitting ? 'Submitting…' : 'Submit Payment Proof'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  return null;
}
