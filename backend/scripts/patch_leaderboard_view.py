#!/usr/bin/env python3
"""Patch accounts/views.py leaderboard() to use game.leaderboard_display."""

from pathlib import Path
import sys

NEW_LEADERBOARD = '''
def leaderboard(request):
    """
    Leaderboard API (daily turnover) — display layer with simulated competition.

    Response shape (stable for clients):
    {
      "leaderboard": [{"username": "...", "turnover": 123.0}, ...],
      "user_stats": {"rank": 7, "turnover": 1500.0},
      "prizes": {"1st": "₹3,000", "2nd": "₹1,500", ..., "7th": "₹100"}
    }

    Note: Rank is 0 when the user's daily turnover is <= 50 (client shows "Unranked").
    Display list mixes seeded fake profiles with real UserDailyTurnover (top 10 shown).
    Prize payout command uses DB only — not this display list.
    """
    try:
        from game.models import LeaderboardSetting, UserDailyTurnover
        from game.utils import get_leaderboard_period_date
        from game.leaderboard_display import build_display_leaderboard

        try:
            db_user = User.objects.get(pk=getattr(request.user, 'id', None))
        except Exception:
            return Response({'error': 'User not found'}, status=status.HTTP_401_UNAUTHORIZED)
        current_user_id = db_user.id

        period_date = get_leaderboard_period_date()

        setting = LeaderboardSetting.objects.first()
        if not setting:
            setting = LeaderboardSetting.objects.create()

        real_rows_qs = UserDailyTurnover.objects.filter(
            period_date=period_date, turnover__gt=0
        ).select_related('user').order_by('-turnover', 'user_id')[:50]

        real_rows = [
            {
                'user_id': row.user_id,
                'username': row.user.username or '',
                'turnover': float(row.turnover),
            }
            for row in real_rows_qs
        ]

        user_row = UserDailyTurnover.objects.filter(
            user_id=current_user_id, period_date=period_date
        ).first()
        user_turnover = float(user_row.turnover) if user_row else 0.0

        display = build_display_leaderboard(
            period_date=period_date,
            real_rows=real_rows,
            current_user_id=current_user_id,
            user_turnover_real=user_turnover,
        )

        logger.info(
            "Leaderboard Request - User: %s (ID: %s), Rank: %s, Turnover: %s",
            db_user.username, current_user_id,
            display['user_stats']['rank'], user_turnover,
        )

        return Response({
            'leaderboard': display['leaderboard'],
            'user_stats': display['user_stats'],
            'prizes': display.get('prizes', {}),
        })
    except Exception as e:
        logger.error(f"Error in leaderboard API: {str(e)}", exc_info=True)
        return Response({'error': 'Failed to fetch leaderboard data'}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
'''


def patch_views(content: str) -> str:
    start = content.find("def leaderboard(request):")
    if start == -1:
        raise SystemExit("leaderboard function not found")

    rest = content[start + 1:]
    next_def = rest.find("\ndef ")
    if next_def == -1:
        return content[:start] + NEW_LEADERBOARD.strip() + "\n"

    end = start + 1 + next_def + 1
    return content[:start] + NEW_LEADERBOARD.strip() + "\n\n" + content[end:]


def main():
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "/app/accounts/views.py")
    text = path.read_text(encoding="utf-8")
    path.write_text(patch_views(text), encoding="utf-8")
    print(f"Patched {path}")


if __name__ == "__main__":
    main()
