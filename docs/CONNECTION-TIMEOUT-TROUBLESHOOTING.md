# Connection timeout – what to check

Timeouts can mean the **whole site** won’t load, or only **API / game** calls fail.

---

## A. Whole site won’t load (browser spins, then “timed out”)

Often **Cloudflare Error 522** or similar.

1. **Test the origin without Cloudflare**  
   Open **http://187.77.186.84** in the browser.  
   - If this fails → problem is on the **LB** (Nginx down, firewall, wrong IP).  
   - If this works but **https://gunduata.club** fails → Cloudflare / DNS / SSL mode.

2. **Cloudflare**  
   See **[CLOUDFLARE-522-FIX.md](./CLOUDFLARE-522-FIX.md)**  
   - A record → **187.77.186.84**  
   - SSL mode **Flexible** if origin has no HTTPS on 443  
   - Firewall on LB: **80** and **443** open  

3. **On the LB**  
   ```bash
   ssh root@187.77.186.84
   systemctl status nginx
   ss -tlnp | grep ':80\|:443'
   ```

---

## B. Site loads but login / wallet / API errors (timeout after ~30s)

The app calls **https://gunduata.club/api/...** → Nginx on the LB → app servers **:8001**.

1. **Quick check from your PC**  
   ```bash
   curl -s -o /dev/null -w "%{http_code} time:%{time_total}s\n" --max-time 35 https://gunduata.club/api/maintenance/status/
   ```  
   - **000** or long hang → LB can’t reach backends or backends are down.  
   - **502 / 503** → Nginx up but **no healthy upstream** on 8001.

2. **On each app server** (72.61.254.71, 74, 41)  
   ```bash
   ss -tlnp | grep 8001
   curl -s -o /dev/null -w "%{http_code}\n" --max-time 5 http://127.0.0.1:8001/api/maintenance/status/
   ```  
   Start or fix the service that should listen on **8001**.

3. **From the LB** (can LB reach backends?)  
   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" --max-time 10 http://72.61.254.71:8001/api/maintenance/status/
   ```  
   If this times out → **firewall / routing** between LB and app servers, or app not bound to the right interface.

4. **Nginx upstream timeouts** (optional)  
   If backends are slow but healthy, add longer timeouts in the **`location /api/`** block on the LB (see `docs/nginx-gunduata.conf`).

---

## C. Game / WebSocket disconnects or times out

See **[WEBSOCKET-NOT-WORKING.md](./WEBSOCKET-NOT-WORKING.md)** and ensure **`location /ws/`** has long `proxy_read_timeout` / `proxy_send_timeout`.

---

## D. Client-side (website) timeout

The React app uses Axios with a **30s** default timeout (`website/src/api/http.ts`).  
If the API is consistently slower, you can raise that value after fixing the server (raising timeout alone does not fix a dead backend).

---

**Short checklist:** Origin **187.77.186.84:80** works? → Cloudflare DNS + SSL? → App servers **8001** up? → LB can curl each app server? → Nginx `app_backend` upstream correct?
