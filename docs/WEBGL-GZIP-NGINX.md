# Unity WebGL `.gz` + `Content-Encoding: gzip`

If the game shows:

> Unable to parse Build/WebGL.framework.js.gz! … web server … misconfigured to not serve the file with HTTP Response Header **Content-Encoding: gzip**

## Fix on the LB (Nginx)

Use **`docs/nginx-gunduata-lb-https.conf`** (or re-run **`docs/scripts/fix-nginx-gunduata-on-lb.sh`**) so these locations use **`alias`** to the `.gz` file, **`gzip off`**, and **`add_header Content-Encoding gzip always;`**.

Avoid **`rewrite` + `root`** for this; it can break headers or let Nginx gzip the body again.

After editing:

```bash
nginx -t && systemctl reload nginx
```

## Cloudflare

After fixing Nginx, **purge the cache** or users may keep a bad old object:

- **Caching** → **Configuration** → **Purge Everything**, or **Custom Purge** → URL prefix `https://gunduata.club/game/Build/`

Also:

- Turn **off Auto Minify** for **JavaScript** (Speed → Optimization).
- Optional: **Cache Rule** for URI Path starts with `/game/Build/` → **Bypass cache** (or **Standard** with respect origin `Cache-Control`).

**Note:** Cloudflare may **strip** the `Content-Encoding: gzip` header to the browser while still sending a **decompressed** body. That is usually fine for `WebGL.framework.js` (script tag). If Unity still errors, purge cache first; the failure is often **cached gzip bytes without the header** from an older misconfiguration.

Origin (direct to LB) should show:

```bash
curl -sI -H "Host: gunduata.club" --resolve gunduata.club:443:YOUR_LB_IP -k \
  "https://gunduata.club/game/Build/WebGL.framework.js" | grep -i content
```

Expect **`content-encoding: gzip`**.

## Verify (through Cloudflare)

```bash
curl -sI "https://gunduata.club/game/Build/WebGL.framework.js" | grep -i content
```

Headers may differ from origin; after purge, the game should load. If not, test **grey-cloud** (DNS only) temporarily to confirm origin behavior.
