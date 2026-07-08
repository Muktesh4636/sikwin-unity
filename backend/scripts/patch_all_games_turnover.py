#!/usr/bin/env python3
"""
Patch game/views.py so Coin Toss and Cricket bets also record
UserDailyTurnover (leaderboard) — same as Gundu Ata / Colour Game.
"""

from pathlib import Path
import sys

COIN_MARKER = "wallet.balance = wallet.balance - bet_amount + payout"
COIN_INSERT = """
            wallet.balance = wallet.balance - bet_amount + payout
            wallet.save(update_fields=['balance', 'updated_at'])

            try:
                from game.turnover import record_leaderboard_turnover
                record_leaderboard_turnover(request.user, bet_amount)
            except Exception as turnover_exc:
                logger.warning('coin_toss turnover record failed: %s', turnover_exc)
"""

# Original save line sits right after balance update — we replace that block carefully.
COIN_OLD = """            # Always deduct bet_amount first, then add payout if won
            wallet.balance = wallet.balance - bet_amount + payout
            wallet.save(update_fields=['balance', 'updated_at'])
"""

COIN_NEW = """            # Always deduct bet_amount first, then add payout if won
            wallet.balance = wallet.balance - bet_amount + payout
            wallet.save(update_fields=['balance', 'updated_at'])

            try:
                from game.turnover import record_leaderboard_turnover
                record_leaderboard_turnover(request.user, bet_amount)
            except Exception as turnover_exc:
                logger.warning('coin_toss turnover record failed: %s', turnover_exc)
"""

CRICKET_OLD = """            balance_before = wallet.balance
            wallet.balance -= stake
            wallet.save(update_fields=['balance', 'updated_at'])

            bet = CricketBet.objects.create(
"""

CRICKET_NEW = """            balance_before = wallet.balance
            wallet.balance -= stake
            wallet.save(update_fields=['balance', 'updated_at'])

            try:
                from game.turnover import record_leaderboard_turnover
                record_leaderboard_turnover(request.user, stake)
            except Exception as turnover_exc:
                logger.warning('cricket bet turnover record failed: %s', turnover_exc)

            bet = CricketBet.objects.create(
"""

COLOUR_OLD = """            balance_before = wallet.balance
            wallet.balance -= total_stake
            wallet.save(update_fields=['balance', 'updated_at'])

            created_bets = []
"""

COLOUR_NEW = """            balance_before = wallet.balance
            wallet.balance -= total_stake
            wallet.save(update_fields=['balance', 'updated_at'])

            try:
                from game.turnover import record_leaderboard_turnover
                record_leaderboard_turnover(request.user, total_stake)
            except Exception as turnover_exc:
                logger.warning('colour bet turnover record failed: %s', turnover_exc)

            created_bets = []
"""


def patch(content: str) -> str:
    if "record_leaderboard_turnover(request.user, bet_amount)" not in content:
        if COIN_OLD not in content:
            raise SystemExit("coin_toss_bet wallet update block not found")
        content = content.replace(COIN_OLD, COIN_NEW, 1)

    if "record_leaderboard_turnover(request.user, stake)" not in content:
        if CRICKET_OLD not in content:
            raise SystemExit("place_cricket_bet wallet update block not found")
        content = content.replace(CRICKET_OLD, CRICKET_NEW, 1)

    if "colour bet turnover record failed" not in content:
        if COLOUR_OLD not in content:
            raise SystemExit("colour_place_bet wallet update block not found")
        content = content.replace(COLOUR_OLD, COLOUR_NEW, 1)

    return content


def main() -> None:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "/app/game/views.py")
    text = path.read_text(encoding="utf-8")
    path.write_text(patch(text), encoding="utf-8")
    print(f"Patched turnover recording in {path}")


if __name__ == "__main__":
    main()
