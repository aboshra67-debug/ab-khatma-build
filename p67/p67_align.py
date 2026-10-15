"""P67: builds verse timings (start / end in ms) inside a reciter's full-surah recording by locating each
EveryAyah verse file inside the surah file (normalized cross-correlation of the real audio).

Only surahs where EVERY verse is found with a clear match are written; anything doubtful is left out, and the
app then keeps the verse-by-verse files for that surah. Nothing is guessed.

usage: python3 p67_align.py <everyayah_folder> <surah_base_url> <out.json> [surah numbers...]
"""
import json
import subprocess
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np

SR = 8000
PREROLL = 80  # ms before the voice onset (also absorbs the mp3 decoder delay)
UA = "Mozilla/5.0 (AB Khatma timing builder)"
here = Path(__file__).parent
meta = json.loads((here / "surahs_meta.json").read_text(encoding="utf-8"))
AYAS = {m["index"]: m["ayas"] for m in meta}


def fetch(url, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            if i == tries - 1:
                raise RuntimeError(f"{url}: {e}")
            time.sleep(2 + 3 * i)


def decode(data: bytes) -> np.ndarray:
    out = subprocess.run(["ffmpeg", "-v", "error", "-i", "pipe:0", "-ac", "1", "-ar", str(SR), "-f", "f32le", "pipe:1"],
                         input=data, capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32).astype(np.float64)


def frames_db(x, win=160):
    n = len(x) // win
    if n == 0:
        return np.array([-120.0])
    e = np.sqrt(np.mean(x[: n * win].reshape(n, win) ** 2, axis=1)) + 1e-9
    return 20 * np.log10(e)


def voice_span(x):
    """(first, last) sample of the voice, ignoring leading / trailing silence."""
    db = frames_db(x)
    thr = max(db.max() - 35, -55)
    on = np.nonzero(db > thr)[0]
    if len(on) == 0:
        return 0, len(x)
    return int(on[0] * 160), int((on[-1] + 1) * 160)


def ncc_search(signal, template, lo, hi):
    """Best offset of [template] inside signal[lo:hi] with its normalized correlation (-1..1)."""
    lo = max(0, lo)
    hi = min(len(signal), hi + len(template))
    seg = signal[lo:hi]
    m = len(template)
    if len(seg) < m or m < 400:
        return None, 0.0
    t = template - template.mean()
    tn = np.sqrt((t ** 2).sum()) + 1e-12
    n = 1 << int(np.ceil(np.log2(len(seg) + m)))
    corr = np.fft.irfft(np.fft.rfft(seg, n) * np.conj(np.fft.rfft(t, n)), n)[: len(seg) - m + 1]
    c1 = np.concatenate([[0], np.cumsum(seg)])
    c2 = np.concatenate([[0], np.cumsum(seg ** 2)])
    s1 = c1[m:] - c1[:-m]
    s2 = c2[m:] - c2[:-m]
    var = np.maximum(s2 - s1 ** 2 / m, 1e-12)
    ncc = corr / (np.sqrt(var) * tn)
    k = int(np.argmax(ncc))
    return lo + k, float(ncc[k])


def align_surah(folder, base, s):
    n = AYAS[s]
    surah = decode(fetch(f"{base}{s:03d}.mp3"))
    total_ms = int(len(surah) * 1000 / SR)

    def verse(a):
        return decode(fetch(f"https://everyayah.com/data/{folder}/{s:03d}{a:03d}.mp3"))

    with ThreadPoolExecutor(8) as ex:
        verses = list(ex.map(verse, range(1, n + 1)))

    starts, scores, lens = [], [], []
    expected = 0
    for a, v in enumerate(verses, start=1):
        v0, v1 = voice_span(v)
        body = v[v0:v1]
        lens.append(len(body))
        tpl = body[: int(1.5 * SR)]
        found, score = ncc_search(surah, tpl, expected - 6 * SR, expected + 14 * SR)
        if found is None or score < 0.5:
            prev = starts[-1] if starts else 0
            found2, score2 = ncc_search(surah, tpl, prev, prev + lens[-1] * 2 + 40 * SR)
            if found2 is not None and score2 > score:
                found, score = found2, score2
        if found is None:
            return None, {"surah": s, "ayah": a, "why": "not found"}
        if starts and found <= starts[-1]:
            return None, {"surah": s, "ayah": a, "why": "not after previous", "score": round(score, 3)}
        starts.append(found)
        scores.append(score)
        expected = found + len(body)

    worst = min(scores)
    med = float(np.median(scores))
    info = {"surah": s, "ayas": n, "median": round(med, 3), "worst": round(worst, 3),
            "worst_ayah": int(np.argmin(scores)) + 1, "duration_ms": total_ms}
    if worst < 0.45 or med < 0.7:
        info["why"] = "weak match (different recording?)"
        return None, info

    times = {}
    for i, st in enumerate(starts):
        a = i + 1
        start_ms = max(0, int(st * 1000 / SR) - PREROLL)
        if a == 1 and s != 1:
            start_ms = 0  # keep the basmala / opening that precedes the first verse in the surah file
        if i + 1 < len(starts):
            end_ms = max(0, int(starts[i + 1] * 1000 / SR) - PREROLL)
        else:
            end_ms = min(total_ms, int((st + lens[i]) * 1000 / SR) + 400)
        if end_ms <= start_ms:
            info["why"] = f"bad span at ayah {a}"
            return None, info
        times[a] = [start_ms, end_ms]
    return times, info


def main():
    folder, base, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    surahs = [int(x) for x in sys.argv[4:]] or list(range(1, 115))
    result = json.loads(out.read_text()) if out.exists() else {}
    report = []
    for s in surahs:
        t0 = time.time()
        try:
            times, info = align_surah(folder, base, s)
        except Exception as e:  # noqa: BLE001
            times, info = None, {"surah": s, "why": f"error {e}"}
        info["secs"] = round(time.time() - t0, 1)
        info["ok"] = times is not None
        print(json.dumps(info, ensure_ascii=False), flush=True)
        report.append(info)
        if times is not None:
            result[str(s)] = [times[a] for a in range(1, AYAS[s] + 1)]
    out.write_text(json.dumps(result, separators=(",", ":")))
    Path(str(out) + ".report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))
    ok = sum(1 for r in report if r["ok"])
    print(f"aligned {ok}/{len(report)} surahs -> {out}")


if __name__ == "__main__":
    main()
