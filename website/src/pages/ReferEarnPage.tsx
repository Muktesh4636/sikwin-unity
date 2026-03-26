import { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { useAuth } from '../auth/AuthContext';
import { apiReferralData, type ReferralData as ReferralDataType } from '../api/endpoints';

function GiftIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 12 20 22 4 22 4 12" />
      <rect x="2" y="7" width="20" height="5" />
      <line x1="12" y1="22" x2="12" y2="7" />
      <path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z" />
      <path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z" />
    </svg>
  );
}

function CopyIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </svg>
  );
}

function ShareIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="18" cy="5" r="3" />
      <circle cx="6" cy="12" r="3" />
      <circle cx="18" cy="19" r="3" />
      <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
      <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
    </svg>
  );
}

function WhatsAppIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413z" />
    </svg>
  );
}

function PeopleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}

function CheckIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M20 6L9 17l-5-5" />
    </svg>
  );
}

/** Same as Kotlin APK Icons.Default.AccountBalanceWallet */
function WalletIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor">
      <path d="M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" />
    </svg>
  );
}

// Milestone tiers shown in the website UI.
// Per your new rules: 3 -> ₹500, 12 -> ₹2400, 20 -> ₹4000.
// If you also have a 4th tier, tell me the referral count and reward and I’ll add it.
const MILESTONES: { count: number; reward: string; description?: string }[] = [
  { count: 3, reward: '₹500', description: 'Reward for 3 referrals' },
  { count: 12, reward: '₹2400', description: 'Reward for 12 referrals' },
  { count: 20, reward: '₹4000', description: 'Reward for 20 referrals' },
];

const HOW_IT_WORKS = [
  'Share your code with friends',
  'Friend registers & deposits ₹100+',
  'You get ₹100 instantly!',
];

export function ReferEarnPage() {
  const nav = useNavigate();
  const { user } = useAuth();
  const [showAllMilestones, setShowAllMilestones] = useState(false);
  const [referralData, setReferralData] = useState<ReferralDataType | null>(null);

  const referralCode = referralData?.referral_code || user?.referral_code || '';

  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  useEffect(() => {
    apiReferralData()
      .then((r) => setReferralData(r.data ?? null))
      .catch(() => setReferralData(null));
  }, []);

  const copyCode = useCallback(() => {
    navigator.clipboard.writeText(referralCode).catch(() => {});
  }, [referralCode]);

  const shareWhatsApp = useCallback(() => {
    const text = encodeURIComponent(`Use my referral code ${referralCode} on Sikwin!`);
    window.open(`https://wa.me/?text=${text}`, '_blank', 'noopener');
  }, [referralCode]);

  const shareNative = useCallback(() => {
    const text = `Use my referral code ${referralCode} on Sikwin!`;
    if (navigator.share) {
      navigator.share({ title: 'Refer & Earn', text }).catch(() => {});
    } else {
      navigator.clipboard.writeText(text).catch(() => {});
    }
  }, [referralCode]);

  const totalReferrals = referralData?.total_referrals ?? 0;
  const depositedCount = referralData?.referrals?.filter((r) => r.has_deposit).length ?? 0;
  const totalEarned = referralData?.total_earnings ?? '0';
  const nextMilestoneFromApi = referralData?.next_milestone;
  const nextMilestoneCount = nextMilestoneFromApi?.next_milestone ?? MILESTONES[0].count;
  const nextMilestoneMatch = MILESTONES.find((m) => m.count === nextMilestoneCount) ?? MILESTONES[0];
  const nextMilestoneReward =
    nextMilestoneFromApi?.next_bonus_display ??
    (typeof nextMilestoneFromApi?.next_bonus === 'number' ? `₹${nextMilestoneFromApi.next_bonus}` : nextMilestoneMatch.reward);
  const currentProgress = nextMilestoneFromApi?.current_progress ?? 0;

  return (
    <div className="mobile-frame min-h-dvh bg-[#121212]">
      <header className="sticky top-0 z-40 bg-[#121212]">
        <div className="mx-auto flex max-w-[460px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            className="flex h-11 w-11 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
            onClick={() => nav(-1)}
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-[#FFCC00]">Refer & Earn</h1>
          <div className="w-11" />
        </div>
      </header>

      <div className="mx-auto max-w-[460px] px-4 pb-24 pt-2">
        {/* Invite section – gradient area */}
        <div
          className="rounded-2xl px-4 pb-6 pt-6"
          style={{ background: 'linear-gradient(180deg, rgba(218,165,32,0.15) 0%, rgba(18,18,18,0) 100%)' }}
        >
          <div className="flex flex-col items-center">
            <div className="flex h-16 w-16 items-center justify-center text-[#FFCC00]">
              <GiftIcon className="h-14 w-14" />
            </div>
            <h2 className="mt-3 text-xl font-bold text-white">Invite Friends & Win!</h2>
          </div>
        </div>

        {/* Referral code card */}
        <div className="mt-4 rounded-2xl bg-[#1E1E1E] px-4 py-4">
          <div className="text-xs font-medium uppercase tracking-wide text-[#BDBDBD]">Your Referral Code</div>
          <div className="mt-2 flex items-center justify-between gap-2">
            <span className="text-lg font-bold text-[#FFCC00]">{referralCode}</span>
            <button
              type="button"
              onClick={copyCode}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
              aria-label="Copy"
            >
              <CopyIcon className="h-5 w-5" />
            </button>
          </div>
          <div className="mt-4 flex gap-3">
            <button
              type="button"
              onClick={shareWhatsApp}
              className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-[#25D366] py-3 text-sm font-semibold text-white transition-opacity active:opacity-90"
            >
              <WhatsAppIcon className="h-5 w-5" />
              WhatsApp
            </button>
            <button
              type="button"
              onClick={shareNative}
              className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-[#FFCC00] py-3 text-sm font-semibold text-black transition-opacity active:opacity-90"
            >
              <ShareIcon className="h-5 w-5" />
              Share
            </button>
          </div>
        </div>

        {/* How it works */}
        <div className="mt-6">
          <h3 className="mb-3 text-base font-bold text-white">How it works</h3>
          <div className="rounded-2xl bg-[#1E1E1E] px-4 py-4">
            {HOW_IT_WORKS.map((text, i) => (
              <div key={i} className="flex items-center gap-3 py-2 first:pt-0 last:pb-0">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#FFCC00] text-sm font-bold text-black">
                  {i + 1}
                </div>
                <span className="text-sm text-white">{text}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Milestone bonuses */}
        <div className="mt-6">
          <h3 className="mb-3 text-base font-bold text-white">Milestone bonuses</h3>
          {!showAllMilestones ? (
            <>
              <div className="rounded-2xl bg-[#1E1E1E] px-4 py-4">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-xs text-[#BDBDBD]">Next reward</div>
                    <div className="mt-1 text-xl font-bold text-[#FFCC00]">{nextMilestoneReward}</div>
                  </div>
                  <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full border-2 border-[#4a4a4a] text-sm font-medium text-white">
                    {currentProgress}/{nextMilestoneCount}
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowAllMilestones(true)}
                className="mt-4 w-full text-center text-sm font-semibold text-[#FFCC00]"
              >
                View All
              </button>
            </>
          ) : (
            <>
              <div className="space-y-3">
                {MILESTONES.map((m) => (
                  <div
                    key={m.count}
                    className="flex items-center gap-4 rounded-2xl bg-[#1E1E1E] px-4 py-3"
                  >
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border-2 border-[#4a4a4a] text-sm font-medium text-white">
                      0/{m.count}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold text-white">{m.count} Referrals</div>
                      <div className="text-xs text-[#BDBDBD]">
                        {m.description ?? `0 / ${m.count} Referrals`}
                      </div>
                    </div>
                    <div className="shrink-0 text-right font-bold text-white">{m.reward}</div>
                  </div>
                ))}
              </div>
              <button
                type="button"
                onClick={() => setShowAllMilestones(false)}
                className="mt-4 w-full text-center text-sm font-semibold text-[#FFCC00]"
              >
                View Less
              </button>
            </>
          )}
        </div>

        {/* Summary cards */}
        <div className="mt-6 grid grid-cols-2 gap-3">
          <div className="flex flex-col items-center justify-center rounded-2xl bg-[#1E1E1E] px-4 py-5">
            <PeopleIcon className="h-8 w-8 text-[#FFCC00]" />
            <div className="mt-2 text-2xl font-bold text-white">{totalReferrals}</div>
            <div className="mt-1 text-xs text-[#BDBDBD]">Total Referrals</div>
          </div>
          <div className="flex flex-col items-center justify-center rounded-2xl bg-[#1E1E1E] px-4 py-5">
            <CheckIcon className="h-8 w-8 text-[#4CAF50]" />
            <div className="mt-2 text-2xl font-bold text-white">{depositedCount}</div>
            <div className="mt-1 text-xs text-[#BDBDBD]">Deposited (counts)</div>
          </div>
        </div>
        <div className="mt-6 rounded-2xl bg-[#1E1E1E] px-4 py-6">
          <div className="flex flex-col items-center justify-center">
            <WalletIcon className="h-8 w-8 text-[#FFCC00]" />
            <div className="mt-2 text-2xl font-bold text-white">₹{totalEarned}</div>
            <div className="mt-1 text-xs text-[#BDBDBD]">Total Earned</div>
          </div>
        </div>
      </div>
    </div>
  );
}
