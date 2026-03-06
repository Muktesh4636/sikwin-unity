import { useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const auth = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const from = useMemo(() => (loc.state as any)?.from?.toString() || '/home', [loc.state]);

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
    <div className="mobile-frame app-shell px-4 pt-10">
      <div className="card p-5">
        <div className="text-primaryYellow text-sm font-black tracking-widest">GUNDU ATA</div>
        <div className="mt-2 text-2xl font-extrabold">Login</div>
        <div className="mt-1 text-textGrey text-sm">Use the same credentials as the APK.</div>

        <div className="mt-6 space-y-3">
          <div>
            <div className="mb-1 text-xs font-bold text-textGrey">Username / Phone</div>
            <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="9182351381" />
          </div>
          <div>
            <div className="mb-1 text-xs font-bold text-textGrey">Password</div>
            <input
              className="input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="•••••"
              type="password"
            />
          </div>
        </div>

        {error ? <div className="mt-4 rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-sm">{error}</div> : null}

        <button className="btn-primary mt-5 w-full" onClick={onSubmit} disabled={busy || !username.trim() || !password}>
          {busy ? 'Logging in…' : 'Login'}
        </button>

        <div className="mt-4 text-center text-sm text-textGrey">
          Don’t have an account?{' '}
          <Link className="text-primaryYellow font-bold" to="/signup">
            Sign Up
          </Link>
        </div>
      </div>
    </div>
  );
}

