#!/usr/bin/env python3
"""Patch dice_game/urls.py and game/models.py for Colour Game API."""
from pathlib import Path
import sys

URLS_PATH = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/app/dice_game/urls.py')
MODELS_PATH = Path(sys.argv[2]) if len(sys.argv) > 2 else Path('/app/game/models.py')

URL_MARKER = "path('api/colour/', include('game.colour_urls'))"
MODEL_MARKER = 'from game.colour_models import ColourRound, ColourBet'


def patch_urls():
    text = URLS_PATH.read_text(encoding='utf-8')
    if URL_MARKER in text:
        print(f'URLs already patched: {URLS_PATH}')
        return
    needle = "path('api/game/', include('game.urls')),"
    insert = (
        "    # Colour Game (WinGo-style)\n"
        "    path('api/colour/', include('game.colour_urls')),\n\n"
        "    # Game endpoints (api/game/)\n"
        "    path('api/game/', include('game.urls')),"
    )
    if needle not in text:
        raise SystemExit(f'Could not find anchor in {URLS_PATH}')
    text = text.replace(needle, insert, 1)
    URLS_PATH.write_text(text, encoding='utf-8')
    print(f'Patched {URLS_PATH}')


def patch_models():
    text = MODELS_PATH.read_text(encoding='utf-8')
    if MODEL_MARKER in text:
        print(f'Models already patched: {MODELS_PATH}')
        return
    text = text.rstrip() + '\n\n# Colour Game models\n' + MODEL_MARKER + '  # noqa: F401\n'
    MODELS_PATH.write_text(text, encoding='utf-8')
    print(f'Patched {MODELS_PATH}')


if __name__ == '__main__':
    patch_models()
    patch_urls()
