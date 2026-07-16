"""
Referral rewards: instant bonus on referee first deposit + daily loss commission.
"""
import logging
import zoneinfo
from datetime import date, timedelta
from decimal import Decimal, ROUND_DOWN

from django.conf import settings
from django.db import transaction
from django.db.models import F, Sum
from django.utils import timezone

logger = logging.getLogger(__name__)
IST = zoneinfo.ZoneInfo('Asia/Kolkata')

# lifetime referral count → commission rate on referee daily wallet loss
REFERRAL_COMMISSION_TIERS = (
    (1, 10, Decimal('0.02')),
    (11, 30, Decimal('0.03')),
    (31, 50, Decimal('0.04')),
    (51, 100, Decimal('0.06')),
    (101, None, Decimal('0.08')),
)


def get_instant_bonus_amount() -> Decimal:
    return Decimal(str(getattr(settings, 'REFERRAL_INSTANT_BONUS_PER_REFEREE', 100)))


def get_commission_tier_rate(lifetime_referral_count: int) -> Decimal:
    count = max(0, int(lifetime_referral_count or 0))
    for lo, hi, rate in REFERRAL_COMMISSION_TIERS:
        if hi is None:
            if count >= lo:
                return rate
        elif lo <= count <= hi:
            return rate
    return Decimal('0.02')


def get_next_commission_tier(lifetime_referral_count: int) -> dict | None:
    count = max(0, int(lifetime_referral_count or 0))
    for lo, hi, rate in REFERRAL_COMMISSION_TIERS:
        if hi is not None and count < hi:
            return {
                'target_referrals': hi,
                'referrals_needed': hi - count,
                'rate': float(rate),
                'rate_percent': f'{int(rate * 100)}%',
            }
    return None


def is_referrer_eligible(referrer) -> bool:
    if not referrer:
        return False
    if referrer.is_staff or referrer.is_superuser:
        return False
    return True


def credit_referral_wallet(referrer, amount_int, transaction_type, description, redis_client=None):
    from .models import Wallet, Transaction

    amount_int = int(amount_int)
    if amount_int <= 0:
        return None

    wallet, _ = Wallet.objects.get_or_create(user=referrer)
    wallet = Wallet.objects.select_for_update().get(pk=wallet.pk)
    balance_before = wallet.balance
    wallet.add(amount_int, is_bonus=True)
    Wallet.objects.filter(pk=wallet.pk).update(total_deposits=F('total_deposits') + amount_int)
    Wallet.apply_deposit_rotation_credit(wallet.pk, amount_int)
    wallet.refresh_from_db()

    if redis_client:
        try:
            redis_client.incrbyfloat(f"user_balance:{referrer.id}", float(amount_int))
        except Exception:
            logger.exception('Redis balance sync failed for referrer %s', referrer.id)

    return Transaction.objects.create(
        user=referrer,
        transaction_type=transaction_type,
        amount=amount_int,
        balance_before=balance_before,
        balance_after=wallet.balance,
        description=description,
    )


def credit_referral_instant_bonus(referee, deposit, redis_client=None) -> bool:
    """Credit referrer once when referee's first deposit is approved."""
    from .models import DepositRequest, ReferralInstantBonus

    referrer = getattr(referee, 'referred_by', None)
    if not is_referrer_eligible(referrer):
        return False

    first_deposit = not DepositRequest.objects.filter(
        user=referee,
        status='APPROVED',
    ).exclude(pk=deposit.pk).exists()
    if not first_deposit:
        return False

    amount = get_instant_bonus_amount()
    if amount <= 0:
        return False

    try:
        with transaction.atomic():
            if ReferralInstantBonus.objects.select_for_update().filter(referee=referee).exists():
                return False

            credit_referral_wallet(
                referrer,
                int(amount),
                'REFERRAL_BONUS',
                f'Referral instant bonus for {referee.username} first deposit (referee_id:{referee.id})',
                redis_client=redis_client,
            )
            ReferralInstantBonus.objects.create(
                referee=referee,
                referrer=referrer,
                deposit_request=deposit,
                amount=int(amount),
            )
        logger.info(
            'Instant referral bonus ₹%s → %s for referee %s',
            amount,
            referrer.username,
            referee.username,
        )
        return True
    except Exception:
        logger.exception('Failed instant referral bonus for referee %s', referee.id)
        return False


def snapshot_wallet_balances_for_date(snapshot_date: date) -> int:
    """Record each user's closing balance for an IST calendar day."""
    from .models import User, WalletDailySnapshot

    count = 0
    for user in User.objects.filter(wallet__isnull=False).select_related('wallet'):
        balance = int(user.wallet.balance or 0)
        _, created = WalletDailySnapshot.objects.update_or_create(
            user=user,
            snapshot_date=snapshot_date,
            defaults={'closing_balance': balance},
        )
        if created:
            count += 1
    return count


def compute_daily_wallet_loss(referee, commission_date: date) -> int:
    """
    Daily wallet loss for commission_date (IST):
    opening at start of day − closing at end of day (midnight snapshots).
    """
    from .models import WalletDailySnapshot

    prev_date = commission_date - timedelta(days=1)
    opening_row = WalletDailySnapshot.objects.filter(user=referee, snapshot_date=prev_date).first()
    closing_row = WalletDailySnapshot.objects.filter(user=referee, snapshot_date=commission_date).first()
    if not opening_row or not closing_row:
        return 0
    loss = int(opening_row.closing_balance) - int(closing_row.closing_balance)
    return max(0, loss)


def process_referral_daily_commission(commission_date: date | None = None, redis_client=None) -> dict:
    """Process daily referral commission for commission_date (default: yesterday IST)."""
    from .models import ReferralCommission, User

    if commission_date is None:
        commission_date = timezone.now().astimezone(IST).date() - timedelta(days=1)

    stats = {
        'commission_date': str(commission_date),
        'credited': 0,
        'skipped': 0,
        'zero_loss': 0,
        'total_amount': 0,
    }

    referees = User.objects.filter(referred_by__isnull=False).select_related('referred_by')
    for referee in referees:
        referrer = referee.referred_by
        if not is_referrer_eligible(referrer):
            stats['skipped'] += 1
            continue

        if ReferralCommission.objects.filter(referee=referee, commission_date=commission_date).exists():
            stats['skipped'] += 1
            continue

        loss = compute_daily_wallet_loss(referee, commission_date)
        lifetime = getattr(referrer, 'total_referrals_count', None)
        if lifetime is None:
            lifetime = User.objects.filter(referred_by=referrer).count()
        rate = get_commission_tier_rate(lifetime)

        if loss <= 0:
            ReferralCommission.objects.create(
                referee=referee,
                referrer=referrer,
                commission_date=commission_date,
                daily_loss=0,
                tier_rate=rate,
                commission_amount=0,
            )
            stats['zero_loss'] += 1
            continue

        commission = int((Decimal(loss) * rate).quantize(Decimal('1'), rounding=ROUND_DOWN))
        if commission <= 0:
            ReferralCommission.objects.create(
                referee=referee,
                referrer=referrer,
                commission_date=commission_date,
                daily_loss=loss,
                tier_rate=rate,
                commission_amount=0,
            )
            stats['zero_loss'] += 1
            continue

        try:
            with transaction.atomic():
                if ReferralCommission.objects.filter(referee=referee, commission_date=commission_date).exists():
                    stats['skipped'] += 1
                    continue

                credit_referral_wallet(
                    referrer,
                    commission,
                    'REFERRAL_COMMISSION',
                    (
                        f'Referral commission {commission_date} from {referee.username}: '
                        f'loss ₹{loss} @ {int(rate * 100)}%'
                    ),
                    redis_client=redis_client,
                )
                ReferralCommission.objects.create(
                    referee=referee,
                    referrer=referrer,
                    commission_date=commission_date,
                    daily_loss=loss,
                    tier_rate=rate,
                    commission_amount=commission,
                )
                stats['credited'] += 1
                stats['total_amount'] += commission
        except Exception:
            logger.exception('Commission failed referee=%s date=%s', referee.id, commission_date)

    return stats


def get_referral_dashboard(user) -> dict:
    """Build referral-data API payload for the new commission system."""
    from .models import ReferralCommission, ReferralInstantBonus, Transaction, User

    total_referrals = getattr(user, 'total_referrals_count', None)
    if total_referrals is None:
        total_referrals = User.objects.filter(referred_by=user).count()

    active_referrals = User.objects.filter(
        referred_by=user,
        deposit_requests__status='APPROVED',
    ).distinct().count()

    instant_total = (
        Transaction.objects.filter(user=user, transaction_type='REFERRAL_BONUS').aggregate(
            total=Sum('amount')
        )['total']
        or 0
    )
    commission_total = (
        Transaction.objects.filter(user=user, transaction_type='REFERRAL_COMMISSION').aggregate(
            total=Sum('amount')
        )['total']
        or 0
    )
    total_earnings = int(instant_total) + int(commission_total)
    tier_rate = get_commission_tier_rate(total_referrals)

    recent_bonuses = list(
        Transaction.objects.filter(
            user=user,
            transaction_type__in=['REFERRAL_BONUS', 'REFERRAL_COMMISSION'],
        )
        .order_by('-created_at')[:10]
        .values('amount', 'transaction_type', 'description', 'created_at')
    )

    referrals_list = []
    for ref in User.objects.filter(referred_by=user).order_by('-date_joined'):
        has_deposit = ref.deposit_requests.filter(status='APPROVED').exists()
        instant_paid = ReferralInstantBonus.objects.filter(referee=ref).exists()
        referrals_list.append({
            'id': ref.id,
            'username': ref.username,
            'date_joined': ref.date_joined.isoformat() if ref.date_joined else None,
            'has_deposit': has_deposit,
            'instant_bonus_paid': instant_paid,
        })

    commission_tiers = [
        {
            'min_referrals': lo,
            'max_referrals': hi,
            'rate_percent': f'{int(rate * 100)}%',
            'rate': float(rate),
            'active': (
                (hi is None and total_referrals >= lo)
                or (hi is not None and lo <= total_referrals <= hi)
            ),
        }
        for lo, hi, rate in REFERRAL_COMMISSION_TIERS
    ]

    return {
        'referral_code': user.referral_code or '',
        'total_referrals': total_referrals,
        'active_referrals': active_referrals,
        'total_earnings': str(total_earnings),
        'instant_bonus_per_referee': str(int(get_instant_bonus_amount())),
        'total_instant_bonuses': str(int(instant_total)),
        'total_commission_earnings': str(int(commission_total)),
        'commission_tier_rate': float(tier_rate),
        'commission_tier_percent': f'{int(tier_rate * 100)}%',
        'commission_tiers': commission_tiers,
        'next_commission_tier': get_next_commission_tier(total_referrals),
        'recent_bonuses': recent_bonuses,
        'referrals': referrals_list,
        # Legacy fields kept empty for old clients during transition
        'current_milestone_bonus': '0',
        'next_milestone': None,
        'milestones': [],
    }
