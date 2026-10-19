#!/usr/bin/env python3
"""P97 Google Play artifact checks, read-only. Inspect build bytes not source promises."""
from __future__ import annotations
import sys,json,re,hashlib
from pathlib import Path
from zipfile import ZipFile, BadZipFile

if len(sys.argv)!=4:
    raise SystemExit("usage: python p97_verify_release_artifacts.py APK AAB REPORTDIR")
apk,aab,output=map(Path,sys.argv[1:])
output.mkdir(parents=True,exist_ok=True)
report={"release":"P97 VC121","baseline_run_id":39290967444,"checks":{},"warnings":[]}
def check(name,passed,detail):
    report["checks"][name]={"pass":bool(passed),"detail":detail}
    print(("PASS" if passed else "FAIL"),name,detail)
def inspect(path,which):
    with ZipFile(path) as z:
        failed=z.testzip()
        names=z.namelist()
        check(which+"_zip_crc",failed is None,"OK" if failed is None else f"bad entry {failed}")
        check(which+"_nonempty",len(names)>50,f"{len(names)} entries")
        check(which+"_sha256",True,hashlib.sha256(path.read_bytes()).hexdigest())
        if which=="apk":
            colored=[n for n in names if re.fullmatch(r"assets/quran_pages_colored/[^/]+\.webp",n)]
            hitmap=[n for n in names if re.fullmatch(r"assets/quran_verse_hitmaps/[^/]+\.json",n)]
            check("quran_colored_604",len(colored)==604,f"{len(colored)}")
            check("quran_hitmap_604",len(hitmap)==604,f"{len(hitmap)}")
            check("apk_unsigned_review_only",not any(n.startswith("META-INF/") and n.lower().endswith((".rsa",".dsa",".ec")) for n in names),"release review not authorized for installation or Play upload")
            dex=[n for n in names if re.fullmatch(r"classes\d*\.dex",n)]
            check("compiled_dex_present",len(dex)>=1,str(dex))
            forbidden_hosts=[b"balanced-creativity-production-f316.up.railway.app"]
            mandatory_host=b"khatma-v2-api-staging.up.railway.app"
            raw=b"".join(z.read(n) for n in dex)
            for forbidden in forbidden_hosts:
                check("no_old_production_host",forbidden not in raw,forbidden.decode())
            check("one_v2_host_in_dex",mandatory_host in raw,"configured V2 host is compiled in release")
            report["warnings"].append("The V2 domain still includes the word 'staging'; this is one existing live Railway service. Naming a service is not proof of production controls.")
        else:
            check("aab_base_manifest",any(n.startswith("base/manifest/") for n in names),"base module manifest present")
            check("aab_base_dex",any(n.startswith("base/dex/") and n.endswith(".dex") for n in names),"base module dex present")
inspect(apk,"apk")
inspect(aab,"aab")
report["overall"]="PASS" if all(v["pass"] for v in report["checks"].values()) else "FAIL"
report["not_proved"]=["Original owner signing and Play App Signing certificate",
"Every Android device size and playback-to-end",
"All audio licensing rights for streaming and downloaded media",
"Production PostgreSQL tested backup/restore",
"Public web deletion request accessible without application to Google-auth users",
"Published privacy policy and Play Console Data Safety review"]
(output/"p97_artifact_release_gate.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print("RESULT",report["overall"],"checks",len(report["checks"]))
if report["overall"]!="PASS":raise SystemExit(1)
