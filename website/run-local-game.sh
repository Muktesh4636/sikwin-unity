#!/usr/bin/env bash
# Serves the full stack on one origin: React SPA + Unity WebGL under /game/
set -e
cd "$(dirname "$0")"
echo ""
echo "Gundu Ata — local stack (same server)"
echo "  • Website (HTML/React):  http://localhost:5173/"
echo "  • WebGL game (direct):   http://localhost:5173/game/index.html"
echo "  • From app nav → game:   http://localhost:5173/ga"
echo ""
echo "API defaults to production unless you set VITE_API_BASE_URL in .env"
echo ""
exec npm run dev
