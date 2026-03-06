export const API_BASE_URL =
  (import.meta as any).env?.VITE_API_BASE_URL?.toString() || 'https://gunduata.club/api/';

export const STORAGE_KEYS = {
  access: 'sikwin_access',
  refresh: 'sikwin_refresh',
  user: 'sikwin_user',
  contactsCache: 'sikwin_support_contacts_cache',
} as const;

