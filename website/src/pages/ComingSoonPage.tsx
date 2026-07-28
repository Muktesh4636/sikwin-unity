import { Link } from 'react-router-dom';

export function ComingSoonPage() {
  return (
    <div className="mobile-frame min-h-dvh flex flex-col items-center justify-center bg-appBg p-4 pb-24 text-center text-textWhite">
      <h1 className="text-3xl font-bold text-primaryYellow">Coming Soon!</h1>
      <p className="mt-4 text-lg text-textWhite/80">This feature is under development. Please check back later.</p>
      <Link to="/" className="mt-8 btn-primary">
        Back to Home
      </Link>
    </div>
  );
}
