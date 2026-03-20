import { Link } from 'react-router-dom';

export function ComingSoonPage() {
  return (
    <div className="mobile-frame min-h-dvh flex flex-col items-center justify-center bg-appBg text-textWhite p-4 text-center">
      <h1 className="text-3xl font-bold text-primaryYellow">Coming Soon!</h1>
      <p className="mt-4 text-lg">This feature is under development. Please check back later.</p>
      <Link to="/me" className="mt-8 btn-primary">
        Go to Profile
      </Link>
    </div>
  );
}
