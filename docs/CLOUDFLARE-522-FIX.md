# Fix Cloudflare Error 522 (Connection timed out)

**Error 522** means Cloudflare cannot connect to your **origin server** (the machine that hosts gunduata.club). The browser and Cloudflare work; the origin does not respond in time.

---

## Fast check (most common fix)

If **`http://187.77.186.84`** loads the site but **`https://gunduata.club`** shows **522**, your LB is almost certainly only listening on **port 80** (HTTP), while Cloudflare is set to **Full** or **Full (strict)** and tries **HTTPS on port 443** to the origin. Nothing answers → timeout → **522**.

**Fix:** In Cloudflare → **SSL/TLS** → **Overview**, set encryption to **Flexible**  
(visitors still use HTTPS to Cloudflare; Cloudflare talks to your server over **HTTP port 80**).

Long-term, add HTTPS on the origin (e.g. Let’s Encrypt on Nginx) and then use **Full (strict)**.

**Origin HTTPS is configured:** Nginx on the LB listens on **443** with Let’s Encrypt. Use **`docs/nginx-gunduata-lb-https.conf`** or **`docs/scripts/fix-nginx-gunduata-on-lb.sh`** after deploys so port 443 is not removed. In Cloudflare set SSL to **Full (strict)** once the origin cert is valid.

---

**Already fixed on origin:** Web root permissions were corrected (`chmod 755`, `chown www-data`) so Nginx returns 200. The gunduata.club server block is set as `default_server`.

## 1. Use Flexible SSL in Cloudflare (if you don’t have SSL on the origin)

If your origin **does not** have HTTPS on port 443, Cloudflare must use **HTTP** to talk to it.

- In **Cloudflare Dashboard** → **SSL/TLS** → **Overview**
- Set encryption mode to **Flexible** (Cloudflare → visitor is HTTPS, Cloudflare → origin is HTTP on port 80).

If you leave it on **Full** or **Full (strict)**, Cloudflare will try to connect to your origin on **port 443**; if nothing is listening there, you get 522.

## 2. Check Cloudflare DNS (origin IP)

In **Cloudflare Dashboard** → **DNS** → **Records** for gunduata.club:

- The **A record** for `gunduata.club` (and `www` if you use it) must point to your **origin server’s public IP**.
- For your setup that is: **`187.77.186.84`** (the LB).
- If the A record points to another IP or is wrong, Cloudflare will try to connect to the wrong server and you’ll get 522. Fix the A record to `187.77.186.84`.

## 3. Open port 80 (and 443) on the origin server

Cloudflare connects to your origin on **port 80** (and 443 if you use HTTPS). If a firewall on the LB allows SSH but blocks HTTP, you get 522.

**On the LB (187.77.186.84):**

```bash
ssh root@187.77.186.84
# If you use ufw:
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status
sudo ufw reload   # or: enable and reload if needed

# Confirm Nginx is listening on port 80:
ss -tlnp | grep :80
# or: netstat -tlnp | grep :80
```

You should see nginx listening on `0.0.0.0:80` (or `*:80`). If not, restart Nginx: `systemctl restart nginx`.

## 4. Test the origin directly

Open **http://187.77.186.84** in your browser (no Cloudflare). If the React app loads, the origin is fine and the issue is Cloudflare ↔ origin (DNS, SSL mode, or firewall). If it doesn’t load, the problem is on the server or network.

## 5. Optional: Test without Cloudflare proxy (DNS only)

In Cloudflare DNS, temporarily set the **A record to “DNS only”** (grey cloud) so traffic goes straight to the origin. Then open **http://gunduata.club** (or http://187.77.186.84). If the site loads, the origin and Nginx are fine and the issue is between Cloudflare and the origin (often firewall or wrong IP). After testing, switch the record back to **Proxied** (orange cloud) if you want Cloudflare in front again.

## 6. Cloudflare SSL/TLS (if you use HTTPS)

In **SSL/TLS** → **Overview**, if the encryption mode is **Full** or **Full (strict)**, the origin must accept HTTPS on port 443. If the origin only has HTTP on 80, use **Flexible** temporarily, or set up SSL on the origin and use **Full**.

---

**Summary:** For 522, check: (1) **SSL mode** = Flexible if the origin has no HTTPS, (2) **A record** = `187.77.186.84`, (3) origin **firewall** allows 80/443. Test the origin directly: **http://187.77.186.84**.
