"""Backfill wallet daily snapshots for the last N days (IST). Run once after deploy."""
from datetime import timedelta

from django.core.management.base import BaseCommand
from django.utils import timezone

from accounts.referral_logic import IST, snapshot_wallet_balances_for_date


class Command(BaseCommand):
    help = 'Backfill wallet daily snapshots using current balances (approximate for past days).'

    def add_arguments(self, parser):
        parser.add_argument('--days', type=int, default=3, help='Number of past days to snapshot (default 3)')

    def handle(self, *args, **options):
        today = timezone.now().astimezone(IST).date()
        days = max(1, options['days'])
        for i in range(1, days + 1):
            d = today - timedelta(days=i)
            count = snapshot_wallet_balances_for_date(d)
            self.stdout.write(f'{d}: {count} new snapshot rows')
        self.stdout.write(self.style.SUCCESS('Backfill complete'))
