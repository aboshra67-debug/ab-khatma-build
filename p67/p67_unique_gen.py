"""P67: builds the «منفردات» data from the app's OWN Quran text (nothing is copied from any book).
A word of an ayah is a «منفردة» when a short phrase around it (4 to 6 words) occurs in ONE ayah only,
while the same phrase with just that one word changed occurs elsewhere in the Quran."""
import collections
import json
import re


def norm(w):
    w = re.sub('[\u0610-\u061a\u064b-\u065f\u0670\u06d6-\u06ed\u0640\u08d3-\u08ff]', '', w)
    w = re.sub('[\u0671\u0623\u0625\u0622]', '\u0627', w).replace('\u0649', '\u064a')
    return w


def build(uthmani_path):
    data = json.load(open(uthmani_path, encoding="utf-8"))
    A = {}
    for a in data:
        raw = a["text"].split()
        assert raw == [w for w in a["text"].split(" ") if w], (a["surah"], a["ayah"])
        idx = [i for i, w in enumerate(raw) if norm(w)]
        A[(a["surah"], a["ayah"])] = (raw, idx, [norm(raw[i]) for i in idx])
    found = {}  # (ayah, rawIndex) -> [level, {variantWord: ref}]
    for n in (6, 5, 4):
        level = n - 3  # 3 = strongest (a long phrase that differs by one word only), 1 = weakest
        g = collections.defaultdict(list)
        for k, (raw, idx, ns) in A.items():
            for i in range(len(ns) - n + 1):
                g[tuple(ns[i:i + n])].append((k, i))
        keys = collections.defaultdict(set)
        for t in g:
            for j in range(n):
                keys[t[:j] + (None,) + t[j + 1:]].add(t)
        for t, pos in g.items():
            if len(pos) != 1:
                continue
            k, i = pos[0]
            raw, idx, ns = A[k]
            for j in range(n):
                vs = keys[t[:j] + (None,) + t[j + 1:]] - {t}
                if not vs:
                    continue
                key = (k, idx[i + j])
                if key in found:
                    continue
                variants = {}
                for v in sorted(vs):
                    (k2, i2) = g[v][0]
                    r2, idx2, _ = A[k2]
                    variants.setdefault(v[j], (r2[idx2[i2 + j]], k2))
                found[key] = (level, list(variants.values())[:5])
    out = {}
    for (k, wi), (level, variants) in sorted(found.items()):
        out.setdefault(f"{k[0]}:{k[1]}", []).append(
            [wi, level, [[w, f"{r[0]}:{r[1]}"] for w, r in variants]]
        )
    return out


if __name__ == "__main__":
    import sys
    o = build(sys.argv[1])
    s = json.dumps(o, ensure_ascii=False, separators=(",", ":"))
    print("ayahs", len(o), "items", sum(len(v) for v in o.values()), "bytes", len(s.encode()))
    lv = collections.Counter(i[1] for v in o.values() for i in v)
    print(lv)
    print(o.get("2:2"), o.get("2:26"))
