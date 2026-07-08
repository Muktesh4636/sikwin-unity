#!/usr/bin/env bash
# Deploy display-only fake leaderboard to dice_game_web on app servers.
# Usage: DEPLOY_SSH_PASSWORD='...' ./backend/scripts/deploy_leaderboard_display.sh

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
  $SCP "${REPO_ROOT}/backend/game/leaderboard_display.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/leaderboard_display.py"
  $SCP "${SCRIPT_DIR}/patch_leaderboard_view.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/patch_leaderboard_view.py"

  echo "==> Installing into ${CONTAINER} on ${APP_HOST}..."
  $SSH "${DEPLOY_USER}@${APP_HOST}" bash -s <<EOF
set -e
docker cp /tmp/leaderboard_display.py ${CONTAINER}:/app/game/leaderboard_display.py
docker cp /tmp/patch_leaderboard_view.py ${CONTAINER}:/tmp/patch_leaderboard_view.py
docker exec ${CONTAINER} python3 /tmp/patch_leaderboard_view.py /app/accounts/views.py
docker restart ${CONTAINER}
echo "Restarted ${CONTAINER} on ${APP_HOST}"
EOF
done

echo "==> Deploy complete."
