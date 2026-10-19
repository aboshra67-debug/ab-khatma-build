#!/usr/bin/env python3
"""P96/VC120: strictly guarded, copy-only Arabic corrections from P95 baseline.

Only known literal UI strings are replaced, simultaneously updating callers that
compare these same strings. No networking, permissions, DB, navigation, audio,
Quran or signing configuration is changed. Abort if the baseline has drifted.
"""
import hashlib
import json
import sys
from pathlib import Path

EXPECTED = {
    "app/src/main/java/com/ab/khatma/secure/SecureV2Api.kt": [
        ("الخادم التجريبي الآمن غير مُعد بعد", "خدمة تسجيل الدخول غير متاحة حاليًا"),
        ("الخدمة غير متاحة على خادم التجربة", "الخدمة غير متاحة حاليًا"),
    ],
    "app/src/main/java/com/ab/khatma/secure/P48ReaderSchedule.kt": [
        ("الخدمة غير متاحة على خادم التجربة", "الخدمة غير متاحة حاليًا"),
    ],
    "app/src/main/java/com/ab/khatma/secure/P50LeaderPlans.kt": [
        ("الخدمة غير متاحة على خادم التجربة", "الخدمة غير متاحة حاليًا"),
    ],
    "app/src/main/java/com/ab/khatma/secure/KhatmaPolishedPanels.kt": [
        ("احتفظ برقم الحساب للدخول إلى النسخة التجريبية.", "احتفظ برقم حسابك لتسجيل الدخول."),
    ],
    "app/src/main/java/com/ab/khatma/ui/screens/AppRoot.kt": [
        ("اكتب اسمك للبدء. التحقق برقم الهاتف سيضاف في مرحلة الأمان.", "اكتب اسمك للبدء."),
    ],
    "app/src/main/java/com/ab/khatma/ui/screens/PrayerSettingsScreen.kt": [
        ("يمكن لاحقًا إضافة تصحيح دقائق يدوي إذا احتجنا مطابقة تقويم مسجد معين.",
         "قد تختلف المواقيت قليلًا عن تقويم مسجدك حسب طريقة الحساب والموقع."),
    ],
    "app/build.gradle.kts": [
        ("versionCode = 119", "versionCode = 120"),
        ('versionName = "0.19.90-p95-preplay-safe"',
         'versionName = "0.19.91-p96-stage2-copy-safe"'),
    ],
}

def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python tools/p96_copy_only_patch.py /path/to/P95-source-root")
    root = Path(sys.argv[1]).resolve()
    if not (root/"app/build.gradle.kts").is_file():
        raise SystemExit("FAIL CLOSED: wrong source root.")
    plans={}
    for rel, changes in EXPECTED.items():
        path=root/rel
        if not path.is_file():
            raise SystemExit(f"FAIL CLOSED: missing file {rel}")
        original=path.read_bytes()
        body=original.decode("utf-8")
        for before,after in changes:
            # P95 SecureV2Api uses the same error copy in both the response
            # mapping and its retry classification. Replace BOTH together.
            expected_count = (2 if rel.endswith("/SecureV2Api.kt")
                              and before == "الخدمة غير متاحة على خادم التجربة" else 1)
            if body.count(before) != expected_count:
                raise SystemExit(f"FAIL CLOSED: baseline drift at {rel}: {before[:55]!r} (count {body.count(before)}, expected {expected_count})")
            if body.count(after) != 0:
                raise SystemExit(f"FAIL CLOSED: replacement unexpectedly present in {rel}")
            body=body.replace(before,after,expected_count)
        plans[path]=(original,body.encode("utf-8"),rel)
    # Only mutate the vetted files after every guard passes.
    ledger=[]
    for path,(old,new,rel) in plans.items():
        assert old!=new
        path.write_bytes(new)
        ledger.append({
            "file":rel,"original_sha256":hashlib.sha256(old).hexdigest(),
            "patched_sha256":hashlib.sha256(new).hexdigest(),
            "changes":len(EXPECTED[rel]),
        })
    for rel,changes in EXPECTED.items():
        data=(root/rel).read_text("utf-8")
        assert all(old not in data and new in data for old,new in changes)
    assert "versionCode = 120" in (root/"app/build.gradle.kts").read_text("utf-8")
    target=root/"BUILD_REPRODUCIBILITY"/"p96"
    target.mkdir(parents=True,exist_ok=True)
    (target/"safe_patch_ledger.json").write_text(json.dumps({
        "origin":"P95 VC119 successful artifact 39270578361",
        "stage":"P96 copy-only corrections (version bump required for reproducibility)",
        "files":ledger,
        "audio_modified":False,
        "quran_modified":False,
        "network_modified":False,
        "permissions_modified":False,
    },ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print("P96 SAFE COPY PATCH APPLIED: "+json.dumps(ledger,ensure_ascii=False))
if __name__=="__main__":
    main()
