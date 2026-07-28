"""POST /api/client-events/ — receive APK click/error telemetry."""
from __future__ import annotations

import logging

from django.contrib.auth import get_user_model
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from accounts.client_events import ClientEvent

logger = logging.getLogger(__name__)
User = get_user_model()

MAX_MESSAGE = 4000
MAX_NAME = 128
MAX_SCREEN = 64
MAX_PROPS_KEYS = 40


def _clip(value, limit: int) -> str:
    text = "" if value is None else str(value)
    return text[:limit]


@api_view(["POST"])
@permission_classes([AllowAny])
def ingest_client_event(request):
    """
    Body:
      event_type: error|click|screen
      name: short action / source id
      message: optional detail (errors)
      screen: optional current route/screen
      props: optional object
      username, device_model, android_version, app_version: optional
    """
    data = request.data if isinstance(request.data, dict) else {}
    event_type = _clip(data.get("event_type") or data.get("type"), 16).lower()
    if event_type not in (ClientEvent.EVENT_ERROR, ClientEvent.EVENT_CLICK, ClientEvent.EVENT_SCREEN):
        return Response(
            {"error": "event_type must be error, click, or screen"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    name = _clip(data.get("name") or data.get("action") or "unknown", MAX_NAME)
    if not name:
        return Response({"error": "name is required"}, status=status.HTTP_400_BAD_REQUEST)

    message = _clip(data.get("message") or data.get("error") or "", MAX_MESSAGE)
    screen = _clip(data.get("screen") or data.get("route") or "", MAX_SCREEN)
    props = data.get("props") if isinstance(data.get("props"), dict) else {}
    if len(props) > MAX_PROPS_KEYS:
        props = dict(list(props.items())[:MAX_PROPS_KEYS])

    user = request.user if getattr(request.user, "is_authenticated", False) else None
    username = _clip(data.get("username") or (user.username if user else ""), 150)

    try:
        event = ClientEvent.objects.create(
            event_type=event_type,
            name=name,
            message=message,
            screen=screen,
            props=props,
            user=user if user and user.is_authenticated else None,
            username=username,
            device_model=_clip(data.get("device_model"), 128),
            android_version=_clip(data.get("android_version"), 32),
            app_version=_clip(data.get("app_version"), 32),
        )
    except Exception as exc:
        logger.exception("client event ingest failed: %s", exc)
        return Response({"error": "failed to store event"}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

    return Response({"ok": True, "id": event.id}, status=status.HTTP_201_CREATED)


@api_view(["GET"])
@permission_classes([AllowAny])
def list_client_events(request):
    """
    Staff-oriented recent events list.
    GET /api/client-events/?type=error&limit=50
    """
    if not (getattr(request.user, "is_staff", False) or getattr(request.user, "is_superuser", False)):
        # Allow unauthenticated local ops with secret? Prefer staff auth.
        # For now require staff JWT; return 403 otherwise.
        return Response({"error": "Staff only"}, status=status.HTTP_403_FORBIDDEN)

    event_type = (request.GET.get("type") or "").strip().lower()
    try:
        limit = min(int(request.GET.get("limit") or 50), 200)
    except (TypeError, ValueError):
        limit = 50

    qs = ClientEvent.objects.all()
    if event_type in (ClientEvent.EVENT_ERROR, ClientEvent.EVENT_CLICK, ClientEvent.EVENT_SCREEN):
        qs = qs.filter(event_type=event_type)

    rows = []
    for e in qs[:limit]:
        rows.append(
            {
                "id": e.id,
                "event_type": e.event_type,
                "name": e.name,
                "message": e.message,
                "screen": e.screen,
                "props": e.props,
                "username": e.username,
                "device_model": e.device_model,
                "android_version": e.android_version,
                "app_version": e.app_version,
                "created_at": e.created_at.isoformat() if e.created_at else None,
            }
        )
    return Response({"count": len(rows), "events": rows})
