#!/usr/bin/env python3
"""P108 Kotlin compile-safe one-line patch; strict guard against unexpected changes."""
from pathlib import Path
import sys
src=Path(sys.argv[1]) if len(sys.argv)==2 else None
if src is None or not src.is_file():
    raise SystemExit("Expected P108MushafUi.kt file path")
s=src.read_text()
old="modifier=Modifier.fillMaxWidth().padding(top=4.dp,horizontal=8.dp)"
new="modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp).padding(top=4.dp)"
if s.count(old)!=1:
    raise RuntimeError("P108 fixed guard expected exactly one source expression")
src.write_text(s.replace(old,new))
print("P108 SAFE Kotlin compile patch applied.")
