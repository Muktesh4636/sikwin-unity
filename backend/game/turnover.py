"""Shared daily leaderboard turnover recording for all games."""

from __future__ import annotations

from django.db.models import F

from accounts.models import Wallet
from game.models import UserDailyTurnover
from game.utils import get_leaderboard_period_date


def record_leaderboard_turnover(user, amount) -> None:
    """
    Add stake to wallet.turnover and UserDailyTurnover for the current
    leaderboard period. Used by Gundu Ata, Colour, Coin, Cricket, etc.
    """
    try:
        amount_int = int(amount)
    except (TypeError, ValueError):
        return
    if amount_int <= 0:
        return

    wallet = Wallet.objects.filter(user=user).first()
    if wallet:
        Wallet.objects.filter(pk=wallet.pk).update(turnover=F('turnover') + amount_int)

    period_date = get_leaderboard_period_date()
    udt, _ = UserDailyTurnover.objects.get_or_create(
        user=user,
        period_date=period_date,
        defaults={'turnover': 0},
    )
    UserDailyTurnover.objects.filter(pk=udt.pk).update(turnover=F('turnover') + amount_int)
