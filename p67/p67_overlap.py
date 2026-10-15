"""P67: finds, for every verse file of an EveryAyah reciter, whether its end already contains the beginning of
the NEXT verse (the reciter joined the two verses — وصل — and the file was cut a little late, e.g. «…المستقيم صـ»).

The last 0.3 s of the voice in verse N is searched inside the first seconds of verse N+1. When the very same
sound is found there (normalized correlation >= 0.8, the files come from one recording), verse N must stop where
that sound begins, and the app goes straight on to verse N+1. Nothing is cut when the match is not clear.

usage: python3 p67_overlap.py <everyayah_folder> <out.json> [surah numbers...]
out: { "surah": [cutMs or 0 for every verse] }  (cut = play verse N only up to this time)
"""
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from p67_align import AYAS, SR, decode, fetch, ncc_search, voice_span  # noqa: E402

TAIL = int(0.30 * SR)
HEAD = int(3.0 * SR)
GOOD = 0.80


def surah_cuts(folder, s):
    n = AYAS[s]

    def verse(a):
        return decode(fetch(f"https://everyayah.com/data/{folder}/{s:03d}{a:03d}.mp3"))

    with ThreadPoolExecutor(8) as ex:
        verses = list(ex.map(verse, range(1, n + 1)))
    cuts = [0] * n
    rows = []
    for i in range(n - 1):
        x, y = verses[i], verses[i + 1]
        x0, x1 = voice_span(x)
        y0, _ = voice_span(y)
        if x1 - x0 < TAIL * 2:
            continue
        tpl = x[x1 - TAIL:x1]
        o, score = ncc_search(y, tpl, 0, min(len(y), y0 + HEAD))
        if o is None:
            continue
        overlap = (o + TAIL) - y0          # how much of verse N+1's voice is already at the end of verse N
        cut = x1 - overlap - int(0.03 * SR)
        ok = score >= GOOD and overlap >= int(0.12 * SR) and o >= y0 - int(0.05 * SR) and cut > x0 + SR
        if ok:
            cuts[i] = int(cut * 1000 / SR)
        rows.append((i + 1, round(score, 3), int(overlap * 1000 / SR), ok))
    return cuts, rows


def main():
    folder, out = sys.argv[1], Path(sys.argv[2])
    surahs = [int(x) for x in sys.argv[3:]] or list(range(1, 115))
    result, report = {}, []
    for s in surahs:
        t0 = time.time()
        try:
            cuts, rows = surah_cuts(folder, s)
        except Exception as e:  # noqa: BLE001
            print(json.dumps({"surah": s, "error": str(e)}), flush=True)
            continue
        result[str(s)] = cuts
        joined = [r for r in rows if r[3]]
        near = [r for r in rows if not r[3] and r[1] >= 0.6]
        info = {"surah": s, "pairs": len(rows), "joined": len(joined), "doubtful": len(near),
                "examples": joined[:4], "doubt_examples": near[:3], "secs": round(time.time() - t0, 1)}
        report.append(info)
        print(json.dumps(info, ensure_ascii=False), flush=True)
    out.write_text(json.dumps(result, separators=(",", ":")))
    Path(str(out) + ".report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))
    print("joined verses:", sum(r["joined"] for r in report), "doubtful:", sum(r["doubtful"] for r in report))


if __name__ == "__main__":
    main()
