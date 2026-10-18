#!/usr/bin/env python3
import sys,json,hashlib,shutil
from pathlib import Path
base=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
manifest=json.loads((here/'manifest.json').read_text())
for item in manifest['files']:
    path=base/item['path'];overlay=here/'overlay'/item['path']
    assert hashlib.sha256(overlay.read_bytes()).hexdigest()==item['after'],item['path']
    current=hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
    assert current in (item['before'],item['after']),f'Unexpected baseline: {item["path"]}'
for item in manifest['files']:
    path=base/item['path'];path.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(here/'overlay'/item['path'],path)
print('P85 VC109 guide, source-measured title frames, bounded adhan playback and opt-in listening applied')
