import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiProfile, apiUpdateProfile, type User } from '../api/endpoints';

const rowClass =
  'flex w-full items-center justify-between gap-4 px-4 py-[18px] text-[15px]';
const labelClass = 'text-[#BDBDBD] flex-1';
const valueClass = 'text-[#8E8E8E] text-right';
const dividerClass = 'h-px w-full bg-[#333]';

export function SecurityPage() {
  const nav = useNavigate();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [showEmailDialog, setShowEmailDialog] = useState(false);
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [emailValue, setEmailValue] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadProfile = () => {
    setLoading(true);
    apiProfile()
      .then((r) => setUser(r.data ?? null))
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadProfile();
  }, []);

  const openEmailDialog = () => {
    setEmailValue(user?.email ?? '');
    setError(null);
    setShowEmailDialog(true);
  };

  const saveEmail = async () => {
    if (!emailValue.trim()) return;
    setSaving(true);
    setError(null);
    try {
      await apiUpdateProfile({ email: emailValue.trim() });
      setUser((prev) => (prev ? { ...prev, email: emailValue.trim() } : null));
      setShowEmailDialog(false);
    } catch (e: any) {
      const msg =
        e?.response?.data?.email?.[0] ||
        e?.response?.data?.detail ||
        (typeof e?.response?.data === 'object' ? JSON.stringify(e.response.data) : null) ||
        e?.message ||
        'Could not update email.';
      setError(typeof msg === 'string' ? msg : 'Could not update email.');
    } finally {
      setSaving(false);
    }
  };

  const openPasswordDialog = () => {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setError(null);
    setShowPasswordDialog(true);
  };

  const savePassword = async () => {
    if (!currentPassword || !newPassword || !confirmPassword) {
      setError('Please fill all fields.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('New passwords do not match.');
      return;
    }
    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await apiUpdateProfile({
        current_password: currentPassword,
        new_password: newPassword,
      });
      setShowPasswordDialog(false);
    } catch (e: any) {
      const msg =
        e?.response?.data?.detail ||
        e?.response?.data?.current_password?.[0] ||
        e?.response?.data?.new_password?.[0] ||
        (typeof e?.response?.data === 'object' ? JSON.stringify(e.response.data) : null) ||
        e?.message ||
        'Could not change password.';
      setError(typeof msg === 'string' ? msg : 'Could not change password.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mobile-frame min-h-dvh bg-[#121212]">
      <header className="sticky top-0 z-40 bg-[#121212]/95 backdrop-blur">
        <div className="relative flex max-w-[430px] items-center px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="absolute left-1/2 top-1/2 w-full -translate-x-1/2 -translate-y-1/2 text-center text-xl font-medium text-[#FFCC00]">
            Security
          </h1>
          <div className="w-10" />
        </div>
      </header>

      <div className="px-0 pt-2 pb-8">
        {loading ? (
          <div className="flex justify-center py-12">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#FFCC00] border-t-transparent" />
          </div>
        ) : (
          <div className="flex flex-col">
            <button type="button" onClick={openEmailDialog} className="w-full text-left">
              <div className={rowClass}>
                <span className={labelClass}>Email</span>
                <span className={`${valueClass} flex items-center justify-end gap-1`}>
                  {user?.email || '—'}
                  <span className="text-[#555]">›</span>
                </span>
              </div>
            </button>
            <div className={dividerClass} />

            <button type="button" onClick={openPasswordDialog} className="w-full text-left">
              <div className={rowClass}>
                <span className={labelClass}>Password</span>
                <span className={`${valueClass} flex items-center justify-end gap-1`}>
                  Change
                  <span className="text-[#555]">›</span>
                </span>
              </div>
            </button>
            <div className={dividerClass} />

            <div className={rowClass}>
              <span className={labelClass}>Real name</span>
              <span className={valueClass}>{user?.username ?? '—'}</span>
            </div>
            <div className={dividerClass} />
          </div>
        )}
      </div>

      {/* Edit Email dialog */}
      {showEmailDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-[340px] rounded-2xl bg-[#2a2520] p-5 shadow-xl">
            <h2 className="text-lg font-semibold text-white">Edit Email</h2>
            <div className="mt-4">
              <label className="mb-1.5 block text-sm text-[#BDBDBD]">New Email</label>
              <input
                type="email"
                value={emailValue}
                onChange={(e) => setEmailValue(e.target.value)}
                placeholder="Enter email"
                className="w-full rounded-xl border border-[#444] bg-[#1a1816] px-4 py-3 text-white placeholder:text-[#666] focus:border-[#FFCC00] focus:outline-none focus:ring-1 focus:ring-[#FFCC00]"
              />
            </div>
            {error && <p className="mt-3 text-sm text-red-400">{error}</p>}
            <div className="mt-6 flex gap-3">
              <button
                type="button"
                onClick={() => { setShowEmailDialog(false); setError(null); }}
                className="flex-1 rounded-xl py-3 text-base font-medium text-white"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={saveEmail}
                disabled={saving || !emailValue.trim()}
                className="flex-1 rounded-xl bg-[#FFCC00] py-3 text-base font-bold text-black disabled:opacity-50"
              >
                {saving ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Change Password dialog */}
      {showPasswordDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-[340px] rounded-2xl bg-[#2a2520] p-5 shadow-xl">
            <h2 className="text-lg font-semibold text-white">Change Password</h2>
            <div className="mt-4 space-y-4">
              <div>
                <label className="mb-1.5 block text-sm text-[#BDBDBD]">Current password</label>
                <input
                  type="password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  placeholder="Enter current password"
                  className="w-full rounded-xl border border-[#444] bg-[#1a1816] px-4 py-3 text-white placeholder:text-[#666] focus:border-[#FFCC00] focus:outline-none focus:ring-1 focus:ring-[#FFCC00]"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm text-[#BDBDBD]">New password</label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Enter new password"
                  className="w-full rounded-xl border border-[#444] bg-[#1a1816] px-4 py-3 text-white placeholder:text-[#666] focus:border-[#FFCC00] focus:outline-none focus:ring-1 focus:ring-[#FFCC00]"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm text-[#BDBDBD]">Confirm new password</label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Confirm new password"
                  className="w-full rounded-xl border border-[#444] bg-[#1a1816] px-4 py-3 text-white placeholder:text-[#666] focus:border-[#FFCC00] focus:outline-none focus:ring-1 focus:ring-[#FFCC00]"
                />
              </div>
            </div>
            {error && <p className="mt-3 text-sm text-red-400">{error}</p>}
            <div className="mt-6 flex gap-3">
              <button
                type="button"
                onClick={() => { setShowPasswordDialog(false); setError(null); }}
                className="flex-1 rounded-xl py-3 text-base font-medium text-white"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={savePassword}
                disabled={saving}
                className="flex-1 rounded-xl bg-[#FFCC00] py-3 text-base font-bold text-black disabled:opacity-50"
              >
                {saving ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
