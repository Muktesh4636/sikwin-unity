import { Link, useSearchParams } from 'react-router-dom';
import { getRefParam, withRefQuery } from '../utils/referralLink';

/** Top switcher for auth pages: Login | Signup (same as APK). Preserves ?ref= for referral invites. */
export function AuthTabs({ current }: { current: 'login' | 'signup' }) {
  const [searchParams] = useSearchParams();
  const ref = getRefParam(searchParams);
  const loginTo = withRefQuery('/login', ref);
  const signupTo = withRefQuery('/signup', ref);

  return (
    <div className="flex w-full items-center justify-end gap-4 border-b border-border pb-3">
      <Link
        to={loginTo}
        className={`text-base font-semibold ${current === 'login' ? 'text-[#FFCC00]' : 'text-textGrey'}`}
      >
        Login
      </Link>
      <Link
        to={signupTo}
        className={`rounded-full px-5 py-2 text-base font-semibold ${current === 'signup' ? 'bg-[#FFCC00] text-black' : 'border border-[#FFCC00] text-[#FFCC00]'}`}
      >
        Signup
      </Link>
    </div>
  );
}
