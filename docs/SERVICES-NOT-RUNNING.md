# Why the Backend / WebSocket Services Stopped Running – and How to Start Them Again

You said they **were running before** but **are not running now**. Here are the most likely reasons and what to do.

---

## 1. Different server: old vs new setup

- **Previously:** The Unity game used **159.198.46.36:8232** for API and WebSocket (`baseUrl`, `wsUrl` in GameApiClient). So the backend and WebSocket were likely running **on that machine**.
- **Now:** The site uses **gunduata.club** → Load Balancer **187.77.186.84** → app servers **72.61.254.71, 72.61.254.74, 72.62.226.41** on port **8001**.

So either:

- The **old server (159.198.46.36)** was turned off, or you moved traffic to the new LB + 72.x app servers. In that case the services need to run on the **new** app servers (72.x), and they may never have been started there or not set to start on boot.
- Or the backend is **still** on 159.198.46.36 and you only changed the website/domain; then 159.198.46.36 must stay up and reachable (and the site/Unity must point to it or go through gunduata.club which proxies to the right place).

So: “they’re not running” can mean **they’re not running on the machine you’re now using** (72.x or LB), or **the old machine (159.198.46.36) is off**.

---

## 2. Server reboot (most common)

If the app servers (72.x) or the old server **rebooted**, and the backend/WebSocket processes are **not** started by systemd/supervisor on boot, they will stay down until you start them again.

**Check and start on each app server (72.61.254.71, 72.61.254.74, 72.62.226.41):**

```bash
# SSH to one app server
ssh root@72.61.254.71   # or 74, 41

# See what’s listening on 8001 (and 8232 if you use it)
ss -tlnp | grep -E '8001|8232'

# List likely service names (adjust names to match your setup)
systemctl list-units --type=service --all | grep -E 'web|gunicorn|daphne|uvicorn|game|bet'

# If you use systemd, start and enable (example names – use the real ones)
sudo systemctl start web          # or: gunduata-web, api, etc.
sudo systemctl enable web         # so it starts on boot
sudo systemctl start game_timer
sudo systemctl enable game_timer
sudo systemctl start bet_worker
sudo systemctl enable bet_worker

# If you use a virtualenv and run manually, you might have a script like:
# cd /path/to/backend && source venv/bin/activate && daphne -p 8001 app.asgi:application
# Run that (or use systemd/supervisor to run it).
```

Repeat on the other two app servers. After that, check again:

```bash
ss -tlnp | grep 8001
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8001/api/maintenance/status/  # or any known path
```

---

## 3. Process crashed and no auto-restart

If there is **no** systemd unit or supervisor config to restart the app on crash, a one-off crash will leave the service down.

**Fix:** Run the backend under a process manager that restarts it:

- **systemd** – create a `.service` file with `Restart=on-failure` (or `always`).
- **supervisor** – add a `[program:web]` (and similar for game_timer, bet_worker) with `autorestart=true`.

Then start/enable the service so it starts on boot and restarts after crashes.

---

## 4. WebSocket on port 8232

Your Unity client used **8232** for WebSocket. On the **72.x** app servers we saw **8232 closed** and **8001 open**. So either:

- The backend on **8001** also serves WebSocket (same process). Then you only need the process on 8001 running, and Nginx should proxy `/ws/` to **app_backend** (8001) as in [WEBSOCKET-NOT-WORKING.md](WEBSOCKET-NOT-WORKING.md).
- Or a **separate** WebSocket process used to listen on **8232**. If that process is not running (or not installed on 72.x), 8232 will stay closed. Then you must start that process on each app server (or one server) and ensure Nginx’s `/ws/` proxy points to it (e.g. `ws_backend` with port 8232).

---

## 5. Quick checklist

| Check | Command / action |
|------|-------------------|
| Is anything listening on 8001 on app servers? | `ssh root@72.61.254.71 "ss -tlnp \| grep 8001"` |
| Is anything listening on 8232? | `ssh root@72.61.254.71 "ss -tlnp \| grep 8232"` |
| Did the server reboot recently? | `ssh root@72.61.254.71 "uptime"` |
| What service names exist? | On each app server: `systemctl list-units --all \| grep -E 'web|game|bet|daphne|gunicorn'` |
| Start and enable on boot | `systemctl start <service> && systemctl enable <service>` |

---

## Summary

- **“Previously running, now not”** usually means: **(1)** server reboot and services not enabled to start on boot, **(2)** process crashed and no restart policy, or **(3)** traffic moved to new servers (72.x) but the backend wasn’t started there (or the old host 159.198.46.36 was shut down).
- **What to do:** SSH to each app server (72.x), find the service names (systemd/supervisor or scripts), start them, and enable them on boot. If WebSocket is on 8232, start that process too and point Nginx `/ws/` to the correct upstream.

If you can share the exact service names or how you used to start the backend (e.g. `systemctl start X`, or a shell script), we can turn this into a one-command or script to run on each server.
