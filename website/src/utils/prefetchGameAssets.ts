const GAME_BUILD_BASE = '/game/Build/';
const ASSETS = ['WebGL.framework.js', 'WebGL.data', 'WebGL.wasm'];

let prefetched = false;

/** Prefetch heavy game assets so they are in cache when user opens the game. Safe to call multiple times. */
export function prefetchGameAssets(): void {
  if (typeof document === 'undefined' || prefetched) return;
  prefetched = true;
  for (const name of ASSETS) {
    const href = GAME_BUILD_BASE + name;
    if (document.querySelector(`link[rel="prefetch"][href="${href}"]`)) continue;
    const link = document.createElement('link');
    link.rel = 'prefetch';
    link.href = href;
    document.head.appendChild(link);
  }
}
