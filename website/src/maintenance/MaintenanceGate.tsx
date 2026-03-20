import React, { useEffect, useState } from 'react';
import { apiMaintenanceStatus } from '../api/endpoints';

type MaintenanceState =
  | { active: false }
  | { active: true; message: string };

function formatMaintenanceMessage(payload: Record<string, any>): string {
  const active = payload?.maintenance === true;
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

export function MaintenanceGate({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<MaintenanceState>({ active: false });
  const [checking, setChecking] = useState(true);

  const MAINTENANCE_CHECK_TIMEOUT_MS = 5000;

  const check = async () => {
    try {
      setChecking(true);
      const resp = await apiMaintenanceStatus();
      if (resp.data?.maintenance === true) {
        setState({ active: true, message: formatMaintenanceMessage(resp.data) });
      } else {
        setState({ active: false });
      }
    } catch {
      // Network failure or timeout: do not block the app.
      setState({ active: false });
    } finally {
      setChecking(false);
    }
  };

  useEffect(() => {
    check();
    const onVis = () => {
      if (document.visibilityState === 'visible') check();
    };
    document.addEventListener('visibilitychange', onVis);
    return () => document.removeEventListener('visibilitychange', onVis);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // If we're still checking after a short time, show the app anyway so the site never appears "down"
  const [showAnyway, setShowAnyway] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setShowAnyway(true), MAINTENANCE_CHECK_TIMEOUT_MS + 500);
    return () => clearTimeout(t);
  }, []);

  if (checking && !showAnyway) {
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
          <button className="btn-primary mt-5 w-full" onClick={check}>
            Retry
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}

