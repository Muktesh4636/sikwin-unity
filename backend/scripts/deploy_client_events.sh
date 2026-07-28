#!/usr/bin/env bash
# Deploy client click/error telemetry API to dice_game_web.
# Usage: DEPLOY_SSH_PASSWORD='...' ./backend/scripts/deploy_client_events.sh

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
  echo "==> Copying client-events files to ${APP_HOST}..."
  $SCP "${REPO_ROOT}/backend/accounts/client_events.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/client_events.py"
  $SCP "${REPO_ROOT}/backend/accounts/client_event_views.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/client_event_views.py"
  $SCP "${REPO_ROOT}/backend/accounts/migrations/0049_client_event.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/0049_client_event.py"
  $SCP "${SCRIPT_DIR}/patch_client_events.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/patch_client_events.py"

  echo "==> Installing into ${CONTAINER} on ${APP_HOST}..."
  $SSH "${DEPLOY_USER}@${APP_HOST}" bash -s <<EOF
set -e
docker cp /tmp/client_events.py ${CONTAINER}:/app/accounts/client_events.py
docker cp /tmp/client_event_views.py ${CONTAINER}:/app/accounts/client_event_views.py
docker cp /tmp/0049_client_event.py ${CONTAINER}:/app/accounts/migrations/0049_client_event.py
docker cp /tmp/patch_client_events.py ${CONTAINER}:/tmp/patch_client_events.py
docker exec ${CONTAINER} python3 /tmp/patch_client_events.py /app/dice_game/urls.py /app/accounts/admin.py
docker exec ${CONTAINER} python3 manage.py migrate accounts 0049_client_event --noinput
docker restart ${CONTAINER}
echo "Restarted ${CONTAINER} on ${APP_HOST}"
EOF
done

echo "==> Deploy complete. Test: curl -X POST https://gunduata.tech/api/client-events/ -H 'Content-Type: application/json' -d '{\"event_type\":\"error\",\"name\":\"deploy_test\",\"message\":\"ok\"}'"
