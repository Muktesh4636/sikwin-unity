import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { t as getT } from '../translations';

const STORAGE_KEY = 'app_language';

export type LocaleTag = 'en' | 'te' | 'hi';

type LocaleContextValue = {
  locale: LocaleTag;
  setLocale: (tag: LocaleTag) => void;
};

const defaultLocale: LocaleTag = 'en';

function loadStoredLocale(): LocaleTag {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'en' || stored === 'te' || stored === 'hi') return stored;
  } catch (_) {}
  return defaultLocale;
}

function applyLocale(tag: LocaleTag) {
  try {
    document.documentElement.lang = tag === 'te' ? 'te' : tag === 'hi' ? 'hi' : 'en';
  } catch (_) {}
}

const LocaleContext = createContext<LocaleContextValue | null>(null);

export function LocaleProvider({ children }: { children: React.ReactNode }) {
  const [locale, setLocaleState] = useState<LocaleTag>(loadStoredLocale);

  const setLocale = useCallback((tag: LocaleTag) => {
    setLocaleState(tag);
    try {
      localStorage.setItem(STORAGE_KEY, tag);
    } catch (_) {}
    applyLocale(tag);
  }, []);

  useEffect(() => {
    applyLocale(locale);
  }, [locale]);

  return (
    <LocaleContext.Provider value={{ locale, setLocale }}>
      {children}
    </LocaleContext.Provider>
  );
}

export function useLocale() {
  const ctx = useContext(LocaleContext);
  if (!ctx) {
    return {
      locale: defaultLocale as LocaleTag,
      setLocale: (_tag: LocaleTag) => {},
    };
  }
  return ctx;
}

/** Translation function for current locale. Use for all user-facing strings. */
export function useTranslations() {
  const { locale } = useLocale();
  return useCallback(
    (key: string) => getT(locale, key),
    [locale]
  );
}
