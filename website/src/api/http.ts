import axios, { AxiosError } from 'axios';
import { API_BASE_URL, STORAGE_KEYS } from '../config';

type RefreshResponse = {
  access?: string;
  refresh?: string;
  refresh_token?: string;
};

function getAccess(): string | null {
  return localStorage.getItem(STORAGE_KEYS.access);
}

function getRefresh(): string | null {
  return localStorage.getItem(STORAGE_KEYS.refresh);
}

function setTokens(access: string, refresh?: string | null) {
  localStorage.setItem(STORAGE_KEYS.access, access);
  if (refresh) localStorage.setItem(STORAGE_KEYS.refresh, refresh);
}

function clearTokens() {
  localStorage.removeItem(STORAGE_KEYS.access);
  localStorage.removeItem(STORAGE_KEYS.refresh);
}

function looksLikeSessionInvalidated(bodyText: string): boolean {
  const lower = bodyText.toLowerCase();
  return lower.includes('session_invalidated') || lower.includes('logged in on another device');
}

let refreshInFlight: Promise<string | null> | null = null;

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
});

http.interceptors.request.use((config) => {
  const token = getAccess();
  if (token) {
    config.headers = config.headers ?? {};
    (config.headers as any).Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (resp) => resp,
  async (error: AxiosError) => {
    const status = error.response?.status;
    const url = (error.config?.url ?? '').toString();
    const isAuthRoute = url.includes('auth/login/') || url.includes('auth/register/') || url.includes('auth/token/refresh/');

    if (status !== 401 || isAuthRoute) {
      throw error;
    }

    const refresh = getRefresh();
    if (!refresh) {
      clearTokens();
      window.dispatchEvent(new CustomEvent('sikwin:logout', { detail: { reason: 'missing_refresh' } }));
      throw error;
    }

    const bodyText =
      typeof error.response?.data === 'string'
        ? error.response.data
        : JSON.stringify(error.response?.data ?? {});

    if (looksLikeSessionInvalidated(bodyText)) {
      clearTokens();
      window.dispatchEvent(new CustomEvent('sikwin:logout', { detail: { reason: 'session_invalidated' } }));
      throw error;
    }

    refreshInFlight =
      refreshInFlight ??
      (async () => {
        try {
          const resp = await axios.post<RefreshResponse>(
            `${API_BASE_URL}auth/token/refresh/`,
            { refresh },
            { timeout: 30_000 }
          );
          const newAccess = resp.data?.access;
          const newRefresh = resp.data?.refresh || resp.data?.refresh_token;
          if (!newAccess) return null;
          setTokens(newAccess, newRefresh ?? null);
          return newAccess;
        } catch {
          return null;
        } finally {
          refreshInFlight = null;
        }
      })();

    const newAccess = await refreshInFlight;
    if (!newAccess) {
      clearTokens();
      window.dispatchEvent(new CustomEvent('sikwin:logout', { detail: { reason: 'refresh_failed' } }));
      throw error;
    }

    // Retry original request once with new token.
    const cfg = error.config!;
    cfg.headers = cfg.headers ?? {};
    (cfg.headers as any).Authorization = `Bearer ${newAccess}`;
    return http.request(cfg);
  }
);

