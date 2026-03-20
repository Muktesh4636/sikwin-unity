import { Link } from 'react-router-dom';

export function ForgotPasswordPage() {
  return (
    <div className="mobile-frame min-h-dvh bg-appBg px-4 pt-6 pb-24">
      <Link to="/login" className="inline-flex items-center gap-1 text-textGrey">
        <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
        Back to Login
      </Link>
      <div className="mt-8 text-2xl font-bold text-textWhite">Forgot password</div>
      <p className="mt-2 text-textGrey">Contact support or use the app to reset your password.</p>
    </div>
  );
}
