# Separating franchises on the website (when user logs in)

You need a way for the website to know **which franchise** the user/session belongs to so API calls and data are scoped correctly.

---

## Option A: URL-based (subdomain or path) — website knows franchise from the URL

**How it works:** Each franchise has its own URL. When the user visits that URL, the site derives the franchise and uses it for all requests.

- **Subdomain:** `gunduata.com` = main (Gundu Ata), `diceking.gunduata.com` or `partner1.gunduata.com` = franchise 1, etc.
- **Path:** `gunduata.com` = main, `gunduata.com/f/diceking` or `gunduata.com/partner/xyz` = franchise.

**Implementation:**

1. **Derive franchise on load**  
   In [website/src/config.ts](website/src/config.ts) (or a small `franchise.ts` module), read `window.location.hostname` or `pathname` and set:
   - `FRANCHISE_ID` or `TENANT_SLUG` (e.g. `"sikwin"` for main, `"diceking"` for partner).
2. **Use it for API**  
   - Either: set **API base URL** per franchise (each franchise has its own backend), e.g. `https://api.diceking.com/api/` for franchise `diceking`.  
   - Or: keep one API base and send a **tenant header** on every request, e.g. `X-Franchise-Id: diceking` or `X-Tenant: partner1`. Backend uses this to scope data.
3. **Storage keys**  
   To avoid mixing sessions when the same user visits different franchise sites (e.g. main vs partner subdomain), prefix localStorage keys with franchise: e.g. `sikwin_access` → `${FRANCHISE_ID}_access` in [website/src/config.ts](website/src/config.ts) and [website/src/api/http.ts](website/src/api/http.ts). Same for `sikwin_refresh`, `sikwin_user`, etc.
4. **Login**  
   User logs in on `diceking.gunduata.com` → franchise = `diceking` → all requests use that franchise (different API or same API + `X-Franchise-Id: diceking`). No extra step at login; the “separation” is fixed by the URL they’re on.

**Pros:** Clear separation per URL; same codebase; easy to explain (this URL = this franchise).  
**Cons:** Requires routing (subdomain or path) and backend support for tenant header or per-franchise API.

---

## Option B: Backend-driven (tenant in user or token)

**How it works:** One website URL (e.g. `gunduata.com`). User logs in → backend returns **user + franchise/tenant id** (or it’s inside the JWT). Frontend stores it and sends it with every request.

**Implementation:**

1. **Backend**  
   Login/profile response includes `franchise_id` or `tenant_id` (or it’s in the JWT payload). Backend scopes all data by this tenant.
2. **Frontend**  
   - After login (or when loading profile), store `franchise_id` in memory and/or localStorage (e.g. in the same place you keep `sikwin_user` or in a dedicated key like `sikwin_franchise`).  
   - In [website/src/api/http.ts](website/src/api/http.ts), add a request interceptor that sends e.g. `X-Franchise-Id: <stored_franchise_id>` (or `X-Tenant-Id`) on every request.  
   - If the backend doesn’t return it in login/profile, you need a small endpoint like `GET /auth/tenant/` or include it in the JWT and decode it on the client.
3. **Separation**  
   “Separating them” = backend uses the header (or token claim) to return only that franchise’s data. Same website, different data per user depending on their franchise.

**Pros:** Single URL; no subdomain/path setup; franchise is a property of the account.  
**Cons:** Backend must support tenant and return it (or put it in the token).

---

## Option C: Build-time / separate deployment per franchise (white-label)

**How it works:** Each franchise has its **own domain and deployment**. The build is configured with that franchise’s API base URL and branding (e.g. `VITE_API_BASE_URL`, `VITE_APP_NAME`). No “detection” at runtime—the deployed site is already that franchise.

**Implementation:**

- Different build per franchise: e.g. `VITE_API_BASE_URL=https://api.diceking.com/api/` and `VITE_APP_NAME="Dice King"` for franchise 1.  
- Deploy to `diceking.com` (or `diceking.gunduata.com`).  
- When a user logs in on that site, they’re already on that franchise’s backend; separation is by deployment, not by URL parsing or tenant header.

**Pros:** Maximum isolation; each franchise can have different branding and API.  
**Cons:** More deployments and build variants to maintain.

---

## Recommendation

- If each franchise has **its own domain/subdomain** (e.g. main = gunduata.com, partners = partner1.gunduata.com): use **Option A** (derive franchise from host/path, set API base or send tenant header, use franchise-prefixed storage keys).  
- If you have **one main site** and users belong to different franchises by account: use **Option B** (backend returns tenant, frontend sends it on every request).  
- If you want **fully separate sites per franchise**: use **Option C** (build/deploy per franchise with env vars).

**Minimal code touch for A or B:**

- **Config:** Export `getFranchiseId()` (from host/path or from stored user).  
- **Storage:** Use franchise-prefixed keys when you have multiple franchises on the same domain (e.g. subdomains sharing origin) so sessions don’t mix.  
- **HTTP:** Use one base URL and add `X-Franchise-Id` (or similar) from config or from user, and have the backend scope by it.

This way, “how we separate them in website when a user logs in” is either: (A) by the URL they’re on, or (B) by the franchise/tenant the backend assigns to their account and that the frontend sends on each request.
