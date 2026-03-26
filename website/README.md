# Gundu Ata (Mobile Website)

Mobile-first website that matches the Sikwin APK UI style and uses the same APIs.

**All website code lives in this one folder (`website/`):** the React app, static assets, and the WebGL game. Nothing outside this folder is needed to build or deploy the site.

```
website/
├── src/                 # React app (pages, components, API, auth)
├── public/              # Static assets (copied as-is into dist)
│   ├── game/            # WebGL game (index.html, Build/, TemplateData/)
│   ├── *.jpg, *.png     # Images, APK, etc.
│   └── ...
├── index.html           # SPA entry
├── package.json
├── vite.config.ts
├── deploy-to-server.sh  # Build + rsync to server
└── dist/                # Build output (generated; deploy this to server)
```

## Access on the internet at gunduata.club

You’ve set a DNS record so **gunduata.club** points to your server. To serve the site there:

1. **On the server** (the IP that gunduata.club points to), configure Nginx to serve the website and proxy `/api/` to your backend. Use the example in `docs/nginx-gunduata.conf`: set `root` to the folder where the built site will live (e.g. `/var/www/gunduata.club`) and `location / { try_files $uri $uri/ /index.html; }` for the SPA.
2. **Deploy the built site** to that folder:
   ```bash
   cd website
   ./deploy-to-server.sh
   ```
   This builds the site and uploads `dist/` to the server. Defaults use the LB at `187.77.186.84` and path `/var/www/gunduata.club`; override with `DEPLOY_HOST`, `REMOTE_PATH` if your server is different.
3. **Reload Nginx** on the server after the first deploy: `nginx -t && systemctl reload nginx` (or `sudo systemctl reload nginx`).

After that, anyone on the internet can open **http://gunduata.club** (or **https://gunduata.club** if you enable SSL) and get the same site. The app already uses `https://gunduata.club/api/` as the API base, so API calls work when the site is loaded from gunduata.club.

**If gunduata.club still shows "Roll with Royalty" or another site:** Nginx on the Load Balancer is serving a different folder. Deploy the build (step 2 above), then on the LB set Nginx `root` for gunduata.club to `/var/www/gunduata.club` and reload Nginx. Step-by-step: **[docs/DEPLOY-GUNDUATA-CLUB.md](../docs/DEPLOY-GUNDUATA-CLUB.md)**.

## Setup

```bash
cd "/Users/pradyumna/Sikwin copy/website"
npm install
```

## Run (dev)

```bash
npm run dev -- --host
```

## Configure API base URL

By default it uses:

- `https://gunduata.club/api/`

To override, create a `.env` file:

```bash
VITE_API_BASE_URL=https://gunduata.club/api/
```

## Redirect IP to gunduata.club

If you have a DNS record pointing **gunduata.club** to your server (or machine), you can redirect visitors from the IP to the same page on the domain so the URL bar shows **gunduata.club**.

Create a `.env` file (or add to it):

- **Local dev** (e.g. you run Vite on port 5174 and gunduata.club resolves to your machine):
  ```bash
  VITE_CANONICAL_SITE_URL=http://gunduata.club:5174
  ```
  Then opening `http://192.168.29.147:5174/` will redirect to `http://gunduata.club:5174/` (same page).

- **Production** (site served at gunduata.club on port 80/443):
  ```bash
  VITE_CANONICAL_SITE_URL=https://gunduata.club
  ```
  Then opening the site via IP will redirect to `https://gunduata.club/` (same path).

Restart the dev server after changing `.env`.

## Franchise website (Kiran)

To create a **separate website for the Kiran franchise** (same codebase, different branding and config):

1. **Use the Kiran env file**  
   `website/.env.kiran` is already set up with:
   - `VITE_APP_NAME=Kiran`
   - `VITE_STORAGE_KEY_PREFIX=kiran` (so Kiran and main site don’t share session if on same domain)
   - `VITE_CANONICAL_SITE_URL` and `VITE_ALLOWED_HOSTS` for the Kiran domain

2. **Build the Kiran site**
   ```bash
   cd website
   npm run build:kiran
   ```
   Output is in `dist/` (same as main build). Deploy this `dist/` to Kiran’s domain (e.g. `kiran.gunduata.club` or a separate domain).

3. **Run Kiran site locally**
   ```bash
   npm run dev:kiran
   ```
   Uses Kiran env so the app shows “Kiran” and uses Kiran storage keys.

4. **Optional**  
   - Edit `.env.kiran` and set `VITE_API_BASE_URL` if Kiran uses a different API.  
   - Set `VITE_CANONICAL_SITE_URL` and `VITE_ALLOWED_HOSTS` to your real Kiran domain before production deploy.

## WebGL game load time

The Unity build at `public/game/` can be large (`.wasm`, `.data`).

**Split from the main app:** The React SPA (`/`) does **not** prefetch or preload WebGL files. Unity only loads when the user opens the game at **`/game/index.html`** (e.g. after tapping Play). That keeps the main Gundu Ata UI light and avoids downloading WebGL until needed.

1. **Stable game URL** — `GAME_PAGE_HREF` in `src/config.ts`. Bump `VITE_GAME_VERSION` in `.env` when you ship a new WebGL build.

2. **Server** — Serve `/game/Build/*` with gzip/brotli and cache headers (nginx `gzip_static` on `.gz` if used).

3. **Unity** — Smaller build = faster first game load.

## Download APK

The **Home** page has a **Download APK** button (below the promotional banners). To enable it, place your Android APK at:

- `website/public/gundu-ata.apk`

The link serves the file and triggers a download as `GunduAta.apk`.

## Notes

- Unity is **not** integrated (game will be added later).
- Implemented screens: Login, Home, Wallet, Withdraw, Leaderboard, Dice Results, Help Center, Profile.
- Maintenance gate: blocks the UI when `GET /maintenance/status/` returns `maintenance: true`.

# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) (or [oxc](https://oxc.rs) when used in [rolldown-vite](https://vite.dev/guide/rolldown)) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```
