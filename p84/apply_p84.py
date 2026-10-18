"""Apply only the audited VC107 -> VC108 overlay; reject a mismatched baseline."""
import hashlib
import json
import shutil
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
patch = Path(__file__).resolve().parent
manifest = json.loads((patch / 'manifest.json').read_text())
for item in manifest:
    target = root / item['path']
    actual = hashlib.sha256(target.read_bytes()).hexdigest() if target.exists() else None
    if actual != item['before']:
        raise SystemExit('Unexpected baseline: ' + item['path'])
for item in manifest:
    source = patch / 'overlay' / item['path']
    if hashlib.sha256(source.read_bytes()).hexdigest() != item['after']:
        raise SystemExit('Corrupt patch: ' + item['path'])
    target = root / item['path']
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
print('P84 VC108 privacy review applied; original login, preferences and audio preserved')
