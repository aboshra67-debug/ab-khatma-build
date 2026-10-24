"""Builds the bundled verse timings of surat An-Nazi'at (79) for the VC132 audio test from the experiment results."""
import json
out = "android_p105/assets/p105_timing/%s_79.txt"
def write(app_id, bounds, total):
    assert len(bounds) == 45 and total > bounds[-1]
    prev = 0
    seq = []
    for k in range(46):
        end = bounds[k] if k < 45 else total
        assert end > prev + 300, (app_id, k + 1, prev, end)
        seq.append((k + 1, prev, end)); prev = end
    open(out % app_id, "w").write("\n".join("%d,%d,%d" % t for t in seq))
    print(app_id, "ok", seq[:3], seq[-1])
a = json.load(open("results/p105_align_jibreel_79.json"))
v = a["verses"]
write("jibreel", [v[k + 1]["start_ms"] for k in range(45)], a["surah_ms"])
for app_id, rid in (("maher", "maher"), ("banna_murattal", "banna"), ("yasser_salama", "yasser")):
    d = json.load(open("results/p105_dtw_%s_79.json" % rid))
    write(app_id, [b["dtw_ms"] for b in d["bounds"]], d["surah_ms"])
