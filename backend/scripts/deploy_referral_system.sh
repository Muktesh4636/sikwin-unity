#!/usr/bin/env bash
# Deploy new referral rewards system to dice_game_web on app servers.
# Usage: DEPLOY_SSH_PASSWORD='Gunduata@123' ./backend/scripts/deploy_referral_system.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOY_USER="${DEPLOY_USER:-root}"
CONTAINER="${DICE_WEB_CONTAINER:-dice_game_web}"
DAILY_CONTAINER="${DICE_DAILY_CONTAINER:-dice_game_daily_reset}"
APP_HOSTS="${DEPLOY_APP_HOSTS:-72.62.226.41 72.61.254.71 72.61.254.74}"

if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  SSH="sshpass -p ${DEPLOY_SSH_PASSWORD} ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no"
  SCP="sshpass -p ${DEPLOY_SSH_PASSWORD} scp -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no"
else
  SSH="ssh -o StrictHostKeyChecking=no"
  SCP="scp -o StrictHostKeyChecking=no"
fi

for APP_HOST in $APP_HOSTS; do
  echo "==> Copying referral files to ${APP_HOST}..."
  $SCP "${REPO_ROOT}/backend/accounts/referral_logic.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/referral_logic.py"
  $SCP "${REPO_ROOT}/backend/accounts/migrations/0048_referral_rewards_system.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/0048_referral_rewards_system.py"
  $SCP "${REPO_ROOT}/backend/accounts/management/commands/process_referral_daily_commission.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/process_referral_daily_commission.py"
  $SCP "${REPO_ROOT}/backend/accounts/management/commands/backfill_wallet_snapshots.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/backfill_wallet_snapshots.py"
  $SCP "${SCRIPT_DIR}/patch_referral_system.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/patch_referral_system.py"

  echo "==> Installing into ${CONTAINER} on ${APP_HOST}..."
  $SSH "${DEPLOY_USER}@${APP_HOST}" bash -s <<EOF
set -e
docker cp /tmp/referral_logic.py ${CONTAINER}:/app/accounts/referral_logic.py
docker cp /tmp/0048_referral_rewards_system.py ${CONTAINER}:/app/accounts/migrations/0048_referral_rewards_system.py
docker exec ${CONTAINER} mkdir -p /app/accounts/management/commands
docker cp /tmp/process_referral_daily_commission.py ${CONTAINER}:/app/accounts/management/commands/process_referral_daily_commission.py
docker cp /tmp/backfill_wallet_snapshots.py ${CONTAINER}:/app/accounts/management/commands/backfill_wallet_snapshots.py
docker cp /tmp/patch_referral_system.py ${CONTAINER}:/tmp/patch_referral_system.py
docker exec ${CONTAINER} python3 /tmp/patch_referral_system.py /app
docker exec ${CONTAINER} python manage.py migrate accounts --noinput
docker exec ${CONTAINER} python manage.py backfill_wallet_snapshots --days 3
docker restart ${CONTAINER}
echo "Restarted ${CONTAINER} on ${APP_HOST}"

# Add nightly commission to daily_reset loop (00:00 IST = 18:30 UTC)
if docker ps --format '{{.Names}}' | grep -q '^${DAILY_CONTAINER}\$'; then
  docker exec ${DAILY_CONTAINER} sh -c 'grep -q process_referral_daily_commission /proc/1/cmdline 2>/dev/null || true'
  # Patch loop script if not already present
  docker exec ${DAILY_CONTAINER} sh -c 'cat /proc/1/cmdline | tr "\\0" " "'
fi
EOF
done

echo "==> Deploy complete. Schedule: run 'python manage.py process_referral_daily_commission' nightly at 00:05 IST."
