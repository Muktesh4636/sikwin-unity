#!/usr/bin/env bash
# Deploy PayBitra UPI deposit integration to dice_game_web on app servers.
# Usage: DEPLOY_SSH_PASSWORD='...' ./backend/scripts/deploy_paybitra_deposit.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOY_USER="${DEPLOY_USER:-root}"
CONTAINER="${DICE_WEB_CONTAINER:-dice_game_web}"
APP_HOSTS="${DEPLOY_APP_HOSTS:-72.61.254.71 72.61.254.74}"

if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  SSH="sshpass -p ${DEPLOY_SSH_PASSWORD} ssh -o StrictHostKeyChecking=no"
  SCP="sshpass -p ${DEPLOY_SSH_PASSWORD} scp -o StrictHostKeyChecking=no"
else
  SSH="ssh -o StrictHostKeyChecking=no"
  SCP="scp -o StrictHostKeyChecking=no"
fi

for APP_HOST in $APP_HOSTS; do
  echo "==> Copying to ${APP_HOST}..."
  $SCP "${REPO_ROOT}/backend/accounts/paybitra_client.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/paybitra_client.py"
  $SCP "${SCRIPT_DIR}/patch_initiate_deposit.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/patch_initiate_deposit.py"
  $SCP "${SCRIPT_DIR}/patch_paybitra_settings.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/patch_paybitra_settings.py"

  echo "==> Installing into ${CONTAINER} on ${APP_HOST}..."
  $SSH "${DEPLOY_USER}@${APP_HOST}" bash -s <<EOF
set -e
docker cp /tmp/paybitra_client.py ${CONTAINER}:/app/accounts/paybitra_client.py
docker cp /tmp/patch_initiate_deposit.py ${CONTAINER}:/tmp/patch_initiate_deposit.py
docker cp /tmp/patch_paybitra_settings.py ${CONTAINER}:/tmp/patch_paybitra_settings.py
docker exec ${CONTAINER} python3 /tmp/patch_paybitra_settings.py /app/dice_game/settings.py
docker exec ${CONTAINER} python3 /tmp/patch_initiate_deposit.py /app/accounts/views.py
docker restart ${CONTAINER}
echo "Restarted ${CONTAINER} on ${APP_HOST}"
EOF
done

echo "==> Deploy complete."
