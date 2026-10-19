#!/usr/bin/env python3
"""Fail-closed Android patch: one live HTTPS V2 API host, no legacy Railway host in release.

Runs *after* the independently checked P96 copy-only patch.
Only Android UpdateClient allowlist and build version metadata change.
"""
from pathlib import Path
import hashlib
import json
import sys

assert len(sys.argv)==2, "Usage: p97_official_endpoint_patch.py SOURCE_ROOT"
root=Path(sys.argv[1]).resolve()
changes={
"app/src/main/java/com/ab/khatma/updates/UpdateClient.kt":[
('private const val TEST_HOST = "khatma-v2-api-staging.up.railway.app"',
 'private val activeApiHost get() = Uri.parse(BuildConfig.V2_API_BASE_URL).host.orEmpty()'),
('private val downloadHosts = setOf("github.com", TEST_HOST)',
 'private val downloadHosts get() = setOf("github.com", activeApiHost)'),
('if (base.host != TEST_HOST) return null // staging-only until production is reviewed',
 'if (base.host != activeApiHost || base.encodedAuthority?.contains("@") == true) return null'),
],
"app/build.gradle.kts":[
('versionCode = 120','versionCode = 121'),
('versionName = "0.19.91-p96-stage2-copy-safe"',
 'versionName = "0.19.92-p97-single-server-safe"'),
],
}
plan=[]
for rel,pairs in changes.items():
    path=root/rel
    original=path.read_bytes()
    source=original.decode("utf-8")
    for old,new in pairs:
        if source.count(old)!=1 or (new not in old and new in source):
            raise SystemExit(f"FAIL-CLOSED: {rel} drift on {old[:48]!r}")
        source=source.replace(old,new,1)
    plan.append((path,rel,original,source.encode("utf-8")))
for path,rel,old,new in plan:
    path.write_bytes(new)
out=root/"BUILD_REPRODUCIBILITY"/"p97"
out.mkdir(parents=True,exist_ok=True)
(out/"single_server_patch_ledger.json").write_text(json.dumps({
    "reproducibility":"From P95 source, apply P96 guarded patch, then P97 guarded patch",
    "only_live_api":"Configured Gradle KHATMA_V2_API_BASE_URL, same as KHATMA_API_BASE_URL",
    "changed_files":[{"path":rel,"before_sha256":hashlib.sha256(old).hexdigest(),
      "after_sha256":hashlib.sha256(new).hexdigest()} for _,rel,old,new in plan],
    "removed_legacy_code":False,"quran_modified":False,"audio_modified":False,
    "database_modified":False,"manifest_modified":False,
},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print("P97 SAFE SINGLE SERVER PATCH APPLIED",",".join(x[1] for x in plan))
