import { useEffect } from 'react';

const GAME_URL = '/game/index.html';

/**
 * Redirects to the Unity WebGL game. The game must be present at public/game/
 * (run website/copy-webgl-build.sh after building the Unity WebGL project).
 * Cache-busting ?v= ensures the latest game page (no loading screen) is loaded.
 */
export function GunduAtaGamePage() {
  useEffect(() => {
    window.location.href = GAME_URL + '?v=' + Date.now();
  }, []);

  return (
    <div className="mobile-frame flex min-h-dvh items-center justify-center bg-[#121212]">
      <p className="text-[#BDBDBD]">Opening game…</p>
    </div>
  );
}
