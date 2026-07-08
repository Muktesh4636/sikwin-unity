#!/usr/bin/env bash
# Deploy multi-game leaderboard turnover to dice_game_web.
# Usage: DEPLOY_SSH_PASSWORD='...' ./backend/scripts/deploy_all_games_turnover.sh

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
  $SCP "${REPO_ROOT}/backend/game/turnover.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/turnover.py"
  $SCP "${SCRIPT_DIR}/patch_all_games_turnover.py" "${DEPLOY_USER}@${APP_HOST}:/tmp/patch_all_games_turnover.py"

  echo "==> Installing into ${CONTAINER} on ${APP_HOST}..."
  $SSH "${DEPLOY_USER}@${APP_HOST}" bash -s <<EOF
set -e
docker cp /tmp/turnover.py ${CONTAINER}:/app/game/turnover.py
docker cp /tmp/patch_all_games_turnover.py ${CONTAINER}:/tmp/patch_all_games_turnover.py
docker exec ${CONTAINER} python3 /tmp/patch_all_games_turnover.py /app/game/views.py
# Align colour game helper with shared module (idempotent)
docker exec ${CONTAINER} python3 - <<'PY'
from pathlib import Path
path = Path('/app/game/colour_engine.py')
text = path.read_text(encoding='utf-8')
old = '''def _record_turnover(user, amount: int):
    wallet = Wallet.objects.filter(user=user).first()
    if wallet:
        Wallet.objects.filter(pk=wallet.pk).update(turnover=F('turnover') + int(amount))
    period_date = get_leaderboard_period_date()
    udt, _ = UserDailyTurnover.objects.get_or_create(
        user=user,
        period_date=period_date,
        defaults={'turnover': 0},
    )
    UserDailyTurnover.objects.filter(pk=udt.pk).update(turnover=F('turnover') + int(amount))
'''
new = '''def _record_turnover(user, amount: int):
    from game.turnover import record_leaderboard_turnover
    record_leaderboard_turnover(user, amount)
'''
if old in text:
    text = text.replace(old, new, 1)
    path.write_text(text, encoding='utf-8')
    print('Updated colour_engine._record_turnover to shared helper')
elif 'from game.turnover import record_leaderboard_turnover' in text:
    print('colour_engine already uses shared helper')
else:
    print('WARNING: colour_engine _record_turnover block not matched; left unchanged')
PY
docker restart ${CONTAINER}
echo "Restarted ${CONTAINER} on ${APP_HOST}"
EOF
done

echo "==> Deploy complete."
