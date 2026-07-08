"""Colour Game round lifecycle — uses existing DB tables (60s rounds, 30s betting window)."""
from __future__ import annotations

import random
import re
from datetime import timedelta
from decimal import Decimal

from django.db import transaction
from django.db.models import F
from django.utils import timezone

from accounts.models import Transaction, Wallet
from game.colour_models import ColourBet, ColourRound
from game.models import UserDailyTurnover
from game.utils import get_leaderboard_period_date

ROUND_DURATION_SECONDS = 60
BETTING_WINDOW_SECONDS = 30

NUMBER_TO_RESULT = {
    0: 'red_violet',
    1: 'green',
    2: 'red',
    3: 'green',
    4: 'red',
    5: 'green_violet',
    6: 'red',
    7: 'green',
    8: 'red',
    9: 'green',
}

PAYOUT_MULTIPLIERS = {
    'green': Decimal('2'),
    'red': Decimal('2'),
    'violet': Decimal('4.5'),
    'number': Decimal('9'),
}


def _now():
    return timezone.now()


def _round_end(round_obj: ColourRound) -> timezone.datetime:
    if round_obj.end_time:
        return round_obj.end_time
    return round_obj.start_time + timedelta(seconds=ROUND_DURATION_SECONDS)


def _round_close(round_obj: ColourRound) -> timezone.datetime:
    if round_obj.close_time:
        return round_obj.close_time
    return round_obj.start_time + timedelta(seconds=BETTING_WINDOW_SECONDS)


def _seconds_remaining(round_obj: ColourRound, now=None) -> int:
    now = now or _now()
    delta = (_round_end(round_obj) - now).total_seconds()
    return max(0, int(delta))


def _betting_open(round_obj: ColourRound, now=None) -> bool:
    now = now or _now()
    if round_obj.status != 'BETTING':
        return False
    return now < _round_close(round_obj)


def _generate_round_id() -> str:
    last = ColourRound.objects.order_by('-id').values_list('round_id', flat=True).first()
    if last:
        m = re.search(r'(\d+)$', last)
        if m:
            return f'R{int(m.group(1)) + 1}'
    return f'R{int(_now().timestamp())}'


def _side_wins(bet_on: str, result_key: str, result_number: int, bet_number: int | None) -> bool:
    if bet_on == 'number':
        return bet_number is not None and bet_number == result_number
    if bet_on == 'green':
        return 'green' in result_key
    if bet_on == 'red':
        return 'red' in result_key
    if bet_on == 'violet':
        return 'violet' in result_key
    return False


def _record_turnover(user, amount: int):
    from game.turnover import record_leaderboard_turnover
    record_leaderboard_turnover(user, amount)


def settle_round(round_obj: ColourRound) -> ColourRound:
    if round_obj.status == 'COMPLETED':
        return round_obj

    result_number = random.randint(0, 9)
    result_key = NUMBER_TO_RESULT[result_number]
    now = _now()
    end = _round_end(round_obj)
    close = _round_close(round_obj)

    with transaction.atomic():
        round_obj = ColourRound.objects.select_for_update().get(pk=round_obj.pk)
        if round_obj.status == 'COMPLETED':
            return round_obj

        round_obj.number = result_number
        round_obj.result = result_key
        round_obj.status = 'COMPLETED'
        round_obj.result_time = now
        round_obj.end_time = round_obj.end_time or end
        round_obj.close_time = round_obj.close_time or close
        round_obj.save(
            update_fields=['number', 'result', 'status', 'result_time', 'end_time', 'close_time']
        )

        pending = ColourBet.objects.select_for_update().filter(round=round_obj, status='PENDING')
        for bet in pending:
            won = _side_wins(bet.bet_on, result_key, result_number, bet.number)
            payout = int(Decimal(bet.amount) * PAYOUT_MULTIPLIERS.get(bet.bet_on, Decimal('2'))) if won else 0
            bet.status = 'WON' if won else 'LOST'
            bet.payout = payout
            bet.settled_at = now
            bet.save(update_fields=['status', 'payout', 'settled_at'])

            if payout > 0:
                wallet, _ = Wallet.objects.select_for_update().get_or_create(user=bet.user)
                balance_before = wallet.balance
                wallet.add(payout)
                wallet.refresh_from_db()
                Transaction.objects.create(
                    user=bet.user,
                    transaction_type='WIN',
                    amount=payout,
                    balance_before=balance_before,
                    balance_after=wallet.balance,
                    description=f'Colour Game win — round {round_obj.round_id}',
                )

    return round_obj


def _create_round(now=None) -> ColourRound:
    now = now or _now()
    start = now
    close = start + timedelta(seconds=BETTING_WINDOW_SECONDS)
    end = start + timedelta(seconds=ROUND_DURATION_SECONDS)
    return ColourRound.objects.create(
        round_id=_generate_round_id(),
        status='BETTING',
        start_time=start,
        close_time=close,
        end_time=end,
    )


def advance_colour_rounds() -> ColourRound | None:
    now = _now()
    active = ColourRound.objects.filter(status='BETTING').order_by('-start_time').first()

    if active:
        end = _round_end(active)
        if now >= end:
            settle_round(active)
            active = None
        else:
            updates = {}
            if not active.close_time:
                updates['close_time'] = active.start_time + timedelta(seconds=BETTING_WINDOW_SECONDS)
            if not active.end_time:
                updates['end_time'] = active.start_time + timedelta(seconds=ROUND_DURATION_SECONDS)
            if updates:
                ColourRound.objects.filter(pk=active.pk).update(**updates)
                active.refresh_from_db()

    if active is None:
        active = _create_round(now)

    return active


def build_round_payload(round_obj: ColourRound | None) -> dict:
    if not round_obj:
        return {'status': 'no_round', 'message': 'No active round'}

    now = _now()
    remaining = _seconds_remaining(round_obj, now)
    payload = {
        'status': 'active' if round_obj.status == 'BETTING' else round_obj.status.lower(),
        'round_id': round_obj.round_id,
        'timer': remaining,
        'betting_open': _betting_open(round_obj, now),
        'start_time': round_obj.start_time.isoformat(),
        'server_time': now.isoformat(),
        'round_duration_seconds': ROUND_DURATION_SECONDS,
    }
    if round_obj.result:
        payload['result'] = round_obj.result
    if round_obj.number is not None:
        payload['number'] = round_obj.number
    return payload


def build_result_payload(round_obj: ColourRound) -> dict:
    return {
        'round_id': round_obj.round_id,
        'status': round_obj.status,
        'result': round_obj.result or None,
        'number': round_obj.number,
        'result_time': round_obj.result_time.isoformat() if round_obj.result_time else None,
    }


def place_colour_bets(user, round_obj: ColourRound, bet_items: list[dict]) -> dict:
    if user.is_staff or user.is_superuser:
        raise ValueError('Admins are not allowed to participate in the game.')

    now = _now()
    if round_obj.status != 'BETTING' or not _betting_open(round_obj, now):
        raise ValueError('Betting is closed for this round')

    if not bet_items:
        raise ValueError('No bets provided')

    total_stake = 0
    normalized = []
    for item in bet_items:
        bet_on = str(item.get('bet_on', '')).lower().strip()
        if bet_on not in ('green', 'red', 'violet', 'number'):
            raise ValueError(f'Invalid bet_on: {bet_on}')
        try:
            amount = int(item.get('amount', 0))
        except (TypeError, ValueError):
            raise ValueError('Invalid bet amount')
        if amount < 1:
            raise ValueError('Bet amount must be at least ₹1')
        number = item.get('number')
        if bet_on == 'number':
            try:
                number = int(number)
            except (TypeError, ValueError):
                raise ValueError('Number bet requires number 0-9')
            if number < 0 or number > 9:
                raise ValueError('Number must be between 0 and 9')
        else:
            number = None
        total_stake += amount
        normalized.append({'bet_on': bet_on, 'amount': amount, 'number': number})

    with transaction.atomic():
        wallet, _ = Wallet.objects.select_for_update().get_or_create(user=user)
        if wallet.balance < total_stake:
            raise ValueError('Insufficient balance')

        balance_before = wallet.balance
        if not wallet.deduct(total_stake):
            raise ValueError('Insufficient balance')

        Transaction.objects.create(
            user=user,
            transaction_type='BET',
            amount=total_stake,
            balance_before=balance_before,
            balance_after=wallet.balance,
            description=f'Colour Game bet — round {round_obj.round_id}',
        )
        _record_turnover(user, total_stake)

        placed = []
        for item in normalized:
            bet = ColourBet.objects.create(
                user=user,
                round=round_obj,
                bet_on=item['bet_on'],
                number=item['number'],
                amount=item['amount'],
            )
            placed.append(bet)

        wallet.refresh_from_db()

    return {
        'round_id': round_obj.round_id,
        'bets_placed': len(placed),
        'total_stake': total_stake,
        'wallet_balance': wallet.balance,
        'bets': [
            {
                'id': b.id,
                'bet_on': b.bet_on,
                'number': b.number,
                'amount': b.amount,
                'status': b.status,
            }
            for b in placed
        ],
    }
