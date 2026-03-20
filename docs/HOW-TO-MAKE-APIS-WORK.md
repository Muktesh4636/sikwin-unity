# How to Make the APIs Work (REST + WebSocket)

For the website and Unity WebGL game at **https://gunduata.club** to work, the backend APIs (REST and WebSocket) must be running and reachable through the Load Balancer.

---

## 1. Backend must be running on the app servers

The app servers (72.61.254.71, 72.61.254.74, 72.62.226.41) must run your backend service(s):

| Service      | Port | Purpose |
|-------------|------|--------|
| **web**     | 8001 | REST API (auth, wallet, deposits, game, etc.) and optionally WebSocket |
| **WebSocket** | Same app on 8001, or a separate service on e.g. 8232 | Real-time game (rounds, bets) for the Unity game |

- **REST:** The website and game call `https://gunduata.club/api/...`. Nginx on the LB forwards `/api/` to the app servers on **port 8001**. So the process listening on 8001 must expose the REST endpoints (e.g. `/api/auth/login/`, `/api/auth/wallet/`, `/api/game/...`).
- **WebSocket:** The Unity game connects to a WebSocket URL (e.g. `wss://gunduata.club/ws/game/`). The backend must handle WebSocket at that path; Nginx must proxy with `Upgrade` and `Connection` headers (see below).

**Check that the backend is up:**

```bash
# From your machine or LB
curl -s -o /dev/null -w "%{http_code}" https://gunduata.club/api/maintenance/status/
# Expect 200 (or 404 if that route is missing; 502/503 means backend down)
```

On each app server you can check the process and port:

```bash
ssh root@72.61.254.71  # or 74, 41
ss -tlnp | grep 8001   # or: netstat -tlnp | grep 8001
# Your "web" or API process should be listening on 8001
```

---

## 2. Nginx on the Load Balancer (LB)

The LB (187.77.186.84) must:

1. **Serve the static site** from `/var/www/gunduata.club` (React + game assets).
2. **Proxy `/api/`** to the app servers (port 8001).
3. **Proxy WebSocket** (e.g. `/ws/`) to the backend with `Upgrade` and `Connection` so the game’s real-time connection works.

Example for the **API** (you should already have this):

```nginx
location /api/ {
    proxy_pass http://app_backend;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Example for **WebSocket** (add a block like this if your backend serves WS at `/ws/`):

```nginx
location /ws/ {
    proxy_pass http://app_backend;   # or http://ws_backend if WebSocket is on another port
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 86400;
    proxy_send_timeout 86400;
}
```

If WebSocket runs on a different port (e.g. 8232), define an upstream and use it:

```nginx
upstream ws_backend {
    server 72.61.254.71:8232;
    server 72.61.254.74:8232;
    server 72.62.226.41:8232;
}
# then: proxy_pass http://ws_backend;
```

After editing the config:

```bash
nginx -t && systemctl reload nginx
```

Use the script that already includes the WebSocket block (see below):  
`docs/scripts/fix-nginx-gunduata-on-lb.sh` (or the snippet in `docs/nginx-gunduata.conf`).

---

## 3. Website configuration (frontend)

The React app uses **API_BASE_URL** for all REST calls. Default:

- **Production:** `https://gunduata.club/api/` (in `website/src/config.ts`).

So when users open **https://gunduata.club**, the site calls `https://gunduata.club/api/auth/login/`, `.../api/auth/wallet/`, etc. No change needed if the backend is reachable at that URL.

For **local development** against a remote API:

```bash
# website/.env
VITE_API_BASE_URL=https://gunduata.club/api/
```

Then `npm run dev` will still use the live API.

---

## 4. Unity WebGL game (WebSocket URL)

The Unity game uses a **WebSocket URL** (e.g. for real-time rounds). In the Unity project, `GameApiClient` has a field like:

- `wsUrl = "ws://159.198.46.36:8232/ws/game/"`

When the game runs **in the browser** at **https://gunduata.club**, it must use the same host and HTTPS:

- Use **wss://** (not ws://) for HTTPS pages.
- Use the same domain so Nginx can proxy: e.g. **wss://gunduata.club/ws/game/**.

So either:

- Build the WebGL game with `wsUrl` set to **wss://gunduata.club/ws/game/** (or leave it configurable), and ensure the backend and Nginx serve WebSocket at `/ws/game/`, or  
- Have the game read the base URL from the page (e.g. from `window.location`) and build the WebSocket URL from that so it works on any domain.

---

## 5. Quick checklist

| Step | What to do |
|------|------------|
| 1 | Backend “web” (and WS if separate) running on app servers (8001, and 8232 if used). |
| 2 | Nginx on LB: `root /var/www/gunduata.club`, `location /api/` → app_backend, `location /ws/` → backend with Upgrade/Connection. |
| 3 | Website: API base = `https://gunduata.club/api/` (default). |
| 4 | Unity: WebSocket URL = `wss://gunduata.club/ws/game/` (or derived from site URL) when playing on gunduata.club. |
| 5 | Test: open https://gunduata.club, login, open game; check Network tab for `/api/` (200) and WebSocket (101). |

If **REST works but WebSocket fails**, check Nginx has the `/ws/` block with `Upgrade` and `Connection`, and that the backend actually listens for WebSocket on that path.
