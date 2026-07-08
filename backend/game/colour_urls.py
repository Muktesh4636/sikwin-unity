from django.urls import path

from game import colour_views

urlpatterns = [
    path('round/', colour_views.colour_round, name='colour_round'),
    path('round/<str:round_id>/result/', colour_views.colour_round_result, name='colour_round_result'),
    path('bet/', colour_views.colour_bet, name='colour_bet'),
    path('bets/', colour_views.colour_bets, name='colour_bets'),
    path('results/', colour_views.colour_results, name='colour_results'),
]
