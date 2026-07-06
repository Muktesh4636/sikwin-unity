/** Backend admin (e.g. Django) at /admin/ must not be handled by the React SPA shell. */
export function isAdminUrlPath(pathname: string): boolean {
  return pathname === '/admin' || pathname.startsWith('/admin/');
}
