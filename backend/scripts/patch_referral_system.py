#!/usr/bin/env python3
"""Patch accounts app for new referral rewards system (replaces milestone bonuses)."""

from pathlib import Path
import sys

MODELS_APPEND = '''

class ReferralInstantBonus(models.Model):
    """Tracks one-time instant bonus paid to referrer when referee makes first deposit."""
    referee = models.OneToOneField(User, on_delete=models.CASCADE, related_name='referral_instant_bonus')
    referrer = models.ForeignKey(User, on_delete=models.CASCADE, related_name='referral_instant_bonuses_given')
    deposit_request = models.ForeignKey('DepositRequest', on_delete=models.SET_NULL, null=True, blank=True)
    amount = models.BigIntegerField()
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Instant bonus ₹{self.amount} referee={self.referee_id} referrer={self.referrer_id}"


class WalletDailySnapshot(models.Model):
    """Closing wallet balance at end of an IST calendar day (for referral commission)."""
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='wallet_daily_snapshots')
    snapshot_date = models.DateField()
    closing_balance = models.BigIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = [['user', 'snapshot_date']]
        indexes = [models.Index(fields=['snapshot_date'])]

    def __str__(self):
        return f"{self.user.username} {self.snapshot_date} closing={self.closing_balance}"


class ReferralCommission(models.Model):
    """Daily commission credited to referrer based on referee wallet loss."""
    referee = models.ForeignKey(User, on_delete=models.CASCADE, related_name='referral_commissions_as_referee')
    referrer = models.ForeignKey(User, on_delete=models.CASCADE, related_name='referral_commissions_as_referrer')
    commission_date = models.DateField()
    daily_loss = models.BigIntegerField(default=0)
    tier_rate = models.DecimalField(max_digits=5, decimal_places=4, default=Decimal('0.02'))
    commission_amount = models.BigIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = [['referee', 'commission_date']]

    def __str__(self):
        return f"Commission {self.commission_date} referee={self.referee_id} ₹{self.commission_amount}"
'''

NEW_REFERRAL_DATA = '''
@api_view(['GET'])
@permission_classes([IsAuthenticated])
def referral_data(request):
    """Referral stats: instant bonus + daily commission tiers."""
    try:
        user = User.objects.get(pk=getattr(request.user, 'id', None))
    except Exception:
        return Response({'error': 'User not found'}, status=status.HTTP_401_UNAUTHORIZED)

    if not user.referral_code:
        user.referral_code = user.generate_unique_referral_code()
        user.save(update_fields=['referral_code'])

    from .referral_logic import get_referral_dashboard
    return Response(get_referral_dashboard(user))
'''

OLD_REFERRAL_BLOCK = """            # Check for referral bonus
            if deposit.user.referred_by:
                from .referral_logic import calculate_referral_bonus
                bonus_amount = calculate_referral_bonus(deposit.amount)
                
                if bonus_amount > 0:
                    referrer = deposit.user.referred_by
                    referrer_wallet, _ = Wallet.objects.get_or_create(user=referrer)
                    referrer_wallet = Wallet.objects.select_for_update().get(pk=referrer_wallet.pk)
                    
                    ref_balance_before = referrer_wallet.balance
                    # Referral bonus needs to be rotated 1 time (counts as deposit for withdrawable rule)
                    referrer_wallet.add(bonus_amount, is_bonus=True)
                    Wallet.objects.filter(pk=referrer_wallet.pk).update(total_deposits=F('total_deposits') + int(bonus_amount))
                    referrer_wallet.refresh_from_db()

                    # Update Redis balance for referrer
                    if redis_client:
                        try:
                            redis_client.incrbyfloat(f"user_balance:{referrer.id}", float(bonus_amount))
                        except: pass
                    
                    Transaction.objects.create(
                        user=referrer,
                        transaction_type='REFERRAL_BONUS',
                        amount=bonus_amount,
                        balance_before=ref_balance_before,
                        balance_after=referrer_wallet.balance,
                        description=f"Referral bonus from {deposit.user.username}'s deposit of ₹{deposit.amount}",
                    )
                    logger.info(f"Referral bonus of ₹{bonus_amount} granted to {referrer.username} for {deposit.user.username}'s deposit")
                    
                    # Milestone bonus: only when referral completes their FIRST deposit
                    first_deposit = not DepositRequest.objects.filter(
                        user=deposit.user, status='APPROVED'
                    ).exclude(pk=deposit.pk).exists()
                    if first_deposit:
                        from .referral_logic import check_and_award_milestone_bonus
                        active_referrals = User.objects.filter(
                            referred_by=referrer,
                            deposit_requests__status='APPROVED'
                        ).distinct().count()
                        milestone_awarded = check_and_award_milestone_bonus(referrer, active_referrals)
                        if milestone_awarded:
                            logger.info(f"Milestone bonus awarded to {referrer.username}")"""

NEW_REFERRAL_BLOCK = """            # Referral instant bonus (first deposit only, once per referee)
            if deposit.user.referred_by_id:
                from .referral_logic import credit_referral_instant_bonus
                credit_referral_instant_bonus(deposit.user, deposit, redis_client=redis_client)"""

SETTINGS_SNIPPET = """
# Referral rewards
REFERRAL_INSTANT_BONUS_PER_REFEREE = 100
"""


def patch_models(path: Path) -> None:
    text = path.read_text(encoding='utf-8')
    if 'class ReferralInstantBonus' not in text:
        text = text.rstrip() + MODELS_APPEND
    if "('REFERRAL_COMMISSION'" not in text:
        text = text.replace(
            "('REFERRAL_BONUS', 'Referral Bonus'),\n        ('MILESTONE_BONUS', 'Milestone Bonus'),",
            "('REFERRAL_BONUS', 'Referral Bonus'),\n        ('REFERRAL_COMMISSION', 'Referral Commission'),\n        ('MILESTONE_BONUS', 'Milestone Bonus'),",
        )
    path.write_text(text, encoding='utf-8')


def replace_function(content: str, name: str, new_body: str) -> str:
    marker = f"def {name}(request):"
    start = content.find(marker)
    if start == -1:
        raise SystemExit(f"{name} not found")
    rest = content[start + 1 :]
    next_def = rest.find("\ndef ")
    if next_def == -1:
        return content[:start] + new_body.strip() + "\n"
    end = start + 1 + next_def + 1
    return content[:start] + new_body.strip() + "\n\n" + content[end:]


def patch_views(path: Path) -> None:
    text = path.read_text(encoding='utf-8')
    if OLD_REFERRAL_BLOCK in text:
        text = text.replace(OLD_REFERRAL_BLOCK, NEW_REFERRAL_BLOCK)
    elif 'credit_referral_instant_bonus' not in text:
        raise SystemExit('Could not find old referral block in views.py')
    text = replace_function(text, 'referral_data', NEW_REFERRAL_DATA)
    path.write_text(text, encoding='utf-8')


def patch_settings(path: Path) -> None:
    text = path.read_text(encoding='utf-8')
    if 'REFERRAL_INSTANT_BONUS_PER_REFEREE' in text:
        return
    text = text.rstrip() + SETTINGS_SNIPPET
    path.write_text(text, encoding='utf-8')


def main() -> None:
    base = Path(sys.argv[1] if len(sys.argv) > 1 else '/app')
    patch_models(base / 'accounts' / 'models.py')
    patch_views(base / 'accounts' / 'views.py')
    patch_settings(base / 'dice_game' / 'settings.py')
    print('Patched referral system')


if __name__ == '__main__':
    main()
