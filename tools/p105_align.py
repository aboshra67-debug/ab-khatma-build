"""Experiment: locate every EveryAyah verse file inside the whole-surah recording (same reciter) by waveform matching.
Output: results/p105_align_<id>_<surah>.json  (start/end of every verse inside the surah file + match score)."""
import json, os, subprocess, sys, urllib.request, numpy as np
os.makedirs("results", exist_ok=True)
from numpy.fft import rfft, irfft

SR = 8000

def fetch(url, path):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
        f.write(r.read())

def decode(path):
    out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32).astype(np.float64)

def match(win, x):
    n = len(win) + len(x)
    nfft = 1 << (n - 1).bit_length()
    c = irfft(rfft(win, nfft) * np.conj(rfft(x, nfft)), nfft)[: len(win) - len(x) + 1]
    e = np.concatenate([[0], np.cumsum(win ** 2)])
    loc = e[len(x):] - e[: len(win) - len(x) + 1]
    sc = c / (np.sqrt(loc * np.sum(x ** 2)) + 1e-9)
    k = int(np.argmax(sc))
    return k, float(sc[k])

def run(rid, surah_url, verse_fmt, surah, nverses):
    fetch(surah_url, "/tmp/surah.mp3")
    S = decode("/tmp/surah.mp3")
    res = {"id": rid, "surah": surah, "surah_ms": int(len(S) / SR * 1000), "verses": []}
    pos = 0
    cum = 0
    for v in range(1, nverses + 1):
        fetch(verse_fmt % (surah, v), "/tmp/v.mp3")
        x = decode("/tmp/v.mp3")
        lo = max(0, min(pos, len(S) - len(x)) - 2 * SR)
        hi = min(len(S), pos + len(x) + 10 * SR)
        if hi - lo < len(x) + 1:
            res["verses"].append({"v": v, "error": "window empty", "pos_ms": int(pos / SR * 1000), "len_ms": int(len(x) / SR * 1000)})
            break
        k, sc = match(S[lo:hi], x)
        start = lo + k
        if sc < 0.5:  # retry with the whole file
            k, sc = match(S, x)
            start = k
        res["verses"].append({"v": v, "start_ms": int(start / SR * 1000), "end_ms": int((start + len(x)) / SR * 1000),
                              "len_ms": int(len(x) / SR * 1000), "cum_ms": int(cum / SR * 1000), "score": round(sc, 3)})
        pos = start + len(x)
        cum += len(x)
    sc = [a["score"] for a in res["verses"] if "score" in a]
    res["min_score"] = min(sc)
    res["mean_score"] = round(sum(sc) / len(sc), 3)
    json.dump(res, open("results/p105_align_%s_%d.json" % (rid, surah), "w"), ensure_ascii=False, indent=1)
    print(rid, surah, "min", res["min_score"], "mean", res["mean_score"])

if __name__ == "__main__":
    surah = int(sys.argv[1]); nv = int(sys.argv[2])
    E = "https://everyayah.com/data/%s/%%03d%%03d.mp3"
    cfg = [
        ("maher", "https://cdn.mp3quran.net/audio/maher-muaiqly/r1/%03d.mp3" % surah, E % "MaherAlMuaiqly128kbps"),
        ("banna", "https://cdn.mp3quran.net/audio/mahmoud-banna/r1/%03d.mp3" % surah, E % "mahmoud_ali_al_banna_32kbps"),
        ("jibreel", "https://download.quranicaudio.com/quran/muhammad_jibreel/complete/%03d.mp3" % surah, E % "Muhammad_Jibreel_128kbps"),
        ("yasser", "https://cdn.mp3quran.net/audio/yasser-salama/r1/%03d.mp3" % surah, E % "Yaser_Salamah_128kbps"),
    ]
    only = sys.argv[3] if len(sys.argv) > 3 else None
    for rid, su, vf in cfg:
        if only and rid != only:
            continue
        try:
            run(rid, su, vf, surah, nv)
        except Exception as ex:
            import traceback; traceback.print_exc()
            print(rid, "FAILED", repr(ex))
            json.dump({"id": rid, "error": repr(ex)}, open("results/p105_align_%s_%d.json" % (rid, surah), "w"))
