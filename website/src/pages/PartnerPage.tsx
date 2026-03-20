import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackArrow } from '../components/BackArrow';
import { apiPartnerRequest } from '../api/endpoints';

export function PartnerPage() {
  const nav = useNavigate();
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmedName = name.trim();
    const trimmedPhone = phone.trim();
    if (!trimmedName || !trimmedPhone) {
      setError('Please enter your name and phone number.');
      return;
    }
    setSubmitting(true);
    try {
      await apiPartnerRequest({
        name: trimmedName,
        phone_number: trimmedPhone,
        message: message.trim() || undefined,
      });
      setSuccess(true);
    } catch (err: any) {
      const msg =
        err?.response?.data?.detail ||
        err?.response?.data?.message ||
        'Could not submit. Please try again.';
      setError(String(msg));
    } finally {
      setSubmitting(false);
    }
  };

  if (success) {
    return (
      <div className="mobile-frame min-h-dvh bg-[#121212]">
        <header className="sticky top-0 z-40 bg-[#121212]">
          <div className="mx-auto flex max-w-[460px] items-center gap-3 px-4 py-4">
            <button
              type="button"
              onClick={() => nav(-1)}
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
              aria-label="Back"
            >
              <BackArrow />
            </button>
            <h1 className="flex-1 text-center text-xl font-bold text-[#FFCC00]">Become a partner with us</h1>
            <div className="w-11" />
          </div>
        </header>
        <div className="mx-auto max-w-[460px] px-4 py-8 text-center">
          <p className="text-white">Thanks for your interest! We&apos;ll get in touch soon.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="mobile-frame min-h-dvh bg-[#121212]">
      <header className="sticky top-0 z-40 bg-[#121212]">
        <div className="mx-auto flex max-w-[460px] items-center gap-3 px-4 py-4">
          <button
            type="button"
            onClick={() => nav(-1)}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded text-[#FFCC00] transition-opacity hover:opacity-90"
            aria-label="Back"
          >
            <BackArrow />
          </button>
          <h1 className="flex-1 text-center text-xl font-bold text-[#FFCC00]">Become a partner with us</h1>
          <div className="w-11" />
        </div>
      </header>

      <div className="mx-auto max-w-[460px] px-4 pb-10 pt-4">
        <p className="mb-6 text-[#E0E0E0]">
          Partner with Gundu Ata at 50% discount. Enter your details below and we&apos;ll get in touch.
        </p>

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label htmlFor="partner-name" className="block text-sm font-medium text-[#FFCC00]">
              Name
            </label>
            <input
              id="partner-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Enter your name"
              className="mt-1.5 w-full rounded-xl border-0 bg-[#1E1E1E] px-4 py-3 text-white placeholder:text-[#BDBDBD] focus:ring-2 focus:ring-[#FFCC00] focus:ring-offset-0"
              autoComplete="name"
            />
          </div>

          <div>
            <label htmlFor="partner-phone" className="block text-sm font-medium text-[#FFCC00]">
              Phone Number
            </label>
            <input
              id="partner-phone"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="Enter your phone number"
              className="mt-1.5 w-full rounded-xl border-0 bg-[#1E1E1E] px-4 py-3 text-white placeholder:text-[#BDBDBD] focus:ring-2 focus:ring-[#FFCC00] focus:ring-offset-0"
              autoComplete="tel"
            />
          </div>

          <div>
            <label htmlFor="partner-message" className="block text-sm font-medium text-[#FFCC00]">
              Message (optional)
            </label>
            <textarea
              id="partner-message"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="Any additional details or questions"
              rows={4}
              className="mt-1.5 w-full resize-none rounded-xl border-0 bg-[#1E1E1E] px-4 py-3 text-white placeholder:text-[#BDBDBD] focus:ring-2 focus:ring-[#FFCC00] focus:ring-offset-0"
            />
          </div>

          {error && (
            <p className="text-sm text-red-400">{error}</p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="h-14 w-full rounded-xl bg-[#FFCC00] font-bold text-white transition-opacity hover:opacity-95 active:opacity-90 disabled:opacity-60"
          >
            {submitting ? 'Submitting…' : 'Submit Request'}
          </button>
        </form>
      </div>
    </div>
  );
}
