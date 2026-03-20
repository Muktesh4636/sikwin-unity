#!/bin/bash
# Fix 403 Forbidden on gunduata.club by setting correct ownership and permissions.
# Nginx runs as www-data and must be able to read all files under the web root.
#
# Run ON the Load Balancer (after SSH):
#   sudo bash fix-403-gunduata-permissions.sh
#
# Or from your machine (one-liner):
#   ssh root@187.77.186.84 'bash -s' < docs/scripts/fix-403-gunduata-permissions.sh

set -e
ROOT="${1:-/var/www/gunduata.club}"

echo "==> Fixing ownership and permissions for $ROOT"
chown -R www-data:www-data "$ROOT"
chmod -R 755 "$ROOT"
find "$ROOT" -type f -exec chmod 644 {} \;
echo "==> Done. Reload the site; 403 should be gone."
