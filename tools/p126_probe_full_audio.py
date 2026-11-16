#!/usr/bin/env python3
import json,re,urllib.request,urllib.parse
L=[('ا','Alif A.wav'),('ب','Baaudio.wav'),('ت','Taaudio.wav'),('ث','Thaaudio.wav'),('ج','Jeemaudio.wav'),('ح','Haaudio.wav'),('خ','Khaaudio.wav'),('د','Dalaudio.wav'),('ذ','Dzalaudio.wav'),('ر','RAAUDIO.wav'),('ز','ZaiAUDIO.wav'),('س','Senaudio.wav'),('ش','Shenaudio.wav'),('ص','Sadaudio.wav'),('ض','Ddadaudio.wav'),('ط','Ttaaudio.wav'),('ظ','Dddaudio.wav'),('ع','Aaaudio.wav'),('غ','Ghaudio.wav'),('ف','Faaudio.wav'),('ق','Qafaudio.wav'),('ك','Kafaudio.wav'),('ل','Lamudio.wav'),('م','Maudio.wav'),('ن','Naudio.wav'),('ه','Haaaudio.wav'),('و','Wawaudio.wav'),('ي','Yaaudio.wav')]
api="https://en.wikiversity.org/w/api.php?"+urllib.parse.urlencode({"action":"parse","page":"Learn Arabic (Language Of Quran)/Alphabets","prop":"wikitext","format":"json"})
with urllib.request.urlopen(urllib.request.Request(api,headers={"User-Agent":"AB-Khatma-Educational/1.0 (absoftware.eg@gmail.com)"}),timeout=50) as f:
 s=json.load(f)["parse"]["wikitext"]["*"]
positions=[s.lower().index("[[file:"+n.lower()) for ch,n in L]
for i,(ch,name) in enumerate(L):
 section=s[positions[i]:(positions[i+1] if i+1<len(L) else len(s))]
 matches=re.findall(r'\[\[File:([^|\]\n]+\.wav)[^\]\n]*\]\]\s*\|\|[^|\n]*\|\|([^|\n]+)\|\|',section,re.I)
 print(ch,name,len(matches),repr(matches[:4]))
 assert len(matches)>=3, ch+" lacks 3 speaking examples"
