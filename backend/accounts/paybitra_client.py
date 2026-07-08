"""PayBitra pay-in API client for dynamic UPI deposit details."""

from __future__ import annotations

import logging
import os
import urllib.parse
from decimal import Decimal
from typing import Any

import requests
from django.conf import settings

logger = logging.getLogger(__name__)

DEFAULT_BASE = "https://api.paybitra.com/v1"


def _cfg(name: str, default: str = "") -> str:
    value = getattr(settings, name, None)
    if value:
        return str(value)
    return os.environ.get(name, default)


def build_upi_uri(upi_id: str, acc_holder_name: str, amount: Decimal, code: str) -> str:
    params = {
        "pa": upi_id,
        "pn": acc_holder_name,
        "am": str(amount),
        "cu": "INR",
        "tn": code or "Deposit",
    }
    return "upi://pay?" + urllib.parse.urlencode(params)


def _api_error(payload: dict[str, Any], fallback: str) -> str:
    err = payload.get("error") or {}
    if isinstance(err, dict):
        return err.get("message") or fallback
    return fallback


def initiate_upi_deposit(amount: Decimal) -> dict[str, Any]:
    """Create a PayBitra order and fetch assigned UPI details for the amount."""
    base = _cfg("PAYBITRA_API_BASE", DEFAULT_BASE).rstrip("/")
    key = _cfg("PAYBITRA_TRANSACTION_KEY")
    user_id = _cfg("PAYBITRA_USER_ID", "PS")
    merchant_code = _cfg("PAYBITRA_CODE", "PS247")
    ot = _cfg("PAYBITRA_OT", "n")
    role_token = _cfg("PAYBITRA_ROLE_TOKEN", "")

    if not key:
        raise ValueError("PayBitra is not configured on the server")

    params: dict[str, Any] = {
        "user_id": user_id,
        "code": merchant_code,
        "ot": ot,
        "key": key,
        "amount": int(amount),
    }
    if role_token:
        params["roleToken"] = role_token

    gen_resp = requests.get(f"{base}/payIn/generate-payin", params=params, timeout=30)
    gen_resp.raise_for_status()
    gen_payload = gen_resp.json()
    if gen_payload.get("error"):
        raise ValueError(_api_error(gen_payload, "PayBitra generate-payin failed"))

    data = gen_payload.get("data") or {}
    order_id = data.get("merchantOrderId")
    if not order_id:
        raise ValueError("PayBitra did not return an order id")

    assign_resp = requests.post(
        f"{base}/payIn/assign-bank/{order_id}",
        json={"amount": int(amount), "type": "upi"},
        timeout=30,
    )
    assign_resp.raise_for_status()
    assign_payload = assign_resp.json()
    assign_err = assign_payload.get("error") or {}
    if isinstance(assign_err, dict) and assign_err.get("message"):
        raise ValueError(assign_err["message"])

    bank = (assign_payload.get("data") or {}).get("bank") or {}
    upi_id = bank.get("upi_id") or ""
    acc_holder_name = bank.get("acc_holder_name") or ""
    txn_code = bank.get("code") or merchant_code
    if not upi_id:
        raise ValueError("PayBitra did not return UPI payment details")

    upi_uri = build_upi_uri(upi_id, acc_holder_name, amount, txn_code)
    return {
        "amount": str(amount),
        "currency": "INR",
        "upi_id": upi_id,
        "acc_holder_name": acc_holder_name,
        "code": txn_code,
        "upi_uri": upi_uri,
        "paybitra_order_id": order_id,
        "payin_id": data.get("payinId", ""),
        "expires_at": data.get("expirationDate", ""),
        "pay_in_url": data.get("payInUrl", ""),
        "message": "Complete the UPI payment and submit your UTR.",
    }


def submit_paybitra_utr(order_id: str, utr: str, amount: Decimal, code: str = "") -> dict[str, Any] | None:
    """Forward UTR to PayBitra for automatic payment verification."""
    if not order_id or not utr:
        return None

    base = _cfg("PAYBITRA_API_BASE", DEFAULT_BASE).rstrip("/")
    body: dict[str, Any] = {
        "userSubmittedUtr": utr,
        "amount": int(amount),
    }
    if code:
        body["code"] = code

    try:
        resp = requests.post(f"{base}/payIn/process/{order_id}", json=body, timeout=30)
        resp.raise_for_status()
        return resp.json()
    except Exception as exc:
        logger.warning("PayBitra UTR submit failed for order %s: %s", order_id, exc)
        return None
