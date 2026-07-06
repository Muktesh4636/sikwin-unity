/**
 * Must match `cacheBust` in public/game/index.html (Unity config).
 * Bump when you ship a new WebGL build so clients fetch fresh .data / .wasm / .framework.
 */
export const GAME_ASSET_VERSION = '4';

export function gameBuildQuery(): string {
  return `?v=${GAME_ASSET_VERSION}`;
}

export function gameIndexUrl(): string {
  return `/game/index.html${gameBuildQuery()}`;
}
