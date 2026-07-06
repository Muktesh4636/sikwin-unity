import { gameBuildQuery } from './gameAssetVersion';

const GAME_BUILD_BASE = '/game/Build/';
const ASSETS = ['WebGL.framework.js', 'WebGL.data', 'WebGL.wasm'];

const q = gameBuildQuery();

let prefetched = false;

function addPrefetch(href: string): void {
  if (typeof document === 'undefined') return
  if (document.querySelector(`link[rel="prefetch"][href="${href}"]`)) return
  const link = document.createElement('link')
  link.rel = 'prefetch'
  link.href = href
  document.head.appendChild(link)
}

/**
 * Starts prefetching WebGL assets *only when user is about to open the game*.
 * This avoids downloading Unity chunks while browsing the rest of the site.
 */
export function prefetchGameAssets(): void {
  if (typeof document === 'undefined' || prefetched) return
  prefetched = true

  // Warm the game shell first (Unity loader HTML is small but important).
  addPrefetch('/game/index.html' + q);

  for (const name of ASSETS) {
    addPrefetch(GAME_BUILD_BASE + name + q);
  }
}

