#!/usr/bin/env python3
"""Research-only download of human Qur'an candidates. NEVER mark as tajweed-approved."""
from pathlib import Path
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen
import hashlib, html, json, re, subprocess, time
from urllib.error import HTTPError
FILES = [
 ("001", "Chapter 1, Al-Fatiha (Murattal) - Recitation of the Holy Qur'an.mp3", "CC0"),
 ("108", "Chapter 108, Al-Kawthar (Murattal) - Recitation of the Holy Qur'an.mp3", "CC0"),
 ("110", "Chapter 110, An-Nasr (Murattal) v2 - Recitation of the Holy Qur'an.mp3", "CC0"),
 ("112", "Chapter 112, Al-Ikhlas (Murattal) - Recitation of the Holy Qur'an.mp3", "CC BY-SA 4.0"),
 ("113", "Chapter 113, Al-Falaq (Murattal) - Recitation of the Holy Qur'an.mp3", "CC0"),
 ("114", "Chapter 114, Al-Nas (Murattal) - Recitation of the Holy Qur'an.mp3", "CC0"),
]
UA = "ABKhatma-staging-audio-license-audit/1.0 (educational evaluation, not distribution)"
OUT = Path("v45_quarantine")
OUT.mkdir(parents=True, exist_ok=True)
audit = []
def get_bytes(url, cap=6000000):
    last = None
    for attempt in range(4):
        try:
            with urlopen(Request(url, headers={"User-Agent": UA}), timeout=50) as r:
                data = r.read(cap + 1)
                if len(data) > cap: raise ValueError("Download exceeds size cap")
                return data
        except HTTPError as exc:
            if exc.code == 429:
                raise RuntimeError("WIKIMEDIA_RATE_LIMIT_429: access denied; stop without retry or bypass")
            last = exc
            time.sleep((attempt + 1) * 4)
        except TimeoutError as exc:
            last = exc
            time.sleep((attempt + 1) * 4)
    raise RuntimeError(str(last))
try:
    for number, name, intended_license in FILES:
        params = urlencode(dict(action="query", prop="imageinfo",
          iiprop="url|sha1|size|mime|user|extmetadata", titles="File:" + name, format="json"))
        doc = json.loads(get_bytes("https://commons.wikimedia.org/w/api.php?" + params, 1000000))
        page = list(doc["query"]["pages"].values())[0]
        info = page["imageinfo"][0]
        license_seen = html.unescape(info["extmetadata"]["LicenseShortName"]["value"])
        simple = re.sub("<[^>]*>", "", license_seen).upper().replace(" ", "")
        if intended_license == "CC0":
            assert "CC0" in simple or "CC-ZERO" in simple, (number, license_seen)
        else:
            assert "BY-SA" in simple and "4.0" in simple, (number, license_seen)
        assert info["mime"] == "audio/mpeg", number
        assert int(info["size"]) < 5000000, number
        assert urlparse(info["url"]).hostname == "upload.wikimedia.org", number
        assert urlparse(info["url"]).scheme == "https", number
        raw = get_bytes(info["url"], 5000000)
        sha1 = hashlib.sha1(raw).hexdigest()
        assert len(raw) == int(info["size"]), number
        assert sha1 == info["sha1"].lower(), number
        path = OUT / ("surah_" + number + "_UNAPPROVED.mp3")
        path.write_bytes(raw)
        proc = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
          "-of", "json", str(path)], capture_output=True, text=True, check=True)
        seconds = float(json.loads(proc.stdout)["format"]["duration"])
        assert seconds > 5 and seconds < 360, number
        audit.append(dict(surah=number, original_file=name, metadata_file_size=info["size"],
          source_sha1=sha1, local_sha256=hashlib.sha256(raw).hexdigest(),
          duration_seconds=seconds, source_uploader=info.get("user"),
          declared_license=intended_license, current_license_metadata=license_seen,
          source_file_page="https://commons.wikimedia.org/wiki/File:" + name.replace(" ", "_"),
          status="QUARANTINE_LICENSE_AND_TECH_CHECK_ONLY",
          teacher_verified=False, verse_alignment_verified=False, android_verified=False,
          public_playback_allowed=False))
        print("QUARANTINED", number, len(raw), round(seconds, 2), flush=True)
finally:
    (OUT / "evidence_NO_EDUCATIONAL_APPROVAL.json").write_text(
      json.dumps(audit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Finished. 0 files allowed into APK. Teacher/editor rights review pending.")
