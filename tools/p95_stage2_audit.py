#!/usr/bin/env python3
"""Read-only P95 stage-two pre-Play static inventory; no source or user data is changed."""
from __future__ import annotations
import argparse
import collections
import csv
import hashlib
import json
import re
import struct
from pathlib import Path
import xml.etree.ElementTree as ET

FLAGS = [
    ("CRITICAL", "unimplemented_click", re.compile(r"onClick\s*=\s*\{\s*\}")),
    ("HIGH", "runtime_todo", re.compile(r"\b(?:TODO|NotImplementedError)\s*\(")),
    ("REVIEW", "coming_soon_copy", re.compile(r"قريب[ً\u064b]?ا|قريباً|coming soon", re.I)),
    ("REVIEW", "developer_copy", re.compile(r"(?i)\b(?:placeholder|FIXME|TEST ONLY|DEBUG ONLY)\b")),
    ("REVIEW", "obsolete_alarm_copy", re.compile("لا نطلب صلاحية المنبه الدقيق")),
    ("REVIEW", "unsecured_http", re.compile(r"http://(?!localhost|127\.0\.0\.1|10\.0\.2\.2)")),
]
AUDIO_SUFFIXES = {".mp3",".m4a",".aac",".wav",".ogg",".opus",".flac"}
EXPECTED_GROUPS = {"quran_pages_colored":604,"quran_pages":604,"quran_verse_hitmaps":604}
def line_number(source, offset): return source.count("\n",0,offset)+1
def sound_signature(path):
    with path.open("rb") as fh: b=fh.read(16)
    if path.suffix.lower()==".mp3":return b[:3]==b"ID3" or (len(b)>1 and b[0]==255 and (b[1]&224)==224)
    if path.suffix.lower()==".wav":return b.startswith(b"RIFF") and b[8:12]==b"WAVE"
    if path.suffix.lower() in (".ogg",".opus"):return b.startswith(b"OggS")
    if path.suffix.lower()==".flac":return b.startswith(b"fLaC")
    if path.suffix.lower()==".m4a":return b[4:8]==b"ftyp"
    return None
def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--source",required=True)
    ap.add_argument("--output",required=True)
    ap.add_argument("--screenshots")
    args=ap.parse_args()
    source=Path(args.source).resolve();out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
    assert (source/"app/build.gradle.kts").is_file(),"source root not found"
    gradle=(source/"app/build.gradle.kts").read_text(encoding="utf-8")
    manifests=list((source/"app/src/main").glob("AndroidManifest.xml"))
    manifest=ET.parse(manifests[0]).getroot()
    kotlin=sorted((source/"app/src/main/java").rglob("*.kt"))
    tests=sorted((source/"app/src/androidTest").rglob("*.kt"))
    allfiles=[p for p in source.rglob("*") if p.is_file() and not any(s in p.parts for s in ("build",".gradle",".git"))]
    screens=[]; findings=[]
    composables=0; click_handlers=0; text_calls=0; line_count=0
    for p in kotlin:
        s=p.read_text(encoding="utf-8",errors="replace")
        rel=p.relative_to(source).as_posix()
        line_count+=s.count("\n")+1
        composables+=len(re.findall(r"@Composable\b",s))
        click_handlers+=len(re.findall(r"onClick\s*=",s))
        text_calls+=len(re.findall(r"\bText\s*\(",s))
        if "Screen" in p.name or "screen" in rel.lower():screens.append(rel)
        for severity,kind,rx in FLAGS:
            for match in list(rx.finditer(s))[:250]:
                findings.append({"severity":severity,"kind":kind,"file":rel,"line":line_number(s,match.start()),
                                "token":match.group(0)[:60]})
        for match in re.finditer(r"Text\s*\([^\n]{0,30}\"([^\"\n]{155,})\"",s):
            findings.append({"severity":"REVIEW","kind":"long_inline_text","file":rel,"line":line_number(s,match.start()),"token":"long visible Text literal"})
    counts={}; broken_json=[]; bad_images=[]
    for key,expected in EXPECTED_GROUPS.items():
        d=source/"app/src/main/assets"/key
        suffix=".json" if key=="quran_verse_hitmaps" else ".webp"
        files=sorted(d.glob("*"+suffix)) if d.is_dir() else []
        counts[key]=len(files)
        if key=="quran_verse_hitmaps":
            for p in files:
                try:json.loads(p.read_text(encoding="utf-8"))
                except (UnicodeError,ValueError) as exc:broken_json.append({"file":p.name,"error":str(exc)[:100]})
        else:
            for p in files:
                with p.open("rb") as fh: magic=fh.read(12)
                if len(magic)<12 or magic[:4]!=b"RIFF" or magic[8:12]!=b"WEBP":bad_images.append(p.name)
    audio=[p for p in allfiles if p.suffix.lower() in AUDIO_SUFFIXES]
    audio_invalid=[];audio_unverified=[]
    for p in audio:
        sig=sound_signature(p)
        if sig is False:audio_invalid.append(p.relative_to(source).as_posix())
        elif sig is None:audio_unverified.append(p.relative_to(source).as_posix())
    screenshot_list=[]
    if args.screenshots and Path(args.screenshots).exists():
        for p in Path(args.screenshots).rglob("*.png"):
            b=p.read_bytes()
            valid=b[:8]==b"\x89PNG\r\n\x1a\n" and len(b)>=24
            dims=struct.unpack(">II",b[16:24]) if valid else []
            screenshot_list.append({"file":p.name,"valid_png":valid,"dimensions":list(dims)})
    names={"app_files":len(allfiles),"kotlin_files":len(kotlin),"kotlin_lines":line_count,
           "android_test_files":len(tests),"screen_named_files":len(screens),
           "composable_annotations":composables,"click_bindings":click_handlers,"text_calls":text_calls,
           "local_audio_files":len(audio)}
    fatal=[]
    for key,n in counts.items():
        if n!=EXPECTED_GROUPS[key]:fatal.append(f"{key}: expected {EXPECTED_GROUPS[key]}, got {n}")
    if broken_json:fatal.append(f"Invalid Quran hitmap JSON: {len(broken_json)}")
    if bad_images:fatal.append(f"Invalid WebP signatures: {len(bad_images)}")
    if audio_invalid:fatal.append(f"Invalid local audio signatures: {len(audio_invalid)}")
    if "versionCode = 119" not in gradle:fatal.append("P95 versionCode 119 not found")
    if "targetSdk = 36" not in gradle:fatal.append("2026 Play targetSdk 36 not found")
    if not manifests:fatal.append("AndroidManifest missing")
    findings.sort(key=lambda f:({"CRITICAL":0,"HIGH":1,"REVIEW":2}[f["severity"]],f["file"],f["line"]))
    permissions=sorted(e.attrib.get("{http://schemas.android.com/apk/res/android}name","")
       for e in manifest.findall("uses-permission"))
    findings_by_kind=dict(collections.Counter(x["kind"] for x in findings))
    result={"source_label":"P95 VC119 safe source read-only","status":"FAIL" if fatal else "PASS_STATIC_INVARIANTS",
        "counts":names,"quran_assets":counts,"permissions":permissions,
        "findings_by_kind":findings_by_kind,"finding_count":len(findings),
        "findings":findings,"audio_invalid":audio_invalid,"audio_unverified_formats":audio_unverified,
        "invalid_hitmap_json":broken_json,"invalid_quran_webp":bad_images,
        "screens":screens,"screenshots":screenshot_list,"critical_errors":fatal,
        "manual_gates":["Actual visual inspection on physical Android phone",
        "Rights documentation for every recitation, adhan, and ibtehal track",
        "Public account-deletion web path for password AND Google sign-in",
        "Production DB backup and demonstrated restore",
        "Production V1 shutoff with tested client migration",
        "Signed AAB, original app signing certificate, Play Data Safety declaration"]}
    (out/"stage2_audit.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
    with (out/"stage2_findings.csv").open("w",encoding="utf-8",newline="") as fh:
        w=csv.DictWriter(fh,fieldnames=["severity","kind","file","line","token"]);w.writeheader();w.writerows(findings)
    lines=["# P95 / VC119 — Read-only Android Phase 2 audit","","## Static results",
       f"Status: **{result['status']}**",f"Main Kotlin files: {len(kotlin)}; lines: {line_count}",
       f"Composable annotations: {composables}; click bindings (not an exhaustive button test): {click_handlers}",
       f"Screen-named files: {len(screens)}; linked screenshots: {len(screenshot_list)}",
       f"Quran page counts: {counts}",f"Audio files: {len(audio)}; malformed: {len(audio_invalid)}",
       f"Review flags: {findings_by_kind}","","## Scope limits",
       "This is a static source/asset inspection, not full button-by-button UI testing, a subjective visual review,",
       "audio listening, rights clearance, or verification of live production data.",
       "Every detected line is a REVIEW CANDIDATE: dead or hidden UI may be harmless.",
       "No source, runtime database, audio file, permissions, or deployed server was modified.","",
       "## Mandatory pre-release gates"]+[f"- {x}" for x in result["manual_gates"]]
    (out/"stage2_report.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
    print("STAGE2_AUDIT "+json.dumps({"status":result["status"],"counts":names,"assets":counts,
           "flags":findings_by_kind,"screenshot_count":len(screenshot_list),"fatal":fatal},ensure_ascii=False))
    for x in findings[:30]: print("FINDING "+json.dumps(x,ensure_ascii=False))
    if fatal:raise SystemExit(1)
if __name__=="__main__":main()
