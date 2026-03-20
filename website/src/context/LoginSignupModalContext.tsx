import React, { createContext, useCallback, useContext, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslations } from './LocaleContext';

type LoginSignupModalContextValue = {
  showLoginSignupModal: () => void;
};

const Ctx = createContext<LoginSignupModalContextValue | null>(null);

export function useLoginSignupModal() {
  const v = useContext(Ctx);
  if (!v) throw new Error('useLoginSignupModal must be used within LoginSignupModalProvider');
  return v;
}

export function LoginSignupModalProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const t = useTranslations();
  const showLoginSignupModal = useCallback(() => setOpen(true), []);

  return (
    <Ctx.Provider value={{ showLoginSignupModal }}>
      {children}
      {open && (
        <div
          className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/70 p-4"
          onClick={() => setOpen(false)}
          role="dialog"
          aria-modal="true"
          aria-labelledby="login-signup-modal-title"
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-[#2a2a2a] p-6 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 id="login-signup-modal-title" className="text-lg font-bold text-textWhite">
              {t('login_signup_to_play')}
            </h2>
            <div className="mt-6 flex flex-col gap-3">
              <Link
                to="/login"
                className="w-full rounded-xl bg-primaryYellow py-3 text-center font-semibold text-black"
                onClick={() => setOpen(false)}
              >
                {t('login')}
              </Link>
              <Link
                to="/signup"
                className="w-full rounded-xl border-2 border-primaryYellow py-3 text-center font-semibold text-primaryYellow"
                onClick={() => setOpen(false)}
              >
                {t('register')}
              </Link>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="w-full rounded-xl bg-surface py-3 font-semibold text-textWhite"
              >
                {t('cancel')}
              </button>
            </div>
          </div>
        </div>
      )}
    </Ctx.Provider>
  );
}
