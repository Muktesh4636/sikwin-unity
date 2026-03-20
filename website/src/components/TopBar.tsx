import { useNavigate } from 'react-router-dom';
import { BackArrow } from './BackArrow';

export function TopBar({ title, back }: { title: string; back?: boolean }) {
  const nav = useNavigate();
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-appBg/90 backdrop-blur">
      <div className="mx-auto flex max-w-[460px] items-center gap-3 px-4 py-4">
        {back ? (
          <button type="button" className="flex h-11 w-11 items-center justify-center rounded text-primaryYellow transition-opacity hover:opacity-90" onClick={() => nav(-1)} aria-label="Back">
            <BackArrow />
          </button>
        ) : (
          <div className="w-10" />
        )}
        <div className="flex-1 text-center text-xl font-extrabold text-textWhite">{title}</div>
        <div className="w-10" />
      </div>
    </header>
  );
}

