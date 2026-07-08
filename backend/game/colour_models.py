"""Colour Game models — aligned with existing game_colourround / game_colourbet tables."""
from django.conf import settings
from django.db import models


class ColourRound(models.Model):
    round_id = models.CharField(max_length=32, unique=True, db_index=True)
    status = models.CharField(max_length=12, default='BETTING')
    result = models.CharField(max_length=20, blank=True, default='')
    number = models.IntegerField(null=True, blank=True)
    start_time = models.DateTimeField()
    close_time = models.DateTimeField(null=True, blank=True)
    result_time = models.DateTimeField(null=True, blank=True)
    end_time = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'game_colourround'
        ordering = ['-start_time']
        managed = False

    def __str__(self):
        return f"ColourRound {self.round_id} ({self.status})"


class ColourBet(models.Model):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='colour_bets',
        db_column='user_id',
    )
    round = models.ForeignKey(
        ColourRound,
        on_delete=models.CASCADE,
        related_name='bets',
        db_column='round_id',
    )
    bet_on = models.CharField(max_length=10)
    number = models.IntegerField(null=True, blank=True)
    amount = models.IntegerField()
    payout = models.IntegerField(default=0)
    status = models.CharField(max_length=10, default='PENDING')
    created_at = models.DateTimeField(auto_now_add=True)
    settled_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'game_colourbet'
        ordering = ['-created_at']
        managed = False

    def __str__(self):
        extra = f" #{self.number}" if self.bet_on == 'number' else ''
        return f"{self.user_id} {self.round_id} {self.bet_on}{extra} ₹{self.amount}"
