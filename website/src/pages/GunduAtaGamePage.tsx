import { useEffect } from 'react';
import { GAME_PAGE_HREF } from '../config';
import { prefetchGameAssets } from '../utils/prefetchGameAssets';

/**
 * Redirects to the Unity WebGL game. The game must be present at public/game/
 * (run website/copy-webgl-build.sh after building the Unity WebGL project).
 * Uses stable GAME_PAGE_HREF so the browser can cache the shell; bump VITE_GAME_VERSION when you ship a new WebGL build.
 */
export function GunduAtaGamePage() {
  useEffect(() => {
    prefetchGameAssets();
    window.setTimeout(() => {
      window.location.href = GAME_PAGE_HREF;
    }, 200);
  }, []);

  return (
    <div className="mobile-frame flex min-h-dvh items-center justify-center bg-[#121212]">
      <p className="text-[#BDBDBD]">Opening game…</p>
    </div>
  );
}
