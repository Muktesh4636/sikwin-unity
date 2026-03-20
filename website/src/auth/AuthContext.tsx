import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { STORAGE_KEYS } from '../config';
import { apiLogin, apiProfile, type User, type AuthResponse } from '../api/endpoints';

type AuthState = {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  ready: boolean;
};

type AuthContextValue = AuthState & {
  login: (username: string, password: string) => Promise<void>;
  logout: (reason?: string) => void;
  refreshUser: () => Promise<void>;
  setSession: (data: AuthResponse) => void;
};

const Ctx = createContext<AuthContextValue | null>(null);

function readState(): AuthState {
  const accessToken = localStorage.getItem(STORAGE_KEYS.access);
  const refreshToken = localStorage.getItem(STORAGE_KEYS.refresh);
  const userRaw = localStorage.getItem(STORAGE_KEYS.user);
  const user = userRaw ? (JSON.parse(userRaw) as User) : null;
  return { user, accessToken, refreshToken, ready: false };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>(() => readState());

  const setUser = (user: User | null) => {
    if (user) localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(user));
    else localStorage.removeItem(STORAGE_KEYS.user);
    setState((s) => ({ ...s, user }));
  };

  const logout = useCallback((reason?: string) => {
    localStorage.removeItem(STORAGE_KEYS.access);
    localStorage.removeItem(STORAGE_KEYS.refresh);
    localStorage.removeItem(STORAGE_KEYS.user);
    setState({ user: null, accessToken: null, refreshToken: null, ready: true });
    window.dispatchEvent(new CustomEvent('sikwin:logged_out', { detail: { reason } }));
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const resp = await apiLogin(username, password);
    localStorage.setItem(STORAGE_KEYS.access, resp.data.access);
    localStorage.setItem(STORAGE_KEYS.refresh, resp.data.refresh);
    setState((s) => ({
      ...s,
      accessToken: resp.data.access,
      refreshToken: resp.data.refresh,
    }));
    setUser(resp.data.user);
  }, []);

  const setSession = useCallback((data: AuthResponse) => {
    localStorage.setItem(STORAGE_KEYS.access, data.access);
    localStorage.setItem(STORAGE_KEYS.refresh, data.refresh);
    setState((s) => ({
      ...s,
      accessToken: data.access,
      refreshToken: data.refresh,
    }));
    setUser(data.user);
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const resp = await apiProfile();
      setUser(resp.data);
    } catch {
      // ignore; request layer will emit logout if needed
    }
  }, []);

  useEffect(() => {
    // Initial boot: mark ready and attempt to refresh profile if token exists.
    setState((s) => ({ ...s, ready: true }));
    const hasToken = !!localStorage.getItem(STORAGE_KEYS.access);
    if (hasToken) refreshUser();
  }, [refreshUser]);

  useEffect(() => {
    const onLogout = () => logout('http_interceptor');
    window.addEventListener('sikwin:logout' as any, onLogout);
    return () => window.removeEventListener('sikwin:logout' as any, onLogout);
  }, [logout]);

  const value: AuthContextValue = useMemo(
    () => ({
      ...state,
      login,
      logout,
      refreshUser,
      setSession,
    }),
    [state, login, logout, refreshUser, setSession]
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth must be used within AuthProvider');
  return v;
}

