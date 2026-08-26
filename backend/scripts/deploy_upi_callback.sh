#!/usr/bin/env bash
# Deploy POST auth/deposits/upi-callback/ to app servers.
#   DEPLOY_SSH_PASSWORD='...' ./deploy_upi_callback.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP1="${DEPLOY_APP1:-72.61.254.71}"
APP2="${DEPLOY_APP2:-72.61.254.74}"
DEPLOY_USER="${DEPLOY_USER:-root}"
CONTAINER="${DEPLOY_CONTAINER:-dice_game_web}"

deploy_one() {
  local host="$1"
  echo "==> Patching upi-callback on ${host}"
  if [ -n "${DEPLOY_SSH_PASSWORD}" ]; then
    sshpass -p "${DEPLOY_SSH_PASSWORD}" scp -o StrictHostKeyChecking=accept-new \
      "${SCRIPT_DIR}/patch_upi_callback.py" "${DEPLOY_USER}@${host}:/tmp/patch_upi_callback.py"
    sshpass -p "${DEPLOY_SSH_PASSWORD}" ssh -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${host}" bash -s <<EOF
docker cp /tmp/patch_upi_callback.py ${CONTAINER}:/tmp/patch_upi_callback.py
docker exec ${CONTAINER} python3 /tmp/patch_upi_callback.py /app/accounts/views.py /app/accounts/urls.py /app/dice_game/urls.py
docker restart ${CONTAINER} >/dev/null
echo "Restarted ${CONTAINER} on ${host}"
EOF
  else
    scp -o StrictHostKeyChecking=accept-new \
      "${SCRIPT_DIR}/patch_upi_callback.py" "${DEPLOY_USER}@${host}:/tmp/patch_upi_callback.py"
    ssh -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${host}" bash -s <<EOF
docker cp /tmp/patch_upi_callback.py ${CONTAINER}:/tmp/patch_upi_callback.py
docker exec ${CONTAINER} python3 /tmp/patch_upi_callback.py /app/accounts/views.py /app/accounts/urls.py /app/dice_game/urls.py
docker restart ${CONTAINER} >/dev/null
echo "Restarted ${CONTAINER} on ${host}"
EOF
  fi
}

deploy_one "$APP1"
deploy_one "$APP2"
echo "==> Done. Test: POST /api/auth/deposits/upi-callback/ with JWT + session_id/utr/status"
