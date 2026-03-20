import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { AuthTabs } from '../components/AuthTabs';
import { apiSendOtp, apiRegister } from '../api/endpoints';

export function SignupPage() {
  const auth = useAuth();
  const nav = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [referralCode, setReferralCode] = useState('');
  const [showReferral, setShowReferral] = useState(false);
  const [busy, setBusy] = useState(false);
  const [otpBusy, setOtpBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const phoneFull = phone.trim() ? (phone.trim().startsWith('+') ? phone.trim() : '+91' + phone.trim()) : '';

  const handleGetOtp = async () => {
    if (!phoneFull || phoneFull.length < 10) {
      setError('Please enter a valid phone number');
      return;
    }
    setError(null);
    setOtpBusy(true);
    try {
      await apiSendOtp(phoneFull);
      setError(null);
    } catch (e: any) {
      const msg = e?.response?.data?.detail || e?.response?.data?.message || 'Failed to send OTP';
      setError(String(msg));
    } finally {
      setOtpBusy(false);
    }
  };

  const onSubmit = async () => {
    setError(null);
    setBusy(true);
    try {
      const data: Record<string, string> = {
        username: username.trim(),
        password,
        phone_number: phoneFull,
        otp_code: otp.trim(),
      };
      if (referralCode.trim()) data.referral_code = referralCode.trim();
      const resp = await apiRegister(data);
      auth.setSession(resp.data);
      nav('/', { replace: true });
    } catch (e: any) {
      const msg = e?.response?.data?.detail || e?.response?.data?.message || 'Sign-up failed. Please try again.';
      setError(String(msg));
    } finally {
      setBusy(false);
    }
  };

  const canSubmit = username.trim() && password && phoneFull && otp.trim().length >= 4;

  return (
    <div className="mobile-frame min-h-dvh bg-appBg px-4 pt-6 pb-24">
      <AuthTabs current="signup" />
      <div className="mt-8">
        <Link to="/login" className="inline-flex items-center gap-1 text-textGrey">
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          Back
        </Link>
        <div className="mt-4 text-2xl font-extrabold text-[#FFCC00]">Sign up</div>
        <div className="mt-1 text-sm text-textGrey">Welcome to Gundu Ata</div>

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
          <div>
            <label className="mb-1 block text-sm font-medium text-textGrey">Phone number</label>
            <div className="flex gap-2">
              <span className="flex shrink-0 items-center rounded-xl border border-border bg-appBg px-4 py-3 text-textGrey">+91</span>
              <input
                className="input min-w-0 flex-1"
                value={phone}
                onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 15))}
                placeholder="Enter phone number"
                type="tel"
                inputMode="numeric"
                autoComplete="tel"
                maxLength={15}
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-textGrey">OTP Code</label>
            <div className="flex gap-2">
              <input
                className="input flex-1"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="Enter 4-digit OTP"
                maxLength={6}
              />
              <button
                type="button"
                onClick={handleGetOtp}
                disabled={otpBusy || !phone.trim()}
                className="shrink-0 rounded-xl border border-border bg-surface px-4 py-3 text-sm font-medium text-textWhite disabled:opacity-50"
              >
                {otpBusy ? '…' : 'Get OTP'}
              </button>
            </div>
          </div>
          <div>
            <button
              type="button"
              onClick={() => setShowReferral(!showReferral)}
              className="flex items-center gap-2 text-sm font-medium text-[#FFCC00]"
            >
              <span className={showReferral ? 'rotate-180' : ''}>
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </span>
              Have a referral code?
            </button>
            {showReferral && (
              <input
                className="input mt-2 w-full"
                value={referralCode}
                onChange={(e) => setReferralCode(e.target.value)}
                placeholder="Enter referral code"
              />
            )}
          </div>
        </div>

        {error ? <div className="mt-4 rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-sm text-error">{error}</div> : null}

        <button
          className="mt-6 w-full rounded-xl bg-surface py-4 text-base font-medium text-textWhite disabled:opacity-60"
          onClick={onSubmit}
          disabled={busy || !canSubmit}
        >
          {busy ? 'Signing up…' : 'Sign-up'}
        </button>
      </div>
    </div>
  );
}
