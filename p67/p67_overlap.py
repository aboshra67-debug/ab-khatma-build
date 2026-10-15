"""P67: finds, for every verse file of an EveryAyah reciter, whether its end already contains the beginning of
the NEXT verse (the reciter joined the two verses — وصل — and the file was cut a little late, e.g. «…المستقيم صـ»).

The last 0.3 s of the voice in verse N is searched inside the first seconds of verse N+1. When the very same
sound is found there (normalized correlation >= 0.8, the files come from one recording), verse N must stop where
that sound begins, and the app goes straight on to verse N+1. Nothing is cut when the match is not clear.

usage: python3 p67_overlap.py <everyayah_folder> <out.json> [surah numbers...]
out: { "surah": [[cutMs, headMs] for every verse] }
  cutMs  = play this verse file only up to here (0 = the whole file)
  headMs = where the voice starts in this file (used to start it without its silence after a joined verse)
"""
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import subprocess  # noqa: E402

from p67_align import AYAS, SR, fetch, ncc_search, voice_span  # noqa: E402

HSR = 16000  # for the letter check (needs the high frequencies of «ص س ش ز ث»)


def decode(data: bytes, sr=SR) -> np.ndarray:
    out = subprocess.run(["ffmpeg", "-v", "error", "-i", "pipe:0", "-ac", "1", "-ar", str(sr), "-f", "f32le", "pipe:1"],
                         input=data, capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32).astype(np.float64)


def frames(x, sr):
    """per 10 ms: level (dB) and share of energy above 3.5 kHz (hiss of «ص س ش ز ث ف»)."""
    w = sr // 100
    n = len(x) // w
    if n == 0:
        return np.array([-120.0]), np.array([0.0])
    f = x[: n * w].reshape(n, w)
    db = 20 * np.log10(np.sqrt((f ** 2).mean(1)) + 1e-9)
    spec = np.abs(np.fft.rfft(f * np.hanning(w), axis=1)) ** 2
    fr = np.fft.rfftfreq(w, 1 / sr)
    hi = spec[:, fr > 3500].sum(1) / (spec.sum(1) + 1e-12)
    return db, hi


def hiss_join(x, y):
    """Verse N ends abruptly on the first hiss of a letter and verse N+1 starts with that hissing letter:
    returns how many ms to drop from the end of N (0 = not this case)."""
    dx, hx = frames(x, HSR)
    dy, hy = frames(y, HSR)
    thr_x = max(dx.max() - 35, -55)
    on_x = np.nonzero(dx > thr_x)[0]
    on_y = np.nonzero(dy > max(dy.max() - 35, -55))[0]
    if len(on_x) < 50 or len(on_y) < 5:
        return 0
    e = on_x[-1]
    voiced = dx[on_x[0]:e + 1]
    if dx[max(on_x[0], e - 2):e + 1].max() < np.median(voiced) - 12:   # the voice fades out normally
        return 0
    k = e
    while k > on_x[0] and hx[k] >= 0.5:
        k -= 1
    run = e - k                              # frames of hiss at the very end
    if not (2 <= run <= 8):                  # a short cut-off hiss, not a held final «سْ»
        return 0
    y0 = on_y[0]
    if hy[y0:y0 + 3].mean() < 0.6:          # the next verse does not start with a hissing letter
        return 0
    return int(run) * 10 + 10

TAIL = int(0.30 * SR)
HEAD = int(3.0 * SR)
GOOD = 0.80


def surah_cuts(folder, s):
    n = AYAS[s]

    def verse(a):
        return fetch(f"https://everyayah.com/data/{folder}/{s:03d}{a:03d}.mp3")

    with ThreadPoolExecutor(8) as ex:
        raw = list(ex.map(verse, range(1, n + 1)))
    verses = [decode(r) for r in raw]
    wide = [decode(r, HSR) for r in raw]
    cuts = [[0, int(voice_span(v)[0] * 1000 / SR)] for v in verses]
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
            cuts[i][0] = int(cut * 1000 / SR)
            rows.append((i + 1, "same-sound", round(score, 3), int(overlap * 1000 / SR), True))
            continue
        drop = hiss_join(wide[i], wide[i + 1])
        if drop:
            _, e1 = voice_span(x)
            cuts[i][0] = max(0, int(e1 * 1000 / SR) - drop)
            rows.append((i + 1, "hiss", drop, True))
        else:
            rows.append((i + 1, "none", round(score, 3), int(overlap * 1000 / SR), False))
    return cuts, rows


def debug_pair(folder, s, a):
    x = decode(fetch(f"https://everyayah.com/data/{folder}/{s:03d}{a:03d}.mp3"), HSR)
    y = decode(fetch(f"https://everyayah.com/data/{folder}/{s:03d}{a + 1:03d}.mp3"), HSR)
    dx, hx = frames(x, HSR)
    dy, hy = frames(y, HSR)
    on_x = np.nonzero(dx > max(dx.max() - 35, -55))[0]
    on_y = np.nonzero(dy > max(dy.max() - 35, -55))[0]
    e = int(on_x[-1])
    lines = [f"{s}:{a} len={len(dx)} voice_end_frame={e} median={np.median(dx[on_x[0]:e + 1]):.1f} join={hiss_join(x, y)}"]
    for k in range(max(0, e - 40), min(len(dx), e + 5)):
        lines.append(f"  x {k:5d} {dx[k]:6.1f} hi={hx[k]:.2f}")
    y0 = int(on_y[0])
    for k in range(max(0, y0 - 3), y0 + 12):
        lines.append(f"  y {k:5d} {dy[k]:6.1f} hi={hy[k]:.2f}")
    return "\n".join(lines)


def main():
    import os
    if os.environ.get("DEBUG_PAIRS"):
        out = []
        for pair in os.environ["DEBUG_PAIRS"].split():
            s, a = map(int, pair.split(":"))
            out.append(debug_pair(sys.argv[1], s, a))
        Path(sys.argv[2] + ".debug.txt").write_text("\n".join(out))
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
        joined = [r for r in rows if r[-1]]
        near = [r for r in rows if not r[-1] and r[2] >= 0.6]
        info = {"surah": s, "pairs": len(rows), "joined": len(joined), "doubtful": len(near),
                "examples": joined[:4], "doubt_examples": near[:3], "secs": round(time.time() - t0, 1)}
        report.append(info)
        print(json.dumps(info, ensure_ascii=False), flush=True)
    out.write_text(json.dumps(result, separators=(",", ":")))
    Path(str(out) + ".report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))
    print("joined verses:", sum(r["joined"] for r in report), "doubtful:", sum(r["doubtful"] for r in report))


if __name__ == "__main__":
    main()
