#!/usr/bin/env python3
"""Patch accounts/views.py for PayBitra UPI deposit integration."""

from pathlib import Path
import sys

NEW_INITIATE_DEPOSIT = '''
def initiate_deposit(request):
    """Generate dynamic UPI deposit details via PayBitra."""
    amount_raw = request.data.get('amount')
    method = (request.data.get('method') or 'UPI').upper()
    try:
        amount = _parse_amount(amount_raw)
    except ValueError as exc:
        return Response({'error': str(exc)}, status=status.HTTP_400_BAD_REQUEST)

    if amount < 100:
        return Response({'error': 'Minimum deposit amount is ₹100'}, status=status.HTTP_400_BAD_REQUEST)

    if amount < 500:
        return Response(
            {'error': 'Use standard payment methods for deposits below ₹500.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    upi_methods = {'UPI', 'QR', 'GPAY', 'PAYTM', 'PHONEPE'}
    if method not in upi_methods and 'UPI' not in method:
        return Response(
            {'error': 'PayBitra integration is only available for UPI deposits.'},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        from accounts.paybitra_client import initiate_upi_deposit
        result = initiate_upi_deposit(amount)
        return Response(result)
    except ValueError as exc:
        return Response({'error': str(exc)}, status=status.HTTP_400_BAD_REQUEST)
    except Exception as exc:
        logger.exception('PayBitra initiate deposit failed: %s', exc)
        return Response(
            {'error': 'Unable to initiate deposit. Please try again.'},
            status=status.HTTP_502_BAD_GATEWAY,
        )
'''

SUBMIT_UTR_MARKER = "logger.info(f\"Deposit request (UTR) created:"
SUBMIT_UTR_INSERT = """
        paybitra_order_id = (request.data.get('paybitra_order_id') or '').strip()
        paybitra_code = (request.data.get('paybitra_code') or '').strip()
"""

SUBMIT_UTR_AFTER_CREATE = """
        if paybitra_order_id and utr:
            try:
                from accounts.paybitra_client import submit_paybitra_utr
                submit_paybitra_utr(paybitra_order_id, utr, amount, paybitra_code)
            except Exception as paybitra_exc:
                logger.warning(
                    'PayBitra UTR forward failed for user %s order %s: %s',
                    request.user.username,
                    paybitra_order_id,
                    paybitra_exc,
                )
"""


def replace_function(content: str, name: str, new_body: str) -> str:
    start = content.find(f"def {name}(request):")
    if start == -1:
        raise SystemExit(f"{name} function not found")

    rest = content[start + 1 :]
    next_def = rest.find("\ndef ")
    if next_def == -1:
        return content[:start] + new_body.strip() + "\n"

    end = start + 1 + next_def + 1
    return content[:start] + new_body.strip() + "\n\n" + content[end:]


def patch_submit_utr(content: str) -> str:
    if "paybitra_order_id" in content and "submit_paybitra_utr" in content:
        return content

    marker = "        deposit = DepositRequest.objects.create("
    idx = content.find(marker, content.find("def submit_utr(request):"))
    if idx == -1:
        raise SystemExit("submit_utr deposit create block not found")

    content = content[:idx] + SUBMIT_UTR_INSERT + content[idx:]

    log_idx = content.find(SUBMIT_UTR_MARKER)
    if log_idx == -1:
        raise SystemExit("submit_utr log marker not found")

    line_end = content.find("\n", log_idx)
    content = content[: line_end + 1] + SUBMIT_UTR_AFTER_CREATE + content[line_end + 1 :]

    create_idx = content.find(
        "payment_reference=utr,\n            payment_method=payment_method,\n            status='PENDING',",
        content.find("def submit_utr(request):"),
    )
    if create_idx != -1:
        old = (
            "payment_reference=utr,\n            payment_method=payment_method,\n            status='PENDING',"
        )
        new = (
            "payment_reference=utr,\n            payment_method=payment_method,\n            "
            "payment_link=f'paybitra://{paybitra_order_id}' if paybitra_order_id else '',\n            "
            "status='PENDING',"
        )
        content = content.replace(old, new, 1)

    return content


def main() -> None:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "/app/accounts/views.py")
    text = path.read_text(encoding="utf-8")
    text = replace_function(text, "initiate_deposit", NEW_INITIATE_DEPOSIT)
    text = patch_submit_utr(text)
    path.write_text(text, encoding="utf-8")
    print(f"Patched {path}")


if __name__ == "__main__":
    main()
