#!/usr/bin/env python3
"""Append PayBitra settings to dice_game/settings.py if missing."""

from pathlib import Path
import sys

BLOCK = """

# PayBitra UPI deposit gateway (override via environment variables in production)
PAYBITRA_API_BASE = os.getenv('PAYBITRA_API_BASE', 'https://api.paybitra.com/v1')
PAYBITRA_TRANSACTION_KEY = os.getenv(
    'PAYBITRA_TRANSACTION_KEY',
    'e307025fb0d03da817aebad640ad6891d0f477e3216b7ebe1b3e81580ed63715',
)
PAYBITRA_USER_ID = os.getenv('PAYBITRA_USER_ID', 'PS')
PAYBITRA_CODE = os.getenv('PAYBITRA_CODE', 'PS247')
PAYBITRA_OT = os.getenv('PAYBITRA_OT', 'n')
PAYBITRA_ROLE_TOKEN = os.getenv(
    'PAYBITRA_ROLE_TOKEN',
    '7a7b6dbc-71ef-4b0c-985b-7631946951ef',
)
"""


def main() -> None:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "/app/dice_game/settings.py")
    text = path.read_text(encoding="utf-8")
    if "PAYBITRA_TRANSACTION_KEY" in text:
        print(f"PayBitra settings already present in {path}")
        return
    path.write_text(text.rstrip() + BLOCK + "\n", encoding="utf-8")
    print(f"Appended PayBitra settings to {path}")


if __name__ == "__main__":
    main()
