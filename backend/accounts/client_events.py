"""Client-side click / error telemetry from the APK."""
from __future__ import annotations

from django.conf import settings
from django.db import models


class ClientEvent(models.Model):
    EVENT_ERROR = "error"
    EVENT_CLICK = "click"
    EVENT_SCREEN = "screen"
    EVENT_TYPES = [
        (EVENT_ERROR, "Error"),
        (EVENT_CLICK, "Click"),
        (EVENT_SCREEN, "Screen"),
    ]

    event_type = models.CharField(max_length=16, choices=EVENT_TYPES, db_index=True)
    name = models.CharField(max_length=128, db_index=True)
    message = models.TextField(blank=True, default="")
    screen = models.CharField(max_length=64, blank=True, default="", db_index=True)
    props = models.JSONField(default=dict, blank=True)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="client_events",
    )
    username = models.CharField(max_length=150, blank=True, default="")
    device_model = models.CharField(max_length=128, blank=True, default="")
    android_version = models.CharField(max_length=32, blank=True, default="")
    app_version = models.CharField(max_length=32, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["event_type", "-created_at"], name="clientevt_type_created"),
            models.Index(fields=["name", "-created_at"], name="clientevt_name_created"),
        ]

    def __str__(self) -> str:
        return f"{self.event_type}:{self.name} @ {self.created_at}"
