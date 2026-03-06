import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiProfile, apiUpdateProfile, type User } from '../api/endpoints';

const rowClass =
  'flex w-full items-center justify-between gap-4 px-4 py-[18px] text-[15px]';
const labelClass = 'text-[#BDBDBD] flex-1';
const valueClass = 'text-[#8E8E8E] text-right';
const dividerClass = 'h-px w-full bg-[#2C2C2C]';

type EditableField = 'gender' | 'email' | 'telegram' | 'date_of_birth';

const FIELD_LABELS: Record<EditableField, string> = {
  gender: 'Gender',
  email: 'Email',
  telegram: 'Telegram',
  date_of_birth: 'Date of Birth',
};

const API_KEYS: Record<EditableField, string> = {
  gender: 'gender',
  email: 'email',
  telegram: 'telegram',
  date_of_birth: 'date_of_birth',
};

export function PersonalDataPage() {
  const nav = useNavigate();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [editField, setEditField] = useState<EditableField | null>(null);
  const [editValue, setEditValue] = useState('');
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

  const openEdit = (field: EditableField) => {
    const raw = field === 'telegram'
      ? (user?.telegram ?? '')
      : field === 'gender'
        ? (user?.gender ?? '')
        : field === 'email'
          ? (user?.email ?? '')
          : (user?.date_of_birth ?? '');
    setEditValue(raw);
    setEditField(field);
    setError(null);
  };

  const saveEdit = async () => {
    if (!editField) return;
    setSaving(true);
    setError(null);
    const key = API_KEYS[editField];
    const value = editField === 'gender' ? editValue.toUpperCase() : editValue.trim();
    try {
      await apiUpdateProfile({ [key]: value });
      setUser((prev) => (prev ? { ...prev, [key]: value } : null));
      setEditField(null);
    } catch (e: any) {
      const msg =
        e?.response?.data?.detail ||
        e?.response?.data?.email?.[0] ||
        (typeof e?.response?.data === 'object' ? JSON.stringify(e.response.data) : null) ||
        e?.message ||
        'Could not update. Please try again.';
      setError(typeof msg === 'string' ? msg : 'Could not update.');
    } finally {
      setSaving(false);
    }
  };

  const displayTelegram = () => {
    const t = user?.telegram;
    if (!t) return '—';
    return t.startsWith('@') ? t : `@${t}`;
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
          <h1 className="absolute left-1/2 top-1/2 w-full -translate-x-1/2 -translate-y-1/2 text-center text-lg font-medium text-[#FFCC00]">
            My Information
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
            {/* Name - not editable */}
            <div className={rowClass}>
              <span className={labelClass}>Name</span>
              <span className={valueClass}>{user?.username ?? '—'}</span>
            </div>
            <div className={dividerClass} />

            <button type="button" onClick={() => openEdit('gender')} className="w-full text-left">
              <div className={rowClass}>
                <span className={labelClass}>Gender</span>
                <span className={`${valueClass} flex items-center justify-end gap-1`}>
                  {user?.gender || '—'}
                  <span className="text-[#555]">›</span>
                </span>
              </div>
            </button>
            <div className={dividerClass} />

            <button type="button" onClick={() => openEdit('email')} className="w-full text-left">
              <div className={rowClass}>
                <span className={labelClass}>Email</span>
                <span className={`${valueClass} flex items-center justify-end gap-1`}>
                  {user?.email || '—'}
                  <span className="text-[#555]">›</span>
                </span>
              </div>
            </button>
            <div className={dividerClass} />

            <button type="button" onClick={() => openEdit('telegram')} className="w-full text-left">
              <div className={rowClass}>
                <span className={labelClass}>Telegram</span>
                <span className={`${valueClass} flex items-center justify-end gap-1`}>
                  {displayTelegram()}
                  <span className="text-[#555]">›</span>
                </span>
              </div>
            </button>
            <div className={dividerClass} />

            <button type="button" onClick={() => openEdit('date_of_birth')} className="w-full text-left">
              <div className={rowClass}>
                <span className={labelClass}>Date of Birth</span>
                <span className={`${valueClass} flex items-center justify-end gap-1`}>
                  {user?.date_of_birth || '—'}
                  <span className="text-[#555]">›</span>
                </span>
              </div>
            </button>
            <div className={dividerClass} />
          </div>
        )}
      </div>

      {/* Edit dialog - warm dark background, white title, Save yellow */}
      {editField != null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-[340px] rounded-2xl bg-[#2a2520] p-5 shadow-xl">
            <h2 className="text-lg font-semibold text-white">
              Edit {FIELD_LABELS[editField]}
            </h2>

            {editField === 'gender' ? (
              <div className="mt-4 space-y-2">
                {['Male', 'Female', 'Other'].map((opt) => (
                  <label
                    key={opt}
                    className="flex cursor-pointer items-center gap-3 py-2"
                  >
                    <input
                      type="radio"
                      name="gender"
                      checked={editValue.toUpperCase() === opt.toUpperCase()}
                      onChange={() => setEditValue(opt)}
                      className="h-4 w-4 accent-[#FFCC00]"
                    />
                    <span className="text-white">{opt}</span>
                  </label>
                ))}
              </div>
            ) : (
              <div className="mt-4">
                <label className="mb-1.5 block text-sm text-[#BDBDBD]">
                  New {FIELD_LABELS[editField]}
                </label>
                <input
                  type={editField === 'email' ? 'email' : 'text'}
                  value={editValue}
                  onChange={(e) => setEditValue(e.target.value)}
                  placeholder={`Enter ${FIELD_LABELS[editField].toLowerCase()}`}
                  className="w-full rounded-xl border border-[#444] bg-[#1a1816] px-4 py-3 text-white placeholder:text-[#666] focus:border-[#FFCC00] focus:outline-none focus:ring-1 focus:ring-[#FFCC00]"
                />
              </div>
            )}

            {error && (
              <p className="mt-3 text-sm text-red-400">{error}</p>
            )}

            <div className="mt-6 flex gap-3">
              <button
                type="button"
                onClick={() => { setEditField(null); setError(null); }}
                className="flex-1 rounded-xl py-3 text-base font-medium text-white transition-opacity hover:opacity-90"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={saveEdit}
                disabled={saving || (editField !== 'gender' && !editValue.trim())}
                className="flex-1 rounded-xl bg-[#FFCC00] py-3 text-base font-bold text-black transition-opacity hover:opacity-95 disabled:opacity-50"
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
