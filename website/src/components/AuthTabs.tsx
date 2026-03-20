import { Link } from 'react-router-dom';

/** Top switcher for auth pages: Login | Signup (same as APK) */
export function AuthTabs({ current }: { current: 'login' | 'signup' }) {
  return (
    <div className="flex w-full items-center justify-end gap-4 border-b border-border pb-3">
      <Link
        to="/login"
        className={`text-base font-semibold ${current === 'login' ? 'text-[#FFCC00]' : 'text-textGrey'}`}
      >
        Login
      </Link>
      <Link
        to="/signup"
        className={`rounded-full px-5 py-2 text-base font-semibold ${current === 'signup' ? 'bg-[#FFCC00] text-black' : 'border border-[#FFCC00] text-[#FFCC00]'}`}
      >
        Signup
      </Link>
    </div>
  );
}
