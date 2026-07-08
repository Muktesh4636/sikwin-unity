#!/usr/bin/env python3
"""Set gunduata.tech as primary domain in Django settings on the server."""
from pathlib import Path
import sys

SETTINGS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/app/dice_game/settings.py')

text = SETTINGS.read_text(encoding='utf-8')
changed = False

replacements = [
    (
        "'gunduata.club,www.gunduata.club,'\n    'gunduata.online,www.gunduata.online,'",
        "'gunduata.tech,www.gunduata.tech,'\n    'gunduata.club,www.gunduata.club,'\n    'gunduata.online,www.gunduata.online,'",
    ),
    (
        "# Primary domains: gunduata.club (+ legacy gunduata.online)",
        "# Primary domain: gunduata.tech (+ legacy gunduata.club / gunduata.online)",
    ),
]

for old, new in replacements:
    if old in text and new not in text:
        text = text.replace(old, new, 1)
        changed = True

# Prepend gunduata.tech to CORS if list starts with gunduata.club
if "'https://gunduata.club'," in text and text.find("'https://gunduata.tech',") > text.find("'https://gunduata.club',"):
    text = text.replace(
        "'https://gunduata.club',",
        "'https://gunduata.tech',\n    'https://www.gunduata.tech',\n    'https://gunduata.club',",
        1,
    )
    changed = True

if changed:
    SETTINGS.write_text(text, encoding='utf-8')
    print(f'Patched {SETTINGS}')
else:
    print(f'No changes needed: {SETTINGS}')
