#!/usr/bin/env python3
"""
Add POST auth/deposits/upi-callback/ (and deposits/upi-callback/) to accounts/views.py + urls.

Accepts: session_id / paybitra_order_id, utr, txn_id, status
Reuses the same deposit create + PayBitra UTR forward path as submit_utr.
"""

from pathlib import Path
import sys

CALLBACK_FN = r'''
@api_view(['POST'])
@permission_classes([IsAuthenticated])
def upi_deposit_callback(request):
    """
    APK UPI intent SUCCESS callback.
    Body: session_id (or paybitra_order_id), utr, txn_id, status
    Credits / records the same way as submit_utr + PayBitra forward.
    """
    session_id = (
        request.data.get('session_id')
        or request.data.get('paybitra_order_id')
        or ''
    ).strip()
    utr = (request.data.get('utr') or request.data.get('ApprovalRefNo') or '').strip()
    txn_id = (request.data.get('txn_id') or '').strip()
    status_in = (request.data.get('status') or 'SUCCESS').strip().upper()
    amount_raw = request.data.get('amount')
    paybitra_code = (request.data.get('paybitra_code') or '').strip()

    if status_in in ('FAILURE', 'FAILED'):
        return Response({'ok': False, 'status': status_in, 'error': 'Payment failed'}, status=status.HTTP_400_BAD_REQUEST)

    if not session_id:
        return Response({'error': 'session_id is required'}, status=status.HTTP_400_BAD_REQUEST)

    try:
        amount = _parse_amount(amount_raw) if amount_raw not in (None, '') else None
    except ValueError as exc:
        return Response({'error': str(exc)}, status=status.HTTP_400_BAD_REQUEST)

    # Prefer existing pending deposit for this PayBitra order
    deposit = None
    try:
        deposit = (
            DepositRequest.objects.filter(user=request.user, payment_link=f'paybitra://{session_id}')
            .order_by('-id')
            .first()
        )
    except Exception:
        deposit = None

    if deposit is None:
        if amount is None:
            return Response(
                {'error': 'amount is required when creating a new deposit from UPI callback'},
                status=status.HTTP_400_BAD_REQUEST,
            )
        payment_method = (request.data.get('payment_method') or 'UPI').upper()
        deposit = DepositRequest.objects.create(
            user=request.user,
            amount=amount,
            payment_reference=utr or txn_id or session_id,
            payment_method=payment_method,
            payment_link=f'paybitra://{session_id}',
            status='PENDING',
        )
        logger.info(
            'Deposit request (UPI callback) created: user=%s amount=%s session=%s utr=%s',
            request.user.username,
            amount,
            session_id,
            utr,
        )
    else:
        if utr and not deposit.payment_reference:
            deposit.payment_reference = utr
            deposit.save(update_fields=['payment_reference'])

    credited = None
    if utr and session_id:
        try:
            from accounts.paybitra_client import submit_paybitra_utr
            amt_for_pb = amount if amount is not None else deposit.amount
            submit_paybitra_utr(session_id, utr, amt_for_pb, paybitra_code)
        except Exception as paybitra_exc:
            logger.warning(
                'PayBitra UTR forward failed (upi-callback) user=%s order=%s: %s',
                request.user.username,
                session_id,
                paybitra_exc,
            )

    # If companion/admin already approved, report credited amount
    try:
        deposit.refresh_from_db()
        if str(deposit.status).upper() in ('APPROVED', 'SUCCESS', 'CREDITED', 'COMPLETED'):
            credited = str(deposit.amount)
    except Exception:
        pass

    return Response(
        {
            'ok': True,
            'status': str(getattr(deposit, 'status', 'PENDING')),
            'credited': credited,
            'session_id': session_id,
            'deposit_id': deposit.id,
            'message': 'UPI callback received',
        }
    )
'''


def ensure_callback_view(text: str) -> str:
    if "def upi_deposit_callback(request):" in text:
        print("upi_deposit_callback already present")
        return text
    # Append before end of file
    return text.rstrip() + "\n\n" + CALLBACK_FN.strip() + "\n"


def ensure_url(urls_text: str) -> str:
    if "upi-callback" in urls_text or "upi_deposit_callback" in urls_text:
        print("upi-callback url already present")
        return urls_text

    needle_auth = "path('deposits/submit-utr/'"
    insert = (
        "    path('deposits/upi-callback/', views.upi_deposit_callback, name='upi_deposit_callback'),\n"
        "    path('deposits/submit-utr/'"
    )
    if needle_auth in urls_text:
        return urls_text.replace(needle_auth, insert, 1)

    # Fallback: append near other deposit paths
    marker = "path('deposits/"
    idx = urls_text.find(marker)
    if idx == -1:
        raise SystemExit("Could not find deposits url patterns to patch")
    line_start = urls_text.rfind("\n", 0, idx) + 1
    addition = "    path('deposits/upi-callback/', views.upi_deposit_callback, name='upi_deposit_callback'),\n"
    return urls_text[:line_start] + addition + urls_text[line_start:]


def ensure_root_urls(root_urls: str) -> str:
    if "deposits/upi-callback" in root_urls:
        print("upi-callback already in dice_game/urls.py")
        return root_urls
    needle = "path('api/auth/deposits/initiate/', accounts_views.initiate_deposit, name='initiate_deposit'),"
    if needle not in root_urls:
        raise SystemExit("initiate path not found in dice_game/urls.py")
    insert = (
        "path('api/auth/deposits/upi-callback/', accounts_views.upi_deposit_callback, name='upi_deposit_callback'),\n"
        "    path('api/deposits/upi-callback/', accounts_views.upi_deposit_callback, name='upi_deposit_callback_alt'),\n"
        "    " + needle
    )
    return root_urls.replace(needle, insert, 1)


def main() -> None:
    views_path = Path(sys.argv[1] if len(sys.argv) > 1 else "/app/accounts/views.py")
    urls_path = Path(sys.argv[2] if len(sys.argv) > 2 else "/app/accounts/urls.py")
    root_urls_path = Path(sys.argv[3] if len(sys.argv) > 3 else "/app/dice_game/urls.py")

    views = views_path.read_text(encoding="utf-8")
    views = ensure_callback_view(views)
    views_path.write_text(views, encoding="utf-8")
    print(f"Patched views: {views_path}")

    if urls_path.exists():
        urls = urls_path.read_text(encoding="utf-8")
        urls = ensure_url(urls)
        urls_path.write_text(urls, encoding="utf-8")
        print(f"Patched urls: {urls_path}")
    else:
        print(f"WARNING: urls not found at {urls_path}")

    if root_urls_path.exists():
        root = root_urls_path.read_text(encoding="utf-8")
        root = ensure_root_urls(root)
        root_urls_path.write_text(root, encoding="utf-8")
        print(f"Patched root urls: {root_urls_path}")
    else:
        print(f"WARNING: root urls not found at {root_urls_path}")


if __name__ == "__main__":
    main()
