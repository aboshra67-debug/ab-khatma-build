#!/usr/bin/env python3
"""Generate human-recorded OFFLINE letter names and real word examples.
Audio from original creator Ruaa GHAREEB, CC BY-SA 4.0, Wikimedia Commons.
WAV sources unchanged; not Quranic-Tajweed teacher-approved.
"""
import json,re,sys,time,hashlib,urllib.parse,urllib.request,wave,io
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
root=Path(sys.argv[1])
items=[
("ا","الألف","Alif A.wav"),("ب","الباء","Baaudio.wav"),("ت","التاء","Taaudio.wav"),
("ث","الثاء","Thaaudio.wav"),("ج","الجيم","Jeemaudio.wav"),("ح","الحاء","Haaudio.wav"),
("خ","الخاء","Khaaudio.wav"),("د","الدال","Dalaudio.wav"),("ذ","الذال","Dzalaudio.wav"),
("ر","الراء","RAAUDIO.wav"),("ز","الزاي","ZaiAUDIO.wav"),("س","السين","Senaudio.wav"),
("ش","الشين","Shenaudio.wav"),("ص","الصاد","Sadaudio.wav"),("ض","الضاد","Ddadaudio.wav"),
("ط","الطاء","Ttaaudio.wav"),("ظ","الظاء","Dddaudio.wav"),("ع","العين","Aaaudio.wav"),
("غ","الغين","Ghaudio.wav"),("ف","الفاء","Faaudio.wav"),("ق","القاف","Qafaudio.wav"),
("ك","الكاف","Kafaudio.wav"),("ل","اللام","Lamudio.wav"),("م","الميم","Maudio.wav"),
("ن","النون","Naudio.wav"),("ه","الهاء","Haaaudio.wav"),("و","الواو","Wawaudio.wav"),
("ي","الياء","Yaaudio.wav")]
assert len(items)==28
headers={"User-Agent":"AB-Khatma-learning/1.0 (absoftware.eg@gmail.com)"}
def fetch(url):
 for attempt in range(6):
  try:
   with urllib.request.urlopen(urllib.request.Request(url,headers=headers),timeout=60) as r:return r.read()
  except Exception as exc:
   if attempt==5:raise
   time.sleep(9+11*attempt if getattr(exc,'code',None)==429 else 1+2*attempt)
wiki_url="https://en.wikiversity.org/w/api.php?"+urllib.parse.urlencode(dict(action="parse",page="Learn Arabic (Language Of Quran)/Alphabets",prop="wikitext",format="json"))
wiki=json.loads(fetch(wiki_url))["parse"]["wikitext"]["*"]
position=[wiki.lower().index("[[file:"+n.lower()) for _,_,n in items]
rows=[]
allnames={}
for index,(ch,name,wav) in enumerate(items,1):
 section=wiki[position[index-1]:(position[index] if index<28 else len(wiki))]
 matches=re.findall(r'\[\[File:([^|\]\n]+\.wav)[^\]\n]*\]\]\s*\|\|[^|\n]*\|\|([^|\n]+)\|\|',section,re.I)
 assert len(matches)>=3,ch
 examples=[];seen=set()
 for audio,label in matches:
  match=re.search(r"[\u0621-\u064A][\u0621-\u064A\u064B-\u065F\u0670]*",label)
  if match and audio.lower() not in seen:
   examples.append((audio,match.group(0)))
   seen.add(audio.lower())
  if len(examples)==1:break
 assert len(examples)>=1,(ch,"no matched word")
 rows.append((ch,name,wav,examples))
 allnames[wav.lower()]=wav
 for n,txt in examples:allnames[n.lower()]=n
assert len(allnames)>=50,len(allnames)
info={}
commons="https://commons.wikimedia.org/w/api.php?"
names=list(allnames.values())
for start in range(0,len(names),18):
 subset=names[start:start+18]
 url=commons+urllib.parse.urlencode(dict(action="query",prop="imageinfo",iiprop="url|sha1|extmetadata",titles="|".join("File:"+n for n in subset),format="json"))
 doc=json.loads(fetch(url))
 for page in doc["query"]["pages"].values():
  assert page.get("imageinfo"),page
  meta=page["imageinfo"][0]
  lic=meta.get("extmetadata",{}).get("LicenseShortName",{}).get("value","")
  artist=meta.get("extmetadata",{}).get("Artist",{}).get("value","")
  assert "BY-SA" in lic and "4.0" in lic,(page["title"],lic)
  assert "Ruaa" in artist or "GHAREEB" in artist,(page["title"],artist[:100])
  key=page["title"].split(":",1)[1].replace("_"," ").lower()
  info[key]=(meta["url"],meta["sha1"])
assert len(info)==len(allnames),(len(info),len(allnames))
dest=root/"app/src/main/res/raw"
dest.mkdir(parents=True,exist_ok=True)
tasks=[]
for i,(ch,name,wav,examples) in enumerate(rows,1):
 tasks.append((wav,"p126_l%02d_name.wav"%i))
 for j,(s,label) in enumerate(examples,1):tasks.append((s,"p126_l%02d_e%d.wav"%(i,j)))
def download(task):
 src,name=task
 assert not (dest/name).exists()
 url,sha=info[src.lower()]
 time.sleep(2.1)
 data=fetch(url)
 assert hashlib.sha1(data).hexdigest()==sha,src
 with wave.open(io.BytesIO(data)) as w:
  assert w.getnchannels() in (1,2) and w.getsampwidth() in (1,2)
  assert .25<w.getnframes()/w.getframerate()<12
 (dest/name).write_bytes(data)
 return src,name,hashlib.sha256(data).hexdigest()
with ThreadPoolExecutor(max_workers=1) as pool:receipts=list(pool.map(download,tasks))
def q(s):return json.dumps(s,ensure_ascii=False)
lines=[
"package com.ab.khatma.ui.screens",
"import android.content.Context",
"import android.media.MediaPlayer",
"import com.ab.khatma.R",
"internal data class P126Sound(val label:String, val resId:Int)",
"internal data class P126Letter(val char:String,val name:String,val audio:P126Sound,val words:List<P126Sound>)",
"internal object P126Alphabet {",
"val letters:List<P126Letter> = listOf("]
for i,(ch,name,wav,examples) in enumerate(rows,1):
 words=",".join("P126Sound("+q(label)+",R.raw.p126_l%02d_e%d)"%(i,j) for j,(src,label) in enumerate(examples,1))
 lines.append("P126Letter("+q(ch)+","+q(name)+",P126Sound("+q(name)+",R.raw.p126_l%02d_name),listOf("%i+words+")),")
lines.extend([
")",
'fun forLesson(id:String):List<P126Letter> {',
'val chars=when(id) {',
'"F01","N01" -> "ابتثجحخدذرزسشصضطظعغفقكلمنهوي"',
'"F04" -> "اه"',
'"F05" -> "عح"',
'"F06" -> "غخ"',
'"F07" -> "قكجشي"',
'"F08" -> "ضلنر"',
'"F09" -> "طدتسصزظذث"',
'"F10" -> "فبمو"',
'"N02" -> "بتم"',
'"N04","N06" -> "ب"',
'else -> ""',
'}',
'return letters.filter { it.char in chars }',
'}',
'}',
'internal class P126RealLetterPlayer(private val context:Context) {',
'private var media:MediaPlayer?=null',
'fun stop() { media?.let { runCatching { it.stop() };runCatching { it.release() } };media=null }',
'fun play(sample:P126Sound,onStatus:(String)->Unit) {',
'stop()',
'val player=MediaPlayer.create(context,sample.resId) ?: error("تعذر فتح الملف الصوتي")',
'try { media=player;player.setOnCompletionListener { if(media===player){ media=null;runCatching { player.release() } } }',
'player.start();onStatus("استماع مباشر: "+sample.label)',
'} catch(ex:Exception) { if(media===player)media=null;runCatching { player.release() };throw ex }',
'}',
'}'])
out=root/"app/src/main/java/com/ab/khatma/ui/screens/P126HumanAlphabetAudio.kt"
assert not out.exists()
out.write_text("\n".join(lines)+"\n",encoding="utf-8")
audit=root/"BUILD_REPRODUCIBILITY/p126"
audit.mkdir(parents=True,exist_ok=False)
(audit/"HUMAN_AUDIO_ATTRIBUTION.md").write_text(
"# P126 licensed human audio\n\nArtist: Ruaa GHAREEB, Wikimedia Commons, CC BY-SA 4.0. Original audio unmodified.\n"+
"https://commons.wikimedia.org/wiki/User:Ruaa_GHAREEB\n"+
"https://creativecommons.org/licenses/by-sa/4.0/\n"+
"Pronunciation-to-word source map: https://en.wikiversity.org/wiki/Learn_Arabic_(Language_Of_Quran)/Alphabets\n"+
"These clips are letter names and COMPLETE spoken words; they are not individually voiced isolated vowel marks or certified Tajweed recitations.\n"+
"Clip source / stored path / SHA256:\n"+
"\n".join(" * "+a+" | "+b+" | "+c for a,b,c in receipts)+"\n",encoding="utf-8")
print("P126 real human audio integrated:",len(items),"letters and",len(receipts),"verified WAV samples.")
