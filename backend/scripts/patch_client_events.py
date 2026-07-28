#!/usr/bin/env python3
"""Patch dice_game/urls.py + accounts/admin.py for ClientEvent telemetry."""
from pathlib import Path
import sys

URLS_PATH = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/app/dice_game/urls.py")
ADMIN_PATH = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("/app/accounts/admin.py")

URL_MARKER = "ingest_client_event"
ADMIN_MARKER = "ClientEventAdmin"


def patch_urls() -> None:
    text = URLS_PATH.read_text(encoding="utf-8")
    if URL_MARKER in text:
        print(f"URLs already patched: {URLS_PATH}")
        return

    # Ensure import
    if "client_event_views" not in text:
        if "from accounts import views as accounts_views" in text:
            text = text.replace(
                "from accounts import views as accounts_views",
                "from accounts import views as accounts_views\n"
                "from accounts import client_event_views",
                1,
            )
        else:
            text = "from accounts import client_event_views\n" + text

    needle = "path('api/support/contacts/', project_views.support_contacts, name='support_contacts'),"
    insert = (
        "path('api/support/contacts/', project_views.support_contacts, name='support_contacts'),\n"
        "    path('api/client-events/', client_event_views.ingest_client_event, name='client_events_ingest'),\n"
        "    path('api/client-events/list/', client_event_views.list_client_events, name='client_events_list'),"
    )
    if needle not in text:
        # fallback: insert after health
        needle2 = "path('api/health/', project_views.health, name='health'),"
        if needle2 not in text:
            raise SystemExit(f"Could not find URL anchor in {URLS_PATH}")
        insert2 = (
            "path('api/health/', project_views.health, name='health'),\n"
            "    path('api/client-events/', client_event_views.ingest_client_event, name='client_events_ingest'),\n"
            "    path('api/client-events/list/', client_event_views.list_client_events, name='client_events_list'),"
        )
        text = text.replace(needle2, insert2, 1)
    else:
        text = text.replace(needle, insert, 1)

    URLS_PATH.write_text(text, encoding="utf-8")
    print(f"Patched {URLS_PATH}")


def patch_admin() -> None:
    text = ADMIN_PATH.read_text(encoding="utf-8")
    if ADMIN_MARKER in text:
        print(f"Admin already patched: {ADMIN_PATH}")
        return

    block = '''

from accounts.client_events import ClientEvent


@admin.register(ClientEvent)
class ClientEventAdmin(admin.ModelAdmin):
    list_display = ("id", "event_type", "name", "username", "screen", "created_at")
    list_filter = ("event_type", "screen", "app_version")
    search_fields = ("name", "message", "username", "device_model")
    readonly_fields = ("created_at",)
    ordering = ("-created_at",)
'''
    text = text.rstrip() + "\n" + block + "\n"
    ADMIN_PATH.write_text(text, encoding="utf-8")
    print(f"Patched {ADMIN_PATH}")


if __name__ == "__main__":
    patch_urls()
    patch_admin()
