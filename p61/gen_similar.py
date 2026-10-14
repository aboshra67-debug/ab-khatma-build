"""Builds assets/quran/p61_similar.json from the bundled Tanzil text (verbatim, nothing retyped).

For every ayah the file lists the ayat that repeat a long run of the same words, best matches first,
as numbers surah*1000+ayah. The differing words are computed on the phone from the same Tanzil text.
Matching rule (deterministic, on words without diacritics):
  - the two ayat share a run of at least 3 identical consecutive words, and
  - the word-sequence similarity is at least 0.55 (or 0.45 with 6+ shared words), or the ayat are identical.
"""
import json, re, sys, difflib, collections

src, out = sys.argv[1], sys.argv[2]
data = json.load(open(src, encoding="utf-8"))

MARKS = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿـ]")
def norm_word(w):
    w = MARKS.sub("", w)
    for a in "ٱآأإ":
        w = w.replace(a, "ا")
    w = w.replace("ى", "ي").replace("ة", "ه").replace("ء", "")
    w = w.replace("ؤ", "و").replace("ئ", "ي")
    return w

BISMILLAH_LAST = "ٱلرَّحِيمِ"
def body(v):
    t = v["text"]
    if v["ayah"] == 1 and v["surah"] not in (1, 9):
        at = t.find(BISMILLAH_LAST)
        if 0 <= at <= 46 and t.startswith("ب"):
            t = t[at + len(BISMILLAH_LAST):].strip()
    return t

verses = []
for v in data:
    words = [norm_word(w) for w in body(v).split()]
    words = [w for w in words if w]
    verses.append((v["surah"] * 1000 + v["ayah"], words))

N = 3
index = collections.defaultdict(set)
for i, (_, ws) in enumerate(verses):
    for k in range(len(ws) - N + 1):
        index[tuple(ws[k:k + N])].add(i)

cand = collections.defaultdict(set)
for grams, ids in index.items():
    if len(ids) < 2:
        continue
    ids = sorted(ids)
    if len(ids) > 120:          # a phrase this common is not a useful "similar ayah" signal
        continue
    for a in ids:
        cand[a].update(i for i in ids if i != a)

result = {}
for i, others in cand.items():
    a_code, aw = verses[i]
    scored = []
    for j in others:
        b_code, bw = verses[j]
        sm = difflib.SequenceMatcher(None, aw, bw, autojunk=False)
        longest = sm.find_longest_match(0, len(aw), 0, len(bw)).size
        ratio = sm.ratio()
        matched = sum(b.size for b in sm.get_matching_blocks())
        if longest >= N and (aw == bw or ratio >= 0.55 or (matched >= 6 and ratio >= 0.45)):
            scored.append((-ratio, abs(b_code - a_code), b_code))
    scored.sort()
    if scored:
        result[str(a_code)] = [c for _, _, c in scored[:6]]

json.dump(result, open(out, "w"), separators=(",", ":"))
print("ayat with similar ayat:", len(result), "of", len(verses))
