import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.tsx';
import { AuthProvider } from './auth/AuthContext';
import { LocaleProvider } from './context/LocaleContext';
import { CANONICAL_SITE_URL, ALLOWED_HOSTS, APP_NAME } from './config';
import { isAdminUrlPath } from './utils/adminPath';

if (typeof document !== 'undefined') document.title = APP_NAME;

// If canonical site is set and user arrived via IP or unknown host, redirect to same page on canonical domain.
// gunduata1.gunduata.club and other allowed hosts are not redirected.
let willRedirect = false;
if (typeof window !== 'undefined' && CANONICAL_SITE_URL) {
  try {
    const canonical = new URL(CANONICAL_SITE_URL);
    const current = window.location;
    const isIp = /^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(current.hostname);
    const isAllowedHost = ALLOWED_HOSTS.some(
      (allowed: string) => current.hostname === allowed || current.hostname.endsWith('.' + allowed)
    );
    if (!isAllowedHost && (isIp || current.host !== canonical.host)) {
      const redirect = canonical.origin + current.pathname + current.search;
      window.location.replace(redirect);
      willRedirect = true;
    }
  } catch {
    // ignore invalid CANONICAL_SITE_URL
  }
}

const skipSpaBoot =
  typeof window !== 'undefined' && isAdminUrlPath(window.location.pathname);

if (!willRedirect && !skipSpaBoot) {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <AuthProvider>
        <LocaleProvider>
          <App />
        </LocaleProvider>
      </AuthProvider>
    </StrictMode>
  );
}
