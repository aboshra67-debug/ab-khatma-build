"""Fail-closed Android preview signer. Private signing data exists ONLY as Actions secrets.

Required repository secrets:
  KHATMA_PREVIEW_KEYSTORE_B64
  KHATMA_PREVIEW_STORE_PASSWORD

Public certificate pin below prevents accidental key rotation. Production uses a
different application ID and MUST use a distinct release signing process.
"""
import base64
import os
import re
import subprocess
import sys
from pathlib import Path

EXPECTED_SHA256 = "3248c1ebdd2973a992a453919a4207317cdecfc4cf0c7e8dc28dd7d9a5468b41"
ALIAS = "khatma_preview"


def private_dir():
    return Path(os.environ["RUNNER_TEMP"]) / "ab-khatma-preview-private"


def key_file():
    return private_dir() / "preview-signing.p12"


def cert_digest(path):
    pw = os.environ.get("KHATMA_PREVIEW_STORE_PASSWORD", "")
    if len(pw) < 16:
        raise RuntimeError("Missing KHATMA_PREVIEW_STORE_PASSWORD GitHub Actions secret")
    env = dict(os.environ)
    # Use keytool env syntax; never put passwords in process argv or logs.
    result = subprocess.run(
        ["keytool", "-list", "-v", "-storetype", "PKCS12",
         "-keystore", str(path), "-alias", ALIAS,
         "-storepass:env", "KHATMA_PREVIEW_STORE_PASSWORD"],
        env=env, capture_output=True, text=True,
    )
    if result.returncode:
        raise RuntimeError("Cannot verify provided keystore or alias (secret not printed)")
    match = re.search(r"SHA256:\s*([0-9a-fA-F:]+)", result.stdout)
    if not match:
        raise RuntimeError("Keystore certificate SHA-256 unavailable")
    return match.group(1).replace(":", "").lower()


def prepare():
    encoded = os.environ.get("KHATMA_PREVIEW_KEYSTORE_B64", "")
    if not encoded:
        raise RuntimeError("Missing KHATMA_PREVIEW_KEYSTORE_B64 GitHub Actions secret")
    if len(encoded) > 200000:
        raise RuntimeError("Signing secret exceeds expected limit")
    try:
        keystore = base64.b64decode(encoded, validate=True)
    except Exception as exc:
        raise RuntimeError("Keystore secret is not valid single-line base64") from exc
    if not 1000 <= len(keystore) <= 100000:
        raise RuntimeError("Unexpected size for preview PKCS12 keystore")
    folder = private_dir()
    folder.mkdir(mode=0o700, exist_ok=True)
    folder.chmod(0o700)
    path = key_file()
    if path.exists():
        raise RuntimeError("Refusing to replace already prepared keystore")
    path.write_bytes(keystore)
    path.chmod(0o600)
    digest = cert_digest(path)
    if digest != EXPECTED_SHA256:
        path.unlink(missing_ok=True)
        raise RuntimeError("Signing certificate mismatch; REFUSING key rotation")
    print("PASS stable signing certificate pinned; no debug fallback")


def checked(command, env=None):
    result = subprocess.run(command, env=env, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Android signing/verification command failed; exit="+str(result.returncode))
    return result.stdout + result.stderr


def sign():
    key = key_file()
    if not key.is_file():
        raise RuntimeError("Stable signing keystore not prepared: cannot distribute APK")
    if cert_digest(key) != EXPECTED_SHA256:
        raise RuntimeError("Stable signing certificate changed: REFUSING APK")
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "36.0.0"
    zipalign, apksigner = sdk / "zipalign", sdk / "apksigner"
    assert zipalign.is_file() and apksigner.is_file(), "Android build-tools 36 unavailable"
    build_dir = Path("app/build/outputs/apk/debug")
    candidates = list(build_dir.glob("*-unsigned.apk"))
    if len(candidates) != 1:
        raise RuntimeError("Expected exactly one UNSIGNED debug APK. No fallback to random key.")
    unsigned = candidates[0]
    aligned = private_dir() / "preview-aligned.apk"
    output = build_dir / "app-preview-stable.apk"
    checked([str(zipalign), "-f", "-P", "16", "4", str(unsigned), str(aligned)])
    checked([str(zipalign), "-c", "-P", "16", "4", str(aligned)])
    env = dict(os.environ)
    checked([str(apksigner), "sign",
             "--ks", str(key), "--ks-type", "PKCS12",
             "--ks-key-alias", ALIAS,
             "--ks-pass", "env:KHATMA_PREVIEW_STORE_PASSWORD",
             "--key-pass", "env:KHATMA_PREVIEW_STORE_PASSWORD",
             "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
             "--v3-signing-enabled", "true",
             "--out", str(output), str(aligned)], env=env)
    report = checked([str(apksigner), "verify", "--verbose", "--print-certs", str(output)])
    match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)", report)
    if not match or match.group(1).lower() != EXPECTED_SHA256:
        output.unlink(missing_ok=True)
        raise RuntimeError("Final APK signature fingerprint mismatch. APK rejected.")
    if "Verified using v2 scheme (APK Signature Scheme v2): true" not in report:
        output.unlink(missing_ok=True)
        raise RuntimeError("Final APK missing verified v2 signature")
    print("PASS signed APK with pinned permanent preview key")
    print("Signer certificate SHA-256:", EXPECTED_SHA256)
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
        f.write("### AB Khatma preview APK signing\n"
                "- Signature: permanent pinned preview keystore\n"
                "- SHA-256: "+EXPECTED_SHA256+"\n"
                "- No ephemeral debug signing fallback\n")


if __name__ == "__main__":
    try:
        {"prepare": prepare, "sign": sign}[sys.argv[1]]()
    except Exception as e:
        print("ERROR: "+str(e), file=sys.stderr)
        sys.exit(1)
