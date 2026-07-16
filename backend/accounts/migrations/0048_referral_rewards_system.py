from decimal import Decimal

from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0047_wallet_deposit_rotation_lock'),
    ]

    operations = [
        migrations.AlterField(
            model_name='transaction',
            name='transaction_type',
            field=models.CharField(
                max_length=20,
                choices=[
                    ('DEPOSIT', 'Deposit'),
                    ('WITHDRAW', 'Withdraw'),
                    ('BET', 'Bet'),
                    ('WIN', 'Win'),
                    ('REFUND', 'Refund'),
                    ('REFERRAL_BONUS', 'Referral Bonus'),
                    ('REFERRAL_COMMISSION', 'Referral Commission'),
                    ('MILESTONE_BONUS', 'Milestone Bonus'),
                    ('LEADERBOARD_PRIZE', 'Leaderboard Prize'),
                    ('CRICKET_BET', 'Cricket Bet'),
                    ('CRICKET_WIN', 'Cricket Win'),
                ],
            ),
        ),
        migrations.CreateModel(
            name='ReferralInstantBonus',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('amount', models.BigIntegerField()),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('deposit_request', models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, to='accounts.depositrequest')),
                ('referee', models.OneToOneField(on_delete=django.db.models.deletion.CASCADE, related_name='referral_instant_bonus', to=settings.AUTH_USER_MODEL)),
                ('referrer', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='referral_instant_bonuses_given', to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.CreateModel(
            name='WalletDailySnapshot',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('snapshot_date', models.DateField()),
                ('closing_balance', models.BigIntegerField(default=0)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='wallet_daily_snapshots', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'indexes': [models.Index(fields=['snapshot_date'], name='accounts_wa_snapsho_idx')],
                'unique_together': {('user', 'snapshot_date')},
            },
        ),
        migrations.CreateModel(
            name='ReferralCommission',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('commission_date', models.DateField()),
                ('daily_loss', models.BigIntegerField(default=0)),
                ('tier_rate', models.DecimalField(decimal_places=4, default=Decimal('0.02'), max_digits=5)),
                ('commission_amount', models.BigIntegerField(default=0)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('referee', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='referral_commissions_as_referee', to=settings.AUTH_USER_MODEL)),
                ('referrer', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='referral_commissions_as_referrer', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'unique_together': {('referee', 'commission_date')},
            },
        ),
    ]
