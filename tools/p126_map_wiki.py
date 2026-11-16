#!/usr/bin/env python3
import urllib.request,urllib.parse,json,re
p="Learn Arabic (Language Of Quran)/Alphabets"
api="https://en.wikiversity.org/w/api.php?"+urllib.parse.urlencode({"action":"parse","page":p,"prop":"wikitext","format":"json"})
req=urllib.request.Request(api,headers={"User-Agent":"AB-Khatma-Education/1.0 (contact: absoftware.eg@gmail.com)"})
with urllib.request.urlopen(req,timeout=45) as res: data=json.load(res)
raw=data["parse"]["wikitext"]["*"]
header=""
for line in raw.splitlines():
 if re.match(r"^={2,6}",line):header=line.strip()[:80]
 if ".wav" in line.lower() or ".ogg" in line.lower():
  print(header+" :: "+line.strip()[:250])
