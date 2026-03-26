# Franchise Model: What You Need to Do

When you introduce **franchise owners**, each franchise will have:
- **Separate deposit and withdrawal requests** (each owner sees only their franchise’s requests)
- **Separate APK** (different app name, package ID, API URL)
- **Separate website** (different domain, API URL, optional branding)

Below is what to do in **backend**, **website**, and **APK** so this works.

---

## 1. Backend (API at gunduata.club or your server)

The backend must **scope all data by franchise**.

### 1.1 Data model

- Add a **Franchise** (or Tenant) model, e.g.:
  - `id`, `name`, `slug` (e.g. `gunduata`, `diceking`), `domain`, `is_active`, etc.
- Link existing data to franchise:
  - **User** → `franchise_id` (required). A user belongs to one franchise.
  - **Deposit** (deposit requests) → `franchise_id` (or via user).
  - **Withdraw** (withdrawal requests) → `franchise_id` (or via user).
  - **PaymentMethod** (deposit UPI/bank/USDT) → `franchise_id` (each franchise has its own payment methods).
  - **Wallet** → typically via user → franchise.
  - **Support contacts** → `franchise_id` (or global with franchise override). You already use `?package=<applicationId>`; you can also key by `franchise_id`.

Ensure every list/mutation for deposits, withdrawals, payment methods, and support is filtered by the current franchise (see below).

### 1.2 How the API knows “current franchise”

Pick one approach:

- **Option A – Separate API per franchise**  
  Each franchise has its own backend URL (e.g. `https://api.franchise1.com/api/`, `https://api.franchise2.com/api/`). No tenant header needed; each deployment has its own DB or same DB with a fixed franchise context.

- **Option B – Single API + tenant in request**  
  One API base URL. Every request must carry the franchise, e.g.:
  - Header: `X-Franchise-Id: <slug>` or `X-Tenant-Id: <id>`, or
  - Subdomain: `franchise1.gunduata.club` → backend resolves franchise from host.
  Backend then filters all queries (deposits, withdraws, users, payment methods, support) by that franchise.

- **Option C – Tenant in JWT**  
  Login returns a JWT that includes `franchise_id` or `franchise_slug`. Backend reads it from the token and scopes all operations to that franchise. No header needed if token is always present.

Recommendation: **Option B or C** so you run one API and one codebase; Option A is fine if you want full isolation per franchise (separate deploy per franchise).

### 1.3 Endpoints to scope by franchise

- `auth/deposits/mine/` – only current user’s deposits (user already implies franchise).
- `auth/withdraws/mine/` – only current user’s withdrawals.
- `auth/withdraws/initiate/` – create under current user’s franchise.
- `auth/deposits/upload-proof/`, `auth/deposits/submit-utr/` – under current user’s franchise.
- `auth/payment-methods/` – return only **that franchise’s** payment methods (for deposit page).
- `support/contacts/` – return contacts for that franchise (you can keep `package` for backward compatibility and add franchise when needed).

Ensure admin/back-office can list deposits and withdrawals **per franchise** (filter by `franchise_id`).

---

## 2. Website (separate site per franchise)

You have three approaches; see **docs/website-franchise-separation.md** for detail.

- **Option A – URL-based (subdomain/path)**  
  One codebase. Franchise is derived from host (e.g. `franchise1.gunduata.club`) or path. Set API base URL (or tenant header) and storage keys per franchise so sessions don’t mix.

- **Option B – Backend-driven (tenant in user/JWT)**  
  One website URL. After login, backend returns franchise (or it’s in the JWT). Frontend sends e.g. `X-Franchise-Id` on every request (or backend uses JWT). No URL parsing needed.

- **Option C – Build and deploy per franchise (white-label)**  
  **Best fit for “separate website per franchise”.** One codebase, different **builds** per franchise:
  - Set env at build time, e.g.:
    - `VITE_API_BASE_URL=https://api.franchise1.com/api/`
    - `VITE_CANONICAL_SITE_URL=https://franchise1.com`
    - `VITE_ALLOWED_HOSTS=franchise1.com,www.franchise1.com`
  - Deploy the build to that franchise’s domain (e.g. `franchise1.com`).  
  No runtime “detection”; the deployed site is already that franchise. Deposits/withdrawals are separate because the API is franchise-scoped (or a different API URL per franchise).

**Storage keys:** If multiple franchises can be visited on the same domain (e.g. subdomains sharing origin), prefix `localStorage` keys with franchise (e.g. in `website/src/config.ts` and `website/src/api/http.ts`) so sessions don’t mix. With Option C and different domains per franchise, you can keep current keys.

---

## 3. APK (separate app per franchise)

You already have **product flavors** in `sikwin/app/build.gradle` (e.g. `sikwin`, `sai`). To get a true “separate APK per franchise” with its own API and branding:

### 3.1 Per-flavor API URL and download URL

- **Current:** `Constants.kt` has a single `BASE_URL` and `APK_DOWNLOAD_URL`.
- **Change:** Make these **per flavor** so each franchise APK talks to its own API (and download page).

**Option 1 – BuildConfig per flavor (recommended)**  
In `sikwin/app/build.gradle`, inside each `productFlavors` block, add:

```gradle
productFlavors {
    sikwin {
        dimension "franchise"
        resValue "string", "app_name", "Gundu Ata"
        buildConfigField "String", "BASE_URL", '"https://gunduata.club/api/"'
        buildConfigField "String", "APK_DOWNLOAD_URL", '"https://gunduata.com/download"'
    }
    sai {
        dimension "franchise"
        applicationId "com.sai.gunduata"
        resValue "string", "app_name", "Sai"
        buildConfigField "String", "BASE_URL", '"https://api.sai.example.com/api/"'  // franchise API
        buildConfigField "String", "APK_DOWNLOAD_URL", '"https://sai.example.com/download"'
    }
    jittu {
        dimension "franchise"
        applicationId "com.jittu.gunduata"
        resValue "string", "app_name", "Gundu Ata"
        buildConfigField "String", "BASE_URL", '"https://gunduata.club/api/"'
        buildConfigField "String", "APK_DOWNLOAD_URL", '"https://jittu.gunduata.online/gundu-ata.apk"'
    }
    // Add more flavors per franchise...
}
```

Enable BuildConfig if not already:

```gradle
android {
    buildFeatures {
        buildConfig true
        compose true
    }
}
```

Then in code, use `BuildConfig.BASE_URL` and `BuildConfig.APK_DOWNLOAD_URL` instead of `Constants.BASE_URL` and `Constants.APK_DOWNLOAD_URL` (e.g. in `RetrofitClient.kt` and any place that opens the download URL).

**Option 2 – Flavor-specific source set**  
Create `src/sai/java/.../Constants.kt` (and same for other flavors) that override `BASE_URL` and `APK_DOWNLOAD_URL` for that flavor. Less central than BuildConfig.

### 3.2 Unity layer (unityLibrary) – game API URL

`UnityPlayerGameActivity.java` currently has:

```java
private static final String FREQUENCY_API_URL = "https://gunduata.club/api/game/frequency/";
```

For **per-franchise** APKs, this must point to the same franchise’s API. Options:

- Build a **separate unityLibrary build per franchise** (e.g. different build type or flavor that sets this URL), or
- Pass the base URL from the Kotlin app into Unity at runtime (e.g. via `UnitySendMessage` or SharedPreferences) and have the Unity game construct the frequency URL from that base. Then Kotlin already uses `BuildConfig.BASE_URL`, and Unity reads the same base from prefs/Intent.

Until then, all franchise APKs that use this Unity build will hit the same frequency URL; once backend is franchise-aware, you can still scope by user/token.

### 3.3 Deep links and website URL

- `AndroidManifest.xml` has `<data android:scheme="https" android:host="gunduata.com" />`. For another franchise, add a flavor-specific manifest (e.g. `src/sai/AndroidManifest.xml`) or manifest placeholder so each flavor uses its own host (e.g. `sai.example.com`) for deep links and “Open in browser” links.
- Any hardcoded “gunduata.com” in the app (e.g. share messages, APK download) should use the per-flavor download URL or website URL (from BuildConfig or flavor-specific Constants).

### 3.4 ApplicationId and prefs

- Each flavor already has a **different `applicationId`** (e.g. `com.sikwin.app` vs `com.sai.gunduata`). That gives separate installs and separate app data, so **SharedPreferences** (`gunduata_prefs`) are already isolated per franchise APK. No need to change prefs names for isolation when each franchise has its own APK.

---

## 4. Checklist (when you add a new franchise)

Use this when you onboard a new franchise owner.

- [ ] **Backend**
  - Create franchise record (slug, name, domain, etc.).
  - Ensure User, Deposit, Withdraw, PaymentMethod, Support are all scoped by franchise (and API resolves franchise from header/subdomain/JWT).
  - Configure payment methods and support contacts for this franchise.
- [ ] **Website**
  - **If Option C (separate site):** Create build with this franchise’s `VITE_API_BASE_URL`, `VITE_CANONICAL_SITE_URL`, `VITE_ALLOWED_HOSTS`. Deploy to franchise domain.
  - **If Option A:** Add subdomain/path and config so that URL sets API base or tenant header and storage prefix.
- [ ] **APK**
  - Add a new product flavor in `sikwin/app/build.gradle` with its own `applicationId`, `app_name`, `BASE_URL`, `APK_DOWNLOAD_URL`.
  - Use `BuildConfig.BASE_URL` / `BuildConfig.APK_DOWNLOAD_URL` in app code.
  - Point Unity game API (frequency URL) to this franchise’s API (or make it dynamic from Kotlin).
  - Set deep link host to this franchise’s website domain (flavor-specific manifest or placeholder).
  - Build and sign the franchise APK; host it at the franchise’s download URL.

After this, that franchise will have **separate deposits and withdrawals** (backend), **separate website** (separate build/deploy or URL-based config), and **separate APK** (flavor with its own API and branding).
