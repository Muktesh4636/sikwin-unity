#!/bin/bash
# Run this script ON the Load Balancer (after SSH) to apply Nginx for gunduata.club.
# Includes HTTPS on 443 (Let's Encrypt) so Cloudflare "Full" / "Full (strict)" works (no 522).
#
# Prerequisites: cert exists at /etc/letsencrypt/live/gunduata.club/
#   certbot certonly --nginx --expand -d gunduata.club -d www.gunduata.club -d gunduata1.gunduata.club
#
# Usage on LB: copy docs/nginx-gunduata-lb-https.conf to /etc/nginx/conf.d/gunduata.club.conf
#   or: bash fix-nginx-gunduata-on-lb.sh

set -e
ROOT=/var/www/gunduata.club
NGINX_CONF=/etc/nginx/conf.d/gunduata.club.conf

mkdir -p "$ROOT"
chown -R www-data:www-data "$ROOT"
chmod -R 755 "$ROOT"
find "$ROOT" -type f -exec chmod 644 {} \;

cat > "$NGINX_CONF" << 'NGINX_EOF'
# gunduata.club – HTTPS + HTTP (ACME + redirect)
upstream app_backend {
    server 72.61.254.71:8001;
    server 72.61.254.74:8001;
    server 72.62.226.41:8001;
}

server {
    listen 80;
    listen [::]:80;
    server_name gunduata.club www.gunduata.club gunduata1.gunduata.club;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/gunduata.club;
        default_type "text/plain";
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name gunduata.club www.gunduata.club gunduata1.gunduata.club;

    ssl_certificate /etc/letsencrypt/live/gunduata.club/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/gunduata.club/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    root /var/www/gunduata.club;
    index index.html;

    location = /game/Build/WebGL.framework.js {
        gzip off;
        alias /var/www/gunduata.club/game/Build/WebGL.framework.js.gz;
        default_type application/javascript;
        add_header Content-Encoding gzip always;
        add_header Cache-Control "public, max-age=14400, no-transform" always;
    }
    location = /game/Build/WebGL.data {
        gzip off;
        alias /var/www/gunduata.club/game/Build/WebGL.data.gz;
        default_type application/octet-stream;
        add_header Content-Encoding gzip always;
        add_header Cache-Control "public, max-age=14400, no-transform" always;
    }
    location = /game/Build/WebGL.wasm {
        gzip off;
        alias /var/www/gunduata.club/game/Build/WebGL.wasm.gz;
        default_type application/wasm;
        add_header Content-Encoding gzip always;
        add_header Cache-Control "public, max-age=14400, no-transform" always;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://app_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 75s;
        proxy_send_timeout 120s;
        proxy_read_timeout 120s;
    }

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
}
NGINX_EOF

echo "Config written to $NGINX_CONF"
nginx -t && systemctl reload nginx
echo "Done. Use Cloudflare SSL: Full or Full (strict). https://gunduata.club"
