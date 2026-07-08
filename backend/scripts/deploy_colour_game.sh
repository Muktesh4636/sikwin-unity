#!/usr/bin/env bash
# Deploy Colour Game API + worker to dice_game_web on app servers.
# Uses existing game_colourround / game_colourbet tables (managed=False models).
# Usage: DEPLOY_SSH_PASSWORD='...' ./backend/scripts/deploy_colour_game.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOY_USER="${DEPLOY_USER:-root}"
CONTAINER="${DICE_WEB_CONTAINER:-dice_game_web}"
APP_HOSTS="${DEPLOY_APP_HOSTS:-72.61.254.71 72.61.254.74}"

FILES=(
  "backend/game/colour_models.py"
  "backend/game/colour_engine.py"
  "backend/game/colour_views.py"
  "backend/game/colour_urls.py"
  "backend/scripts/colour_round_worker.py"
  "backend/scripts/patch_colour_game.py"
)

if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  SSH="sshpass -p ${DEPLOY_SSH_PASSWORD} ssh -o StrictHostKeyChecking=no"
  SCP="sshpass -p ${DEPLOY_SSH_PASSWORD} scp -o StrictHostKeyChecking=no"
else
  SSH="ssh -o StrictHostKeyChecking=no"
  SCP="scp -o StrictHostKeyChecking=no"
fi

for APP_HOST in $APP_HOSTS; do
  echo "==> Copying Colour Game files to ${APP_HOST}..."
  for rel in "${FILES[@]}"; do
    dest="/tmp/$(basename "$rel")"
    $SCP "${REPO_ROOT}/${rel}" "${DEPLOY_USER}@${APP_HOST}:${dest}"
  done

  echo "==> Installing into ${CONTAINER} on ${APP_HOST}..."
  $SSH "${DEPLOY_USER}@${APP_HOST}" bash -s <<EOF
set -e
docker cp /tmp/colour_models.py ${CONTAINER}:/app/game/colour_models.py
docker cp /tmp/colour_engine.py ${CONTAINER}:/app/game/colour_engine.py
docker cp /tmp/colour_views.py ${CONTAINER}:/app/game/colour_views.py
docker cp /tmp/colour_urls.py ${CONTAINER}:/app/game/colour_urls.py
docker cp /tmp/colour_round_worker.py ${CONTAINER}:/app/colour_round_worker.py
docker cp /tmp/patch_colour_game.py ${CONTAINER}:/tmp/patch_colour_game.py

docker exec ${CONTAINER} python3 /tmp/patch_colour_game.py /app/dice_game/urls.py /app/game/models.py

# Stop any existing colour worker (python one-liner — pkill not in container)
docker exec ${CONTAINER} python3 -c "
import os, signal, subprocess
out = subprocess.check_output(['ps','aux'], text=True)
for line in out.splitlines():
    if 'colour_round_worker.py' in line and 'python3 -c' not in line:
        pid = int(line.split()[1])
        try: os.kill(pid, signal.SIGTERM)
        except ProcessLookupError: pass
" 2>/dev/null || true

docker restart ${CONTAINER}
sleep 5
docker exec -d ${CONTAINER} python3 /app/colour_round_worker.py
echo "Restarted ${CONTAINER} + colour worker on ${APP_HOST}"
EOF
done

echo "==> Deploy complete. Test: curl https://gunduata.tech/api/colour/round/"
