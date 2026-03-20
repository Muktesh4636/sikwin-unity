export const API_BASE_URL =
  (import.meta as any).env?.VITE_API_BASE_URL?.toString() || 'https://gunduata.club/api/';

/** If set, visiting via IP or other host redirects here (e.g. https://gunduata.club). Same path preserved. */
export const CANONICAL_SITE_URL =
  (import.meta as any).env?.VITE_CANONICAL_SITE_URL?.toString()?.trim() || '';

/** Hosts that are valid for this site (no redirect). Subdomains like gunduata1.gunduata.club are allowed. */
export const ALLOWED_HOSTS: string[] =
  (import.meta as any).env?.VITE_ALLOWED_HOSTS?.toString()
    ?.split(',')
    ?.map((h: string) => h.trim())
    ?.filter(Boolean) || ['gunduata.club', 'www.gunduata.club', 'gunduata1.gunduata.club'];

export const STORAGE_KEYS = {
  access: 'sikwin_access',
  refresh: 'sikwin_refresh',
  user: 'sikwin_user',
  contactsCache: 'sikwin_support_contacts_cache',
} as const;

