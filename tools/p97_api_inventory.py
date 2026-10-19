#!/usr/bin/env python3
"""Read-only inspection of Android backend coupling before changing release endpoints."""
from pathlib import Path
from collections import defaultdict
import re
import json
import sys

if len(sys.argv)!=2: raise SystemExit("Usage: python p97_api_inventory.py SOURCE_ROOT")
root=Path(sys.argv[1])
assert (root/"app/src/main").is_dir(),root
needles=[
    ("legacy_endpoint",re.compile(r"BuildConfig\.API_BASE_URL")),
    ("secure_endpoint",re.compile(r"BuildConfig\.V2_API_BASE_URL")),
    ("legacy_client",re.compile(r"\bApiClient\b")),
    ("legacy_direct_url",re.compile(r"https://balanced-creativity|balanced-creativity-production")),
    ("staging_direct_url",re.compile(r"staging\.up\.railway|khatma-v2-api-staging")),
    ("legacy_json",re.compile(r"(?<!/v2)/(?:groups|assignments|users)(?:[\"/{}]|$)")),
]
found=defaultdict(list)
for file in sorted(root.rglob("*.kt")):
    if "build" in file.parts or ".gradle" in file.parts:continue
    s=file.read_text("utf-8",errors="replace")
    for kind,pat in needles:
        for m in pat.finditer(s):
            no=s.count("\n",0,m.start())+1
            lines=s.splitlines()
            found[kind].append({"file":file.relative_to(root).as_posix(),"line":no,
                    "snippet":lines[no-1].strip()[:210]})
report={"source":str(root.name),"matches":dict(found),"counts":{k:len(v) for k,v in found.items()},
        "caution":"matches are not proof of code-path reachability; do not redirect legacy client without tracing"}
out=Path("release-preflight")
out.mkdir(exist_ok=True)
(out/"api_usage.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
print("P97 API INVENTORY",json.dumps(report["counts"],ensure_ascii=False))
for kind,matches in found.items():
    for item in matches[:75]:print("API_REFER",kind,json.dumps(item,ensure_ascii=False))
print("=== ACTIVE ENTRYPOINT & UPDATE CLIENT USAGE ===")
for rel in [
    "app/src/main/java/com/ab/khatma/MainActivity.kt",
    "app/src/main/java/com/ab/khatma/AppViewModel.kt",
    "app/src/main/java/com/ab/khatma/ui/screens/AppRoot.kt",
    "app/src/main/java/com/ab/khatma/updates/UpdateClient.kt",
    "app/src/main/java/com/ab/khatma/secure/SecureV2Screen.kt",
]:
    p=root/rel
    if not p.exists(): continue
    rows=p.read_text(encoding="utf-8",errors="replace").splitlines()
    print("ENTRYPOINT_FILE",rel,"total_lines",len(rows))
    if rel.endswith("UpdateClient.kt") or rel.endswith("AppRoot.kt") or rel.endswith("MainActivity.kt") or rel.endswith("AppViewModel.kt"):
        positions=range(min(len(rows),210))
    else:
        keys=("AppViewModel","AppRoot","SecureV2","setContent","Home","Screen","MainActivity","API_BASE_URL","TEST_HOST")
        positions=set()
        for idx,row in enumerate(rows):
            if any(key in row for key in keys):
                positions.update(range(max(0,idx-1),min(len(rows),idx+2)))
        positions=sorted(positions)[:110]
    for i in positions:
        print("ENTRY_LINE",rel,str(i+1),rows[i][:220])
print("P97 INVENTORY COMPLETE WITHOUT CHANGING SOURCE")
