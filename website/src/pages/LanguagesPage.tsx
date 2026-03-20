import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { useLocale, useTranslations, type LocaleTag } from '../context/LocaleContext';

const LANGUAGES: { tag: LocaleTag; nameKey: string }[] = [
  { tag: 'en', nameKey: 'english' },
  { tag: 'te', nameKey: 'telugu' },
  { tag: 'hi', nameKey: 'hindi' },
];

export function LanguagesPage() {
  const nav = useNavigate();
  const { locale, setLocale } = useLocale();
  const t = useTranslations();

  return (
    <div className="mobile-frame min-h-dvh bg-[#121212]">
      <header className="sticky top-0 z-40 bg-[#121212]/95 backdrop-blur">
        <div className="relative flex max-w-[460px] items-center px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
            aria-label={t('back')}
          >
            <BackArrow />
          </button>
          <h1 className="pointer-events-none absolute left-1/2 top-1/2 w-full -translate-x-1/2 -translate-y-1/2 text-center text-xl font-medium text-[#FFCC00]">
            {t('languages')}
          </h1>
          <div className="w-10" />
        </div>
      </header>

      <div className="px-4 pt-2 pb-8">
        <p className="px-0 py-2 text-sm text-[#BDBDBD]">
          {t('choose_language')}
        </p>

        <div className="mt-2 overflow-hidden rounded-xl bg-[#1E1E1E]">
          {LANGUAGES.map((option) => {
            const isSelected = locale === option.tag;
            return (
              <button
                key={option.tag}
                type="button"
                onClick={() => setLocale(option.tag)}
                className="flex w-full items-center justify-between px-4 py-4 text-left transition-colors hover:bg-[#252525] active:bg-[#2a2a2a]"
              >
                <span className="text-base text-white">{t(option.nameKey)}</span>
                {isSelected && (
                  <svg
                    className="h-6 w-6 shrink-0 text-[#FFCC00]"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                    aria-hidden
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M5 13l4 4L19 7"
                    />
                  </svg>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
