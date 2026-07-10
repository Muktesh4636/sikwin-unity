#!/usr/bin/env python3
"""Add ₹500 minimum for PayBitra initiate_deposit on server."""

from pathlib import Path
import sys

INSERT = """
    if amount < 500:
        return Response(
            {'error': 'Use standard payment methods for deposits below ₹500.'},
            status=status.HTTP_400_BAD_REQUEST,
        )
"""

MARKER = "if amount < 100:"
INSERT_AFTER = "        return Response({'error': 'Minimum deposit amount is ₹100'}, status=status.HTTP_400_BAD_REQUEST)"


def main() -> None:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "/app/accounts/views.py")
    text = path.read_text(encoding="utf-8")
    if "amount < 500" in text and "Use standard payment methods" in text:
        print(f"Already patched: {path}")
        return
    idx = text.find("def initiate_deposit(request):")
    if idx == -1:
        raise SystemExit("initiate_deposit not found")
    block = text[idx:]
    pos = block.find(INSERT_AFTER)
    if pos == -1:
        raise SystemExit("initiate_deposit min-100 block not found")
    end = idx + pos + len(INSERT_AFTER)
    text = text[:end] + INSERT + text[end:]
    path.write_text(text, encoding="utf-8")
    print(f"Patched {path}")


if __name__ == "__main__":
    main()
