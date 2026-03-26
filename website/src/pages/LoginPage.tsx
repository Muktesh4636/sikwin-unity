import { useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { AuthTabs } from '../components/AuthTabs';
import { APP_NAME } from '../config';

export function LoginPage() {
  const auth = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const from = useMemo(() => (loc.state as any)?.from?.toString() || '/', [loc.state]);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    setBusy(true);
    try {
      await auth.login(username.trim(), password);
      nav(from, { replace: true });
    } catch (e: any) {
      const msg = e?.response?.data?.detail || e?.response?.data?.message || 'Login failed. Please try again.';
      setError(String(msg));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mobile-frame min-h-dvh bg-appBg px-4 pt-6 pb-24">
      <AuthTabs current="login" />
      <div className="mt-8">
        <div className="text-2xl font-extrabold text-[#FFCC00]">{APP_NAME}</div>
        <div className="mt-2 text-2xl font-bold text-textWhite">Welcome back</div>
        <div className="mt-1 text-sm text-textGrey">Please enter your username and password to log in</div>

        <div className="mt-6 space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-textGrey">Username</label>
            <input
              className="input w-full"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Please enter your username"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-textGrey">Password</label>
            <input
              className="input w-full"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              type="password"
            />
          </div>
        </div>

        {error ? <div className="mt-4 rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-sm text-error">{error}</div> : null}

        <button
          className="mt-6 w-full rounded-xl bg-surface py-4 text-base font-medium text-textWhite disabled:opacity-60"
          onClick={onSubmit}
          disabled={busy || !username.trim() || !password}
        >
          {busy ? 'Signing in…' : 'Sign-in'}
        </button>

        <div className="mt-4 text-center text-sm text-textGrey">
          <Link className="text-[#FFCC00] font-medium" to="/forgot-password">Forgot password</Link>
        </div>

        <Link
          to="/signup"
          className="mt-12 flex w-full items-center justify-center rounded-xl border-2 border-[#FFCC00] bg-transparent py-4 text-base font-semibold text-[#FFCC00]"
        >
          Sign-up
        </Link>
      </div>
    </div>
  );
}
