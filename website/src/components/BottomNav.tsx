import { NavLink } from 'react-router-dom';

function HomeIcon({ active }: { active: boolean }) {
  return (
    <svg
      className="h-5 w-5"
      fill={active ? 'currentColor' : 'none'}
      stroke="currentColor"
      viewBox="0 0 24 24"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
      />
    </svg>
  );
}

function DiceIcon({ active }: { active: boolean }) {
  return (
    <img
      src="/dice_3d.png"
      alt=""
      className={`h-6 w-6 object-contain brightness-0 invert ${active ? 'opacity-100' : 'opacity-60'}`}
    />
  );
}

function MeIcon({ active }: { active: boolean }) {
  return (
    <svg
      className="h-5 w-5"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      {/* Head circle */}
      <circle cx="12" cy="6.5" r="3.5" />
      {/* Shoulders / upper body silhouette */}
      <path d="M4 24V14c0-4.4 3.6-8 8-8s8 3.6 8 8v10H4z" />
    </svg>
  );
}

const items = [
  { to: '/home', label: 'Home', Icon: HomeIcon },
  { to: '/gundu-ata', label: 'GUNDU ATA', Icon: DiceIcon },
  { to: '/me', label: 'Me', Icon: MeIcon },
] as const;

export function BottomNav() {
  return (
    <nav
      className="fixed bottom-0 left-1/2 z-50 w-full max-w-[430px] -translate-x-1/2 border-t border-border bg-bottomNav"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      <div className="grid grid-cols-3 px-2 py-2">
        {items.map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              [
                'flex flex-col items-center justify-center gap-1 rounded-xl px-2 py-1.5 text-xs font-semibold',
                isActive ? 'text-primaryYellow' : 'text-textGrey',
              ].join(' ')
            }
          >
            {({ isActive }) => (
              <>
                <Icon active={isActive} />
                <span>{label}</span>
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
