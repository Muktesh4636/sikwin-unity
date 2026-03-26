#!/usr/bin/env bash
# Deploy website (dist/) so https://gunduata.club serves the React app.
# When DEPLOY_SSH_PASSWORD is set, also fixes ownership (chown www-data) so Nginx can read files.
# To update the Download APK file before deploy: ./copy-apk-for-download.sh
#
# Usage:
#   ./deploy-to-server.sh              # Deploy to LB only (gunduata.club points here)
#   DEPLOY_TO_ALL=1 ./deploy-to-server.sh   # Deploy to LB + all 3 app servers
#   DEPLOY_SSH_PASSWORD='yourpass' ./deploy-to-server.sh
#
# Requires: npm (build), rsync, ssh. For password auth: sshpass (brew install sshpass).

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# LB is where gunduata.club points — Nginx here must use root /var/www/gunduata.club
LB_HOST="${DEPLOY_HOST:-187.77.186.84}"
# All four: LB + 3 app servers (optional)
APP1="${DEPLOY_APP1:-72.61.254.71}"
APP2="${DEPLOY_APP2:-72.61.254.74}"
APP3="${DEPLOY_APP3:-72.62.226.41}"

DEPLOY_USER="${DEPLOY_USER:-root}"
REMOTE_PATH="${REMOTE_PATH:-/var/www/gunduata.club}"
DEPLOY_TO_ALL="${DEPLOY_TO_ALL:-0}"

# Nginx runs as www-data; rsync from macOS often leaves 700 dirs + uid 501 — fix every deploy.
fix_web_permissions_remote() {
  local host="$1"
  local ssh_cmd=(ssh -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${host}")
  if [ -n "${DEPLOY_SSH_PASSWORD}" ]; then
    ssh_cmd=(sshpass -p "${DEPLOY_SSH_PASSWORD}" ssh -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${host}")
  fi
  "${ssh_cmd[@]}" "chown -R www-data:www-data ${REMOTE_PATH} && find ${REMOTE_PATH} -type d -exec chmod 755 {} \\; && find ${REMOTE_PATH} -type f -exec chmod 644 {} \\;"
}

# Unity requests WebGL.data / .wasm / .framework.js (no .gz). If only .gz exists, try_files falls through to SPA HTML → "Unknown data format".
uncompress_webgl_gz_remote() {
  local host="$1"
  local ssh_cmd=(ssh -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${host}")
  if [ -n "${DEPLOY_SSH_PASSWORD}" ]; then
    ssh_cmd=(sshpass -p "${DEPLOY_SSH_PASSWORD}" ssh -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${host}")
  fi
  "${ssh_cmd[@]}" "cd ${REMOTE_PATH}/game/Build 2>/dev/null && for f in WebGL.framework.js.gz WebGL.data.gz WebGL.wasm.gz; do [ -f \"\$f\" ] && zcat \"\$f\" > \"\${f%.gz}\" && chown www-data:www-data \"\${f%.gz}\"; done; true"
}

deploy_one() {
  local host="$1"
  echo "==> Deploying to ${DEPLOY_USER}@${host}:${REMOTE_PATH}"
  if [ -n "${DEPLOY_SSH_PASSWORD}" ]; then
    sshpass -p "${DEPLOY_SSH_PASSWORD}" ssh -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${host}" "mkdir -p ${REMOTE_PATH}"
    sshpass -p "${DEPLOY_SSH_PASSWORD}" rsync -avz --delete -e "ssh -o StrictHostKeyChecking=accept-new" dist/ "${DEPLOY_USER}@${host}:${REMOTE_PATH}/"
    uncompress_webgl_gz_remote "$host"
    fix_web_permissions_remote "$host"
  else
    ssh -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${host}" "mkdir -p ${REMOTE_PATH}"
    rsync -avz --delete -e ssh dist/ "${DEPLOY_USER}@${host}:${REMOTE_PATH}/"
    uncompress_webgl_gz_remote "$host"
    fix_web_permissions_remote "$host"
  fi
}

echo "==> Building website..."
if [ -f "$SCRIPT_DIR/node_modules/typescript/lib/tsc.js" ]; then
  node "$SCRIPT_DIR/node_modules/typescript/lib/tsc.js" -b && node "$SCRIPT_DIR/node_modules/vite/bin/vite.js" build
else
  npm run build
fi

if [ ! -d "dist" ]; then
  echo "ERROR: dist/ not found after build."
  exit 1
fi

if [ -n "${DEPLOY_SSH_PASSWORD}" ] && ! command -v sshpass &>/dev/null; then
  echo "ERROR: DEPLOY_SSH_PASSWORD is set but sshpass is not installed. Install with: brew install sshpass (macOS)"
  exit 1
fi

# Always deploy to LB (this is what gunduata.club hits)
deploy_one "$LB_HOST"

if [ "$DEPLOY_TO_ALL" = "1" ]; then
  deploy_one "$APP1"
  deploy_one "$APP2"
  deploy_one "$APP3"
fi

echo ""
echo "==> Deploy complete."
echo "    Site will be live at https://gunduata.club once Nginx on the LB is fixed."
echo ""
echo "    If you still see 'Roll with Royalty', on the LB run:"
echo "      ssh ${DEPLOY_USER}@${LB_HOST}"
echo "      # Edit Nginx so server_name gunduata.club has: root ${REMOTE_PATH}; and location / { try_files \$uri \$uri/ /index.html; }"
echo "      nginx -t && systemctl reload nginx"
echo "    See: docs/nginx-gunduata.conf"
