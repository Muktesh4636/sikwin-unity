/** Query keys we accept for invite / referral attribution (must match SignupPage). */
export function getRefParam(searchParams: URLSearchParams): string {
  const raw =
    searchParams.get('ref') ?? searchParams.get('referral') ?? searchParams.get('referral_code');
  return raw?.trim() ?? '';
}

export function withRefQuery(path: '/login' | '/signup', ref: string): string {
  if (!ref) return path;
  return `${path}?ref=${encodeURIComponent(ref)}`;
}
