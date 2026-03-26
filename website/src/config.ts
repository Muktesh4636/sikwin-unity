export const API_BASE_URL =
  (import.meta as any).env?.VITE_API_BASE_URL?.toString() || 'https://gunduata.club/api/';

/** If set, visiting via IP or other host redirects here (e.g. https://gunduata.club). Same path preserved. */
export const CANONICAL_SITE_URL =
  (import.meta as any).env?.VITE_CANONICAL_SITE_URL?.toString()?.trim() || '';

/**
 * Main domain where the Django/game admin panel lives (e.g. https://gunduata.club).
 * Franchise player sites (jittu.*, kiran.*) only replace the public SPA; do not move /game-admin/ to those hosts.
 */
export const ADMIN_PANEL_BASE_URL =
  (import.meta as any).env?.VITE_ADMIN_PANEL_BASE_URL?.toString()?.trim() || '';

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
 * Sent on every API request as `X-Franchise-Code` when set (backend may use it to scope users under a franchise admin).
 */
export const FRANCHISE_CODE =
  (import.meta as any).env?.VITE_FRANCHISE_CODE?.toString()?.trim() || '';

/**
 * If set, sign-ups send this as `referral_code` when the user leaves the field empty (ties them to Jittu’s tree in admin).
 * Replace with the real code from your backend / Jittu’s admin referral.
 */
export const DEFAULT_REFERRAL_CODE =
  (import.meta as any).env?.VITE_DEFAULT_REFERRAL_CODE?.toString()?.trim() || '';

/**
 * Unity WebGL shell URL. Do NOT append a new timestamp on every open — that bypasses the browser cache
 * and makes every game launch re-download assets. Bump VITE_GAME_VERSION only when you deploy a new WebGL build.
 */
const GAME_INDEX_PATH = '/game/index.html';
const gameVersion = (import.meta as any).env?.VITE_GAME_VERSION?.toString()?.trim();
export const GAME_PAGE_HREF = gameVersion
  ? `${GAME_INDEX_PATH}?v=${encodeURIComponent(gameVersion)}`
  : GAME_INDEX_PATH;

