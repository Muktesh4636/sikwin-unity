# Why wss://gunduata.club/ws/game/ Was Not Working – and How to Fix It

## What was wrong

1. **Nginx had no `/ws/` location**  
   Requests to `wss://gunduata.club/ws/game/` were not proxied to the backend. They were handled by `location /` (e.g. SPA or 404), so the WebSocket upgrade never reached your app.

2. **WebSocket proxy was added but pointed at the wrong upstream**  
   After adding `location /ws/`, it was pointed at `ws_backend` (port **8232**). On your app servers, **8232 is closed**; only **8001** is open. So the proxy was trying to reach a service that is not listening.

## What was done on the server

- A **`location /ws/`** block was added to `/etc/nginx/conf.d/gunduata.club.conf` with:
  - `Upgrade` and `Connection` headers for WebSocket
  - `proxy_pass` set to **`http://ws_backend`** (port 8232).

Because nothing listens on 8232, you either get connection errors or no proper WebSocket handshake.

## Fix: Use the app that listens on 8001

Your backend (REST + WebSocket) runs on **port 8001** on the app servers. Nginx must proxy `/ws/` to that same app.

**On the Load Balancer (187.77.186.84), edit the config:**

```bash
sudo nano /etc/nginx/conf.d/gunduata.club.conf
```

In the **`location /ws/`** block, change:

```nginx
proxy_pass http://ws_backend;
```

to:

```nginx
proxy_pass http://app_backend;
```

So the block looks like:

```nginx
location /ws/ {
    proxy_pass http://app_backend;
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

Then:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## Backend requirement

The process listening on **8001** must support **WebSocket** on the path your game uses (e.g. `/ws/game/`). Many stacks (Django Channels, Node with ws, etc.) serve both HTTP and WebSocket on the same port. If your app only serves REST on 8001 and WebSocket on another port (e.g. 8232), then that other service must be running and reachable, and Nginx’s `proxy_pass` for `/ws/` must point to that port (e.g. an upstream that uses 8232).

## Summary

| Issue | Fix |
|-------|-----|
| No `/ws/` in Nginx | Added `location /ws/` with WebSocket headers. |
| `/ws/` pointed at 8232 (closed) | Change `proxy_pass` to `http://app_backend` (8001). |
| Backend must handle WS | Ensure the app on 8001 (or the correct port) serves WebSocket at `/ws/game/`. |

After this, `wss://gunduata.club/ws/game/` should work as long as the backend on 8001 actually handles WebSocket upgrades on that path.
