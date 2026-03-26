# Fix https://gunduata.club showing the correct site

If **https://gunduata.club** still shows the ["Roll with Royalty"](https://gunduata.club/) landing instead of the Gundu Ata React app, do these two steps in order.

---

## Step 1: Deploy the React site to the Load Balancer (from your computer)

From your machine (in the repo):

```bash
cd website
./deploy-to-server.sh
```

This builds the site and uploads it to the **LB** at `187.77.186.84` into `/var/www/gunduata.club`.

(Use `DEPLOY_SSH_PASSWORD='YourPassword' ./deploy-to-server.sh` if you use password auth.)

To deploy to the **LB and all three app servers**: `DEPLOY_TO_ALL=1 ./deploy-to-server.sh`

---

## Step 2: Fix Nginx on the Load Balancer so gunduata.club uses our folder

SSH into the Load Balancer (the server that gunduata.club points to):

```bash
ssh root@187.77.186.84
```

**Option A – Run the fix script (easiest)**  
Copy the script to the server and run it (from your computer you can do):

```bash
scp docs/scripts/fix-nginx-gunduata-on-lb.sh root@187.77.186.84:/tmp/
ssh root@187.77.186.84 'bash /tmp/fix-nginx-gunduata-on-lb.sh'
```

**Option B – Do it manually**  
On the LB, find where gunduata.club is configured:

```bash
grep -r "gunduata.club" /etc/nginx/
```

- If there is a **server** block for `gunduata.club` in that file, edit it and set **root** to `/var/www/gunduata.club;` and **location /** to `try_files $uri $uri/ /index.html;`.
- If you prefer a clean config, create `/etc/nginx/conf.d/gunduata.club.conf` with the contents of **`docs/nginx-gunduata.conf`** (the full `server { ... }` and `upstream app_backend { ... }` blocks).

Then run:

```bash
nginx -t && systemctl reload nginx
```

**If the old site still appears:** Another config might be defining gunduata.club first. Disable or remove the old one (e.g. rename or remove the file in `sites-enabled` that contains the "Roll with Royalty" root) so only the config pointing to `/var/www/gunduata.club` is active.

---

## Game admin at `/game-admin/` shows the Unity game instead

That usually means Nginx has a **`location /game`** block **without** a trailing slash. In Nginx, that prefix matches **`/game-admin`** as well as `/game/...`, so admin URLs are served from the WebGL folder (or the wrong file).

**Fix:**

1. Replace any `location /game { ... }` with **`location /game/`** or **`location ^~ /game/`** so only paths under `/game/` are affected.
2. Add an explicit backend route **before** `location /`, as in **`docs/nginx-gunduata-lb-https.conf`**:

   `location ^~ /game-admin { proxy_pass http://app_backend; ... }`

   Then `nginx -t && systemctl reload nginx`.

3. In **Cloudflare**, disable any rule that rewrites all HTML to `/index.html` for the whole zone (that would force the React SPA on `/game-admin/` too).

---

## Step 3: Check

Open **https://gunduata.club** in your browser. You should see the Gundu Ata React app (login, home, wallet, etc.), not the "Roll with Royalty" page.

---

**Why the wrong site appears:** The domain is served by the Load Balancer. Nginx there was (and may still be) using a different **root** folder for that domain. After deploying our build to `/var/www/gunduata.club` and making Nginx use that as `root` for gunduata.club, the correct app is served.
