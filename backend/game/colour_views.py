"""Colour Game REST API — matches Android ApiService.kt contracts."""
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response

from game.colour_engine import advance_colour_rounds, build_result_payload, build_round_payload, place_colour_bets
from game.colour_models import ColourBet, ColourRound


def _parse_bet_items(data) -> list[dict]:
    if isinstance(data.get('bets'), list):
        return data['bets']
    if 'bet_on' in data or 'amount' in data:
        return [data]
    return []


@api_view(['GET'])
@permission_classes([AllowAny])
def colour_round(request):
    """GET /api/colour/round/ — current round + countdown."""
    try:
        round_obj = advance_colour_rounds()
        return Response(build_round_payload(round_obj))
    except Exception as exc:
        return Response({'status': 'no_round', 'message': str(exc)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)


@api_view(['GET'])
@permission_classes([AllowAny])
def colour_round_result(request, round_id: str):
    """GET /api/colour/round/{round_id}/result/"""
    advance_colour_rounds()
    try:
        round_obj = ColourRound.objects.get(round_id=round_id)
    except ColourRound.DoesNotExist:
        return Response({'error': 'Round not found'}, status=status.HTTP_404_NOT_FOUND)
    return Response(build_result_payload(round_obj))


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def colour_bet(request):
    """POST /api/colour/bet/ — place one or more bets."""
    advance_colour_rounds()
    round_obj = ColourRound.objects.filter(status='BETTING').order_by('-start_time').first()
    if not round_obj:
        return Response({'error': 'Betting is closed for this round'}, status=status.HTTP_400_BAD_REQUEST)

    items = _parse_bet_items(request.data)
    try:
        result = place_colour_bets(request.user, round_obj, items)
    except ValueError as exc:
        return Response({'error': str(exc)}, status=status.HTTP_400_BAD_REQUEST)

    return Response(result, status=status.HTTP_201_CREATED)


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def colour_bets(request):
    """GET /api/colour/bets/ — user's bet history."""
    bets = (
        ColourBet.objects.filter(user=request.user)
        .select_related('round')
        .order_by('-created_at')[:100]
    )
    rows = []
    for bet in bets:
        rows.append({
            'id': bet.id,
            'round_id': bet.round.round_id,
            'bet_on': bet.bet_on,
            'number': bet.number,
            'amount': bet.amount,
            'payout': bet.payout,
            'status': bet.status.lower(),
            'result': bet.round.result or None,
            'result_number': bet.round.number,
            'created_at': bet.created_at.isoformat() if bet.created_at else None,
            'settled_at': bet.settled_at.isoformat() if bet.settled_at else None,
        })
    return Response({'bets': rows})


@api_view(['GET'])
@permission_classes([AllowAny])
def colour_results(request):
    """GET /api/colour/results/ — public recent completed rounds."""
    advance_colour_rounds()
    completed = (
        ColourRound.objects.filter(status='COMPLETED', result__gt='')
        .order_by('-result_time')[:50]
    )
    rows = [
        {
            'round_id': r.round_id,
            'result': r.result,
            'number': r.number,
            'result_time': r.result_time.isoformat() if r.result_time else None,
        }
        for r in completed
    ]
    return Response({'results': rows})
