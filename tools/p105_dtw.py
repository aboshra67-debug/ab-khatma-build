"""Experiment 2: same reciter, DIFFERENT recordings. Align the concatenated EveryAyah verse files to the whole-surah file
with MFCC + DTW, snap every verse boundary to the quietest moment, report results. Jibreel (exact match known) = ground truth."""
import json, os, subprocess, sys
import numpy as np, librosa

SR = 16000
os.makedirs("results", exist_ok=True)

def fetch(url, path):
    subprocess.run(["curl", "-sS", "-L", "--fail", "--max-time", "90", "--retry", "2", "-A", "Mozilla/5.0", "-o", path, url], check=True)

def decode(path):
    out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32).copy()

def feats(y):
    m = librosa.feature.mfcc(y=y, sr=SR, n_mfcc=20, n_fft=1024, hop_length=320)[1:]
    m = (m - m.mean(1, keepdims=True)) / (m.std(1, keepdims=True) + 1e-6)
    return m.astype(np.float32)

def rms_env(y, win=800):  # 50 ms
    c = np.concatenate([[0], np.cumsum(y.astype(np.float64) ** 2)])
    n = len(y) - win
    return np.sqrt((c[win:win + n] - c[:n]) / win)  # env[i] = rms of y[i:i+win]

def run(rid, surah_url, verse_fmt, surah, nv, truth=None):
    fetch(surah_url, "/tmp/s.mp3")
    S = decode("/tmp/s.mp3")
    parts, bounds, tot = [], [], 0
    for v in range(1, nv + 1):
        fetch(verse_fmt % (surah, v), "/tmp/v.mp3")
        x = decode("/tmp/v.mp3")
        parts.append(x); tot += len(x); bounds.append(tot)
    X = np.concatenate(parts)
    fx, fs = feats(X), feats(S)
    D, wp = librosa.sequence.dtw(X=fx, Y=fs, metric="cosine", subseq=False)
    wp = wp[::-1]
    hop = 320
    env = rms_env(S)
    out = []
    for k in range(nv - 1):
        fi = bounds[k] // hop
        js = wp[wp[:, 0] == fi][:, 1]
        j = int(np.median(js)) if len(js) else int(wp[np.argmin(np.abs(wp[:, 0] - fi))][1])
        c = j * hop
        lo, hi = max(0, c - 4800), min(len(env) - 1, c + 4800)  # +-300 ms
        s = lo + int(np.argmin(env[lo:hi]))
        out.append({"after_verse": k + 1, "dtw_ms": int(c / SR * 1000), "snap_ms": int(s / SR * 1000),
                    "snap_rms": int(env[s] * 32768), "typ_rms": int(np.median(env) * 32768)})
    res = {"id": rid, "surah_ms": int(len(S) / SR * 1000), "concat_ms": int(len(X) / SR * 1000), "bounds": out}
    if truth:
        t = json.load(open(truth))["verses"]
        inside, errs = 0, []
        for k in range(nv - 1):
            a, b = t[k]["end_ms"], t[k + 1]["start_ms"]
            sm = out[k]["snap_ms"]
            e = 0 if a - 60 <= sm <= b + 60 else min(abs(sm - a), abs(sm - b))
            errs.append(e); inside += (e == 0)
        res["truth_inside"] = inside; res["truth_total"] = nv - 1; res["truth_max_err_ms"] = max(errs)
        res["truth_errs"] = errs
    json.dump(res, open("results/p105_dtw_%s_%d.json" % (rid, surah), "w"), indent=1)
    print(rid, "done", {k: v for k, v in res.items() if k.startswith("truth_") and k != "truth_errs"}, flush=True)

if __name__ == "__main__":
    surah = int(sys.argv[1]); nv = int(sys.argv[2])
    E = "https://everyayah.com/data/%s/%%03d%%03d.mp3"
    cfg = [
        ("jibreel", "https://download.quranicaudio.com/quran/muhammad_jibreel/complete/%03d.mp3" % surah, E % "Muhammad_Jibreel_128kbps", "results/p105_align_jibreel_79.json"),
        ("maher", "https://cdn.mp3quran.net/audio/maher-muaiqly/r1/%03d.mp3" % surah, E % "MaherAlMuaiqly128kbps", None),
        ("banna", "https://cdn.mp3quran.net/audio/mahmoud-banna/r1/%03d.mp3" % surah, E % "mahmoud_ali_al_banna_32kbps", None),
        ("yasser", "https://cdn.mp3quran.net/audio/yasser-salama/r1/%03d.mp3" % surah, E % "Yaser_Salamah_128kbps", None),
    ]
    for rid, su, vf, tr in cfg:
        try:
            run(rid, su, vf, surah, nv, tr)
        except Exception as ex:
            import traceback; traceback.print_exc()
            json.dump({"id": rid, "error": repr(ex)}, open("results/p105_dtw_%s_%d.json" % (rid, surah), "w"))
