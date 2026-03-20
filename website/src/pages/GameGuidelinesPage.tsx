import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { useTranslations } from '../context/LocaleContext';

const BG = '#121212';

function RuleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M14.5 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V7.5L14.5 2zM6 20V4h7v5h5v11H6zm2-2h8v-2H8v2zm0-4h8v-2H8v2zm0-4h5v-2H8v2z" />
    </svg>
  );
}

function TrendIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6h-6z" />
    </svg>
  );
}

function BulbIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z" />
    </svg>
  );
}

function InfoIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" />
    </svg>
  );
}

function DiceIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M5 3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2H5zm0 2h14v14H5V5zm2.5 3a1.5 1.5 0 100 3 1.5 1.5 0 000-3zm7 0a1.5 1.5 0 100 3 1.5 1.5 0 000-3zm-7 7a1.5 1.5 0 100 3 1.5 1.5 0 000-3zm7 0a1.5 1.5 0 100 3 1.5 1.5 0 000-3z" />
    </svg>
  );
}

function GuidelineSection({
  title,
  icon,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div className="flex items-center gap-3">
        <span className="text-primaryYellow [&>svg]:h-6 [&>svg]:w-6">{icon}</span>
        <h2 className="text-xl font-bold text-white">{title}</h2>
      </div>
      <div className="mt-4 space-y-2">{children}</div>
    </div>
  );
}

function GuidelineItem({
  number,
  title,
  description,
}: {
  number: string;
  title: string;
  description: string;
}) {
  return (
    <div className="rounded-xl bg-[#1E1E1E] p-4">
      <div className="flex gap-4">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primaryYellow/20 text-base font-bold text-primaryYellow">
          {number}
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="text-base font-bold text-white">{title}</h3>
          <p className="mt-1 text-sm leading-relaxed text-[#BDBDBD]">{description}</p>
        </div>
      </div>
    </div>
  );
}

export function GameGuidelinesPage() {
  const nav = useNavigate();
  const t = useTranslations();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  return (
    <div className="mobile-frame min-h-dvh pb-10" style={{ backgroundColor: BG }}>
      <header className="sticky top-0 z-40 flex items-center gap-3 px-4 py-4" style={{ backgroundColor: BG }}>
        <button
          type="button"
          onClick={() => nav(-1)}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-white transition-opacity hover:opacity-90"
          aria-label={t('back')}
        >
          <span className="text-white"><BackArrow className="h-7 w-7" /></span>
        </button>
        <h1 className="flex-1 text-center text-xl font-bold text-white">{t('game_guidelines_title')}</h1>
        <div className="w-11" />
      </header>

      <div className="mx-auto max-w-[460px] space-y-6 px-4">
        {/* Hero Section — match APK */}
        <div className="rounded-2xl bg-[#1E1E1E] p-6 text-center">
          <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-2xl bg-primaryYellow/20">
            <DiceIcon className="h-10 w-10 text-primaryYellow" />
          </div>
          <h2 className="mt-4 text-2xl font-bold text-white">{t('how_to_play')}</h2>
          <p className="mt-2 text-sm text-[#BDBDBD]">{t('learn_rules')}</p>
        </div>

        {/* Game Rules Section */}
        <GuidelineSection title={t('game_rules')} icon={<RuleIcon />}>
          <GuidelineItem number="1" title={t('dice_game_basics')} description={t('dice_game_basics_desc')} />
          <GuidelineItem number="2" title={t('winning_conditions')} description={t('winning_conditions_desc')} />
          <GuidelineItem number="3" title={t('payout_calculation')} description={t('payout_calculation_desc')} />
          <GuidelineItem number="4" title={t('no_winners')} description={t('no_winners_desc')} />
        </GuidelineSection>

        {/* Betting Strategy Section */}
        <GuidelineSection title={t('betting_strategy')} icon={<TrendIcon />}>
          <GuidelineItem number="1" title={t('start_small')} description={t('start_small_desc')} />
          <GuidelineItem number="2" title={t('diversify_bets')} description={t('diversify_bets_desc')} />
          <GuidelineItem number="3" title={t('watch_patterns')} description={t('watch_patterns_desc')} />
          <GuidelineItem number="4" title={t('set_limits')} description={t('set_limits_desc')} />
        </GuidelineSection>

        {/* Pro Tips Section */}
        <GuidelineSection title={t('pro_tips')} icon={<BulbIcon />}>
          <GuidelineItem number="•" title={t('timing_matters')} description={t('timing_matters_desc')} />
          <GuidelineItem number="•" title={t('check_balance')} description={t('check_balance_desc')} />
          <GuidelineItem number="•" title={t('review_history')} description={t('review_history_desc')} />
          <GuidelineItem number="•" title={t('stay_updated')} description={t('stay_updated_desc')} />
        </GuidelineSection>

        {/* Important Notes — yellow tint like APK */}
        <div className="rounded-2xl border border-primaryYellow/30 bg-primaryYellow/10 p-5">
          <div className="flex gap-3">
            <InfoIcon className="h-6 w-6 shrink-0 text-primaryYellow" />
            <div>
              <h3 className="text-base font-bold text-primaryYellow">{t('important_notes')}</h3>
              <p
                className="mt-2 whitespace-pre-line text-sm leading-relaxed text-white"
                style={{ lineHeight: 1.5 }}
              >
                {t('important_notes_content')}
              </p>
            </div>
          </div>
        </div>

        <div className="h-8" />
      </div>
    </div>
  );
}
