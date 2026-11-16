#!/usr/bin/env python3
import json,urllib.parse,urllib.request
api='https://commons.wikimedia.org/w/api.php'
q={'action':'query','list':'categorymembers','cmtitle':'Category:Arabic pronunciation','cmnamespace':6,'cmlimit':500,'format':'json'}
titles=[]; j=0
while True:
 u=api+'?'+urllib.parse.urlencode(q)
 req=urllib.request.Request(u,headers={'User-Agent':'AB-Khatma-Learning/1.0 (educational-app; contact:absoftware.eg@gmail.com)'})
 with urllib.request.urlopen(req,timeout=45) as res:
  data=json.load(res)
 titles.extend(x['title'] for x in data['query']['categorymembers'])
 if 'continue' not in data:break
 q.update({'cmcontinue':data['continue']['cmcontinue']})
 j+=1
 if j>20:break
from collections import defaultdict
for t in titles:
 base=t.replace('File:','')
 low=base.lower()
 if (base.endswith('.wav') and (('audio' in low) or ('pron' in low) or ('pp' in low)) and len(base)<27):
  print(base)
print('TOTAL',len(titles))
