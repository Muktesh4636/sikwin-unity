#!/usr/bin/env bash
# Upload GunduAta.apk to the live site (gunduata.tech/GunduAta.apk).
#
#   ./deploy-apk-only.sh
#   DEPLOY_SSH_PASSWORD='...' ./deploy-apk-only.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

"$SCRIPT_DIR/copy-apk-for-download.sh"

LB_HOST="${DEPLOY_HOST:-72.62.226.41}"
DEPLOY_USER="${DEPLOY_USER:-root}"
REMOTE_PATH="${REMOTE_PATH:-/var/www/gunduata.tech}"
APK="${SCRIPT_DIR}/public/GunduAta.apk"

if [ ! -f "$APK" ]; then
  echo "ERROR: $APK not found"
  exit 1
fi

echo "==> Uploading $(du -h "$APK" | cut -f1) APK to ${DEPLOY_USER}@${LB_HOST}:${REMOTE_PATH}/GunduAta.apk"

if [ -n "${DEPLOY_SSH_PASSWORD}" ]; then
  if ! command -v sshpass &>/dev/null; then
    echo "ERROR: install sshpass (brew install sshpass)"
    exit 1
  fi
  sshpass -p "${DEPLOY_SSH_PASSWORD}" scp -o StrictHostKeyChecking=accept-new \
    "$APK" "${DEPLOY_USER}@${LB_HOST}:${REMOTE_PATH}/GunduAta.apk"
  sshpass -p "${DEPLOY_SSH_PASSWORD}" ssh -o StrictHostKeyChecking=accept-new \
    "${DEPLOY_USER}@${LB_HOST}" "chown www-data:www-data ${REMOTE_PATH}/GunduAta.apk"
else
  scp -o StrictHostKeyChecking=accept-new \
    "$APK" "${DEPLOY_USER}@${LB_HOST}:${REMOTE_PATH}/GunduAta.apk"
fi

echo "==> Done. Verify: curl -sI https://gunduata.tech/GunduAta.apk | grep -i content-length"
