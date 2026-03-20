import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import {
  apiCheckLuckyDrawStatus,
  apiClaimLuckyDraw,
  apiWallet,
  type Wallet,
} from '../api/endpoints';

// Same 6 segments as Kotlin LuckyDrawScreen
const WHEEL_SEGMENTS = [
  { label: '₹10000', color: '#FF0000' },
  { label: '₹5000', color: '#FF4500' },
  { label: '₹1000', color: '#FFA500' },
  { label: '₹500', color: '#FFD700' },
  { label: '₹300', color: '#32CD32' },
  { label: '₹100', color: '#00CED1' },
];

function amountToIndex(amount: number): number {
  const map: Record<number, number> = {
    10000: 0, 5000: 1, 1000: 2, 500: 3, 300: 4, 100: 5,
  };
  return map[amount] ?? 0;
}

export function LuckyDrawPage() {
  const nav = useNavigate();
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [hasClaimed, setHasClaimed] = useState(false);
  const [claimedAmount, setClaimedAmount] = useState<string | null>(null);
  const [eligibleAmount, setEligibleAmount] = useState<number | null>(null);
  const [spinning, setSpinning] = useState(false);
  const [resultDialog, setResultDialog] = useState<{ message: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const segmentDeg = 360 / WHEEL_SEGMENTS.length;
  const segmentHalf = segmentDeg / 2;
  const rotationRef = useRef(0);
  const wheelRef = useRef<HTMLDivElement>(null);

  const fetchStatus = useCallback(async () => {
    try {
      const [statusRes, walletRes] = await Promise.all([
        apiCheckLuckyDrawStatus(),
        apiWallet(),
      ]);
      const status = statusRes.data;
      setWallet(walletRes.data ?? null);
      setHasClaimed(!!status?.claimed);
      const dep = status?.deposit_amount;
      setEligibleAmount(dep != null && dep > 0 ? dep : null);
      if (status?.claimed && status?.reward?.amount != null) {
        setClaimedAmount(String(status.reward.amount));
      }
    } catch {
      setHasClaimed(false);
      setEligibleAmount(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  useEffect(() => {
    if (!wheelRef.current) return;
    wheelRef.current.style.transition = 'none';
    wheelRef.current.style.transform = `rotate(${rotationRef.current}deg)`;
  }, [loading]);

  useEffect(() => {
    if (!wheelRef.current || !hasClaimed) return;
    const amount = claimedAmount ? parseInt(claimedAmount, 10) : NaN;
    const targetIndex = Number.isNaN(amount) ? 0 : amountToIndex(amount);
    const targetRotation = 270 - (targetIndex * segmentDeg + segmentHalf);
    rotationRef.current = targetRotation;
    wheelRef.current.style.transition = 'none';
    wheelRef.current.style.transform = `rotate(${targetRotation}deg)`;
  }, [hasClaimed, claimedAmount, segmentDeg, segmentHalf]);

  const performSpin = useCallback(async () => {
    if (spinning || hasClaimed || eligibleAmount == null || eligibleAmount <= 0) return;
    setSpinning(true);

    try {
      const res = await apiClaimLuckyDraw();
      const body = res.data;
      const reward = body?.lucky_draw ?? body?.reward;
      const amount = reward?.amount != null ? Number(reward.amount) : 0;
      const targetIndex = amountToIndex(amount);
      const lastResult = `₹${amount}`;

      const extraRotations = 10 + Math.floor(Math.random() * 5);
      const targetAngle = 270 - (targetIndex * segmentDeg + segmentHalf);
      const currentRotation = ((rotationRef.current % 360) + 360) % 360;
      const angleDiff = ((targetAngle - currentRotation) % 360 + 360) % 360;
      const totalRotation = rotationRef.current + extraRotations * 360 + angleDiff;

      if (wheelRef.current) {
        wheelRef.current.style.transition = 'transform 3.8s cubic-bezier(0.1, 0, 0.2, 1)';
        wheelRef.current.style.transform = `rotate(${totalRotation}deg)`;
        rotationRef.current = totalRotation;
      }

      setTimeout(() => {
        setSpinning(false);
        setHasClaimed(true);
        setClaimedAmount(String(amount));
        setResultDialog({
          message: `Congratulations! You won ${lastResult}`,
        });
        fetchStatus();
      }, 3800);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { detail?: string } } };
      const msg = err?.response?.data?.detail ?? 'Failed to claim. Try again.';
      setSpinning(false);
      setResultDialog({ message: msg });
    }
  }, [spinning, hasClaimed, eligibleAmount, fetchStatus, segmentDeg, segmentHalf]);

  const canSpin = !hasClaimed && eligibleAmount != null && eligibleAmount > 0;

  const conicGradient = WHEEL_SEGMENTS.map(
    (s, i) => `${s.color} ${i * segmentDeg}deg ${(i + 1) * segmentDeg}deg`
  ).join(', ');
  const conicFrom90 = `conic-gradient(from 90deg, ${conicGradient})`;

  if (loading) {
    return (
      <div className="mobile-frame flex min-h-dvh items-center justify-center bg-[#121212]">
        <div className="h-10 w-10 animate-spin rounded-full border-2 border-primaryYellow border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="mobile-frame min-h-dvh bg-[#121212]">
      <header className="sticky top-0 z-40 flex items-center justify-between border-b border-[#2a2a2a] bg-[#121212] px-4 py-4">
        <button
          type="button"
          onClick={() => nav(-1)}
          className="flex h-11 w-11 items-center justify-center rounded text-primaryYellow"
          aria-label="Back"
        >
          <BackArrow />
        </button>
        <div className="rounded-xl bg-[#1E1E1E] px-4 py-2">
          <span className="font-bold text-primaryYellow">₹ </span>
          <span className="font-bold text-white">{wallet?.balance ?? '0.00'}</span>
        </div>
      </header>

      <div className="flex flex-col items-center px-4 py-6">
        <h1 className="mb-8 text-center text-[28px] font-black uppercase tracking-wide text-white">
          {hasClaimed ? "REWARD CLAIMED!" : 'MEGA SPIN'}
        </h1>

        <div className="relative flex h-[360px] w-[360px] items-center justify-center">
          <div
            className="absolute rounded-full border-[20px]"
            style={{ width: 340, height: 340, borderColor: 'rgba(255, 204, 0, 0.35)' }}
          />
          <div
            ref={wheelRef}
            className="absolute rounded-full border-4 border-white"
            style={{
              width: 300,
              height: 300,
              background: conicFrom90,
              transition: spinning ? undefined : 'none',
              boxShadow: '0 0 0 2px rgba(0,0,0,0.15), inset 0 0 0 1px rgba(0,0,0,0.12)',
            }}
          >
            <svg className="absolute inset-0 h-full w-full overflow-visible" viewBox="0 0 300 300">
              {WHEEL_SEGMENTS.map((_, i) => {
                const angleDeg = 90 + i * segmentDeg;
                const angleRad = (angleDeg * Math.PI) / 180;
                const x2 = 150 + 148 * Math.sin(angleRad);
                const y2 = 150 - 148 * Math.cos(angleRad);
                return (
                  <line
                    key={i}
                    x1={150}
                    y1={150}
                    x2={x2}
                    y2={y2}
                    stroke="rgba(0,0,0,0.3)"
                    strokeWidth={1.5}
                  />
                );
              })}
            </svg>
            {WHEEL_SEGMENTS.map((s, i) => {
              const angleDeg = 90 + i * segmentDeg + segmentHalf;
              const angleRad = (angleDeg * Math.PI) / 180;
              const radiusPx = 105;
              const offsetXPx = radiusPx * Math.sin(angleRad);
              const offsetYPx = -radiusPx * Math.cos(angleRad);
              return (
                <span
                  key={i}
                  className="absolute left-1/2 top-1/2 origin-center text-center text-[13px] font-bold leading-tight text-white"
                  style={{
                    textShadow: '0 1px 2px rgba(0,0,0,0.9), 0 0 1px rgba(0,0,0,0.8)',
                    transform: `translate(calc(-50% + ${offsetXPx}px), calc(-50% + ${offsetYPx}px)) rotate(${angleDeg + 180}deg)`,
                  }}
                >
                  {s.label}
                </span>
              );
            })}
          </div>
          <div
            className="absolute left-1/2 top-[10px] z-10 -translate-x-1/2"
            style={{
              width: 0,
              height: 0,
              borderLeft: '15px solid transparent',
              borderRight: '15px solid transparent',
              borderTop: '30px solid #FF0000',
            }}
          />
          <button
            type="button"
            disabled={spinning || !canSpin}
            onClick={performSpin}
            className="relative z-20 flex h-[60px] w-[60px] items-center justify-center rounded-full border-4 border-white text-sm font-black shadow-[0_4px_12px_rgba(0,0,0,0.45)] transition-opacity disabled:cursor-not-allowed disabled:opacity-70"
            style={{ backgroundColor: '#FFCC00', color: '#000' }}
          >
            SPIN
          </button>
        </div>

        <div className="mt-[60px] flex w-full max-w-[360px] flex-col items-center gap-4">
          <button
            type="button"
            disabled={spinning || !canSpin}
            onClick={performSpin}
            className="h-[60px] w-full rounded-full py-4 text-xl font-extrabold transition-opacity disabled:cursor-not-allowed disabled:opacity-70"
            style={{
              backgroundColor: hasClaimed || !canSpin ? '#555' : '#FFCC00',
              color: hasClaimed || !canSpin ? '#fff' : '#000',
            }}
          >
            {hasClaimed ? "REWARD CLAIMED" : spinning ? 'SPINNING...' : canSpin ? 'SPIN NOW' : 'DEPOSIT TO SPIN'}
          </button>
          <p className="mt-6 text-center leading-relaxed text-[#9E9E9E]" style={{ fontSize: 14 }}>
            {hasClaimed
              ? "You've already claimed your mega spin reward! Make another deposit to try again."
              : canSpin
                ? 'Deposit ₹2000 or more to unlock one free mega spin!'
                : 'Deposit ₹2000 or more to spin the wheel and win up to ₹10,000!'}
          </p>
          {!canSpin && !hasClaimed && (
            <Link
              to="/deposit"
              className="mt-2 rounded-full bg-primaryYellow px-6 py-3 font-bold text-black"
            >
              DEPOSIT NOW
            </Link>
          )}
        </div>
      </div>

      {resultDialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          onClick={() => setResultDialog(null)}
          role="dialog"
          aria-modal="true"
        >
          <div
            className="max-w-sm rounded-2xl bg-[#2a2a2a] p-6 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-lg font-bold text-primaryYellow">Lucky Draw Result</h3>
            <p className="mt-2 text-white">{resultDialog.message}</p>
            <button
              type="button"
              onClick={() => setResultDialog(null)}
              className="mt-4 w-full rounded-xl bg-primaryYellow py-3 font-semibold text-black"
            >
              OK
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
