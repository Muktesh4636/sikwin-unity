"""Nightly referral commission for yesterday (IST). Run after wallet snapshots."""
from datetime import timedelta

from django.core.management.base import BaseCommand
from django.utils import timezone

from accounts.referral_logic import (
    IST,
    process_referral_daily_commission,
    snapshot_wallet_balances_for_date,
)


class Command(BaseCommand):
    help = 'Snapshot wallet balances and credit referrers daily commission for yesterday (IST).'

    def add_arguments(self, parser):
        parser.add_argument(
            '--date',
            type=str,
            help='Commission date YYYY-MM-DD (IST). Default: yesterday.',
        )
        parser.add_argument(
            '--skip-snapshot',
            action='store_true',
            help='Skip wallet snapshot step (use when snapshots already exist).',
        )

    def handle(self, *args, **options):
        from game.utils import get_redis_client

        redis_client = get_redis_client()
        now_ist = timezone.now().astimezone(IST)

        if options.get('date'):
            from datetime import date
            commission_date = date.fromisoformat(options['date'])
        else:
            commission_date = now_ist.date() - timedelta(days=1)

        if not options.get('skip_snapshot'):
            snap_count = snapshot_wallet_balances_for_date(commission_date)
            self.stdout.write(f'Snapshotted closing balances for {commission_date}: {snap_count} new rows')

        stats = process_referral_daily_commission(commission_date, redis_client=redis_client)
        self.stdout.write(self.style.SUCCESS(str(stats)))
