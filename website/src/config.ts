import { GAME_ASSET_VERSION } from './utils/gameAssetVersion';

export const API_BASE_URL =
  (import.meta as any).env?.VITE_API_BASE_URL?.toString() || 'https://gunduata.club/api/';

/** If set, visiting via IP or other host redirects here (e.g. https://gunduata.club). Same path preserved. */
export const CANONICAL_SITE_URL =
  (import.meta as any).env?.VITE_CANONICAL_SITE_URL?.toString()?.trim() || '';

/** Origin for shareable links (e.g. `/signup?ref=`). Uses canonical domain when set, else `window.location.origin`. */
export function getSiteOriginForLinks(): string {
  const c = CANONICAL_SITE_URL;
  if (c) {
    try {
      const withScheme = /^https?:\/\//i.test(c) ? c : `https://${c}`;
      return new URL(withScheme).origin;
    } catch {
      /* ignore */
    }
  }
  if (typeof window !== 'undefined' && window.location?.origin) return window.location.origin;
  return '';
}

/** Hosts that are valid for this site (no redirect). Subdomains like gunduata1.gunduata.club are allowed. */
export const ALLOWED_HOSTS: string[] =
  (import.meta as any).env?.VITE_ALLOWED_HOSTS?.toString()
    ?.split(',')
    ?.map((h: string) => h.trim())
    ?.filter(Boolean) || ['gunduata.club', 'www.gunduata.club', 'gunduata1.gunduata.club'];

/** App/franchise name (e.g. "Gundu Ata", "Kiran"). Set via VITE_APP_NAME for franchise builds. */
export const APP_NAME =
  (import.meta as any).env?.VITE_APP_NAME?.toString()?.trim() || 'Gundu Ata';

/** Storage key prefix so different franchises on same domain don't share session. Leave default for main app. */
export const STORAGE_KEY_PREFIX =
  (import.meta as any).env?.VITE_STORAGE_KEY_PREFIX?.toString()?.trim() || 'sikwin';

export const STORAGE_KEYS = {
  get access() { return `${STORAGE_KEY_PREFIX}_access`; },
  get refresh() { return `${STORAGE_KEY_PREFIX}_refresh`; },
  get user() { return `${STORAGE_KEY_PREFIX}_user`; },
  get contactsCache() { return `${STORAGE_KEY_PREFIX}_support_contacts_cache`; },
} as const;

/**
 * Unity WebGL shell URL. Do NOT append a new timestamp on every open — that bypasses the browser cache
 * and makes every game launch re-download assets. Bump VITE_GAME_VERSION only when you deploy a new WebGL build.
 */
const GAME_INDEX_PATH = '/game/index.html';
const gameVersion = (import.meta as any).env?.VITE_GAME_VERSION?.toString()?.trim();
export const GAME_PAGE_HREF = gameVersion
  ? `${GAME_INDEX_PATH}?v=${encodeURIComponent(gameVersion)}`
  : `${GAME_INDEX_PATH}?v=${GAME_ASSET_VERSION}`;

