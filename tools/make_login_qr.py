#!/usr/bin/env python3
"""
Generates an encrypted PrimaBarcode login QR code.

The app decrypts these with the key baked into its build (`loginQrKey` in local.properties).
The same key must be passed here, so a code only works with builds carrying that key —
rotating the key means rebuilding the app and reissuing every code.

    # one-off, prints the payload to paste into any QR generator
    python make_login_qr.py --user 'PRIMA-COMMERCE\\filip' --key "$KEY"

    # writes filip.png directly (needs: pip install qrcode[pil])
    python make_login_qr.py --user 'PRIMA-COMMERCE\\filip' --key "$KEY" --out filip.png

    # generate a fresh key to put in local.properties
    python make_login_qr.py --new-key

The password is prompted for rather than passed as an argument, so it doesn't end up in shell
history or in the process list where anyone on the machine could read it.

Requires: pip install cryptography
"""

import argparse
import base64
import getpass
import json
import os
import sys

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    sys.exit("Missing dependency. Run: pip install cryptography")

IV_BYTES = 12
KEY_BYTES = 32


def new_key() -> str:
    return base64.b64encode(os.urandom(KEY_BYTES)).decode()


def encrypt(username: str, password: str, key_b64: str) -> str:
    key = base64.b64decode(key_b64)
    if len(key) != KEY_BYTES:
        sys.exit(f"Key must be base64 for exactly {KEY_BYTES} bytes (got {len(key)}).")
    # separator=(',', ':') keeps the payload compact, which keeps the QR less dense
    plaintext = json.dumps(
        {"username": username, "password": password}, separators=(",", ":")
    ).encode()
    iv = os.urandom(IV_BYTES)
    ciphertext = AESGCM(key).encrypt(iv, plaintext, None)
    return base64.b64encode(iv + ciphertext).decode()


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--new-key", action="store_true", help="print a fresh key and exit")
    ap.add_argument("--user", help="username, including domain if used (DOMAIN\\user or user@domain)")
    ap.add_argument("--key", help="base64 AES-256 key; falls back to $PRIMA_QR_KEY")
    ap.add_argument("--out", help="write a PNG here instead of printing the payload")
    args = ap.parse_args()

    if args.new_key:
        print(new_key())
        return

    if not args.user:
        ap.error("--user is required (or use --new-key)")

    key_b64 = args.key or os.environ.get("PRIMA_QR_KEY")
    if not key_b64:
        ap.error("no key: pass --key or set PRIMA_QR_KEY")

    password = getpass.getpass(f"Password for {args.user}: ")
    if not password:
        sys.exit("Password must not be empty — the app rejects codes missing either field.")

    payload = encrypt(args.user, password, key_b64)

    if not args.out:
        print(payload)
        return

    try:
        import qrcode
    except ImportError:
        sys.exit("PNG output needs: pip install 'qrcode[pil]'  (or drop --out to print the payload)")

    # High error correction: these get printed, taped up and scanned in warehouse lighting.
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_H, box_size=8, border=4)
    qr.add_data(payload)
    qr.make(fit=True)
    qr.make_image(fill_color="black", back_color="white").save(args.out)
    print(f"Wrote {args.out} ({len(payload)} chars, QR version {qr.version})")


if __name__ == "__main__":
    main()
