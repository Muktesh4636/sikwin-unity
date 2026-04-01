import React, { useEffect, useRef, useState } from 'react';
import { apiMaintenanceStatus } from '../api/endpoints';

type MaintenanceState =
  | { active: false }
  | { active: true; message: string };

/** Same flag the APK reads from GET maintenance/status/; tolerate aliases the admin API might send. */
function isMaintenanceActive(payload: Record<string, any> | undefined): boolean {
  if (!payload || typeof payload !== 'object') return false;
  const p = payload as Record<string, unknown>;
  if (p.maintenance === true) return true;
  if (p.is_maintenance === true) return true;
  if (p.under_maintenance === true) return true;
  const settings = p.game_settings as Record<string, unknown> | undefined;
  if (settings?.maintenance === true) return true;
  if (settings?.is_maintenance === true) return true;
  return false;
}

function formatMaintenanceMessage(payload: Record<string, any>): string {
  const active = isMaintenanceActive(payload);
  if (!active) return '';

  const rh = Number(payload?.remaining_hours ?? 0);
  const rm = Number(payload?.remaining_minutes ?? 0);
  const legacyUntil = payload?.maintenance_until != null ? Number(payload.maintenance_until) : null;

  if (Number.isFinite(rh) && Number.isFinite(rm)) {
    if (rh <= 0 && rm <= 0) return 'App under maintenance. Please come back soon.';
    if (rh <= 0) return `App under maintenance. Please come back after ${rm} minutes.`;
    if (rm <= 0) return `App under maintenance. Please come back after ${rh === 1 ? '1 hour' : `${rh} hours`}.`;
    return `App under maintenance. Please come back after ${rh === 1 ? '1 hour' : `${rh} hours`} ${rm} minutes.`;
  }

  if (legacyUntil != null && Number.isFinite(legacyUntil)) {
    if (legacyUntil < 60) return `App under maintenance. Please come back after ${legacyUntil} minutes.`;
    if (legacyUntil % 60 === 0) {
      const h = Math.floor(legacyUntil / 60);
      return `App under maintenance. Please come back after ${h === 1 ? '1 hour' : `${h} hours`}.`;
    }
    const h = Math.floor(legacyUntil / 60);
    const m = legacyUntil % 60;
    return `App under maintenance. Please come back after ${h === 1 ? '1 hour' : `${h} hours`} ${m} minutes.`;
  }

  return 'App under maintenance. Please come back soon.';
}

const POLL_MS = 30_000;

/** Local dev hits production API for maintenance and waits seconds — skip so the shell loads instantly. */
function isLocalhostOrigin(): boolean {
  if (typeof window === 'undefined') return false;
  const h = window.location.hostname;
  return h === 'localhost' || h === '127.0.0.1' || h === '[::1]';
}

export function MaintenanceGate({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<MaintenanceState>({ active: false });
  const [checking, setChecking] = useState(() => !isLocalhostOrigin());
  const firstLoadDone = useRef(false);

  const check = async (opts?: { forInitialGate?: boolean }) => {
    const isInitial = opts?.forInitialGate === true;
    try {
      if (isInitial && !firstLoadDone.current) setChecking(true);
      const resp = await apiMaintenanceStatus();
      const data = resp.data as Record<string, any> | undefined;
      if (isMaintenanceActive(data)) {
        setState({ active: true, message: formatMaintenanceMessage(data ?? {}) });
      } else {
        setState({ active: false });
      }
    } catch {
      setState({ active: false });
    } finally {
      if (isInitial) {
        firstLoadDone.current = true;
        setChecking(false);
      }
    }
  };

  useEffect(() => {
    if (isLocalhostOrigin()) {
      firstLoadDone.current = true;
      setChecking(false);
      return;
    }

    void check({ forInitialGate: true });
    const onVis = () => {
      if (document.visibilityState === 'visible') void check();
    };
    document.addEventListener('visibilitychange', onVis);
    const poll = window.setInterval(() => void check(), POLL_MS);
    return () => {
      document.removeEventListener('visibilitychange', onVis);
      window.clearInterval(poll);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (checking) {
    return (
      <div className="mobile-frame app-shell grid place-items-center min-h-dvh bg-[#121212]">
        <div className="text-[#BDBDBD]">Loading…</div>
      </div>
    );
  }

  if (state.active) {
    return (
      <div className="mobile-frame app-shell px-4 pt-10">
        <div className="card p-5">
          <div className="text-primaryYellow text-sm font-black tracking-widest">MAINTENANCE</div>
          <div className="mt-2 text-lg font-extrabold">App under maintenance</div>
          <div className="mt-2 text-textGrey">{state.message}</div>
          <button className="btn-primary mt-5 w-full" onClick={() => void check()}>
            Retry
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}

