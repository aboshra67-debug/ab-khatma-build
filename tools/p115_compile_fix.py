"""P115 safe compile fix: preserve mandatory onOpenIndex callback in FlowReader."""
from pathlib import Path
import sys
p=Path(sys.argv[1])/'app/src/main/java/com/ab/khatma/ui/screens/QuranScreen.kt'
assert p.is_file()
s=p.read_text('utf8')
a='''                    onSearch = { showSearch = true },
                    onPageViewed = if(currentTarget.key.startsWith("learning_page_"))'''
b='''                    onSearch = { showSearch = true },
                    onOpenIndex = {
                        if (currentTarget.key.startsWith("learning_page_")) p115ReturnToLearning=true
                        target = null
                    },
                    onPageViewed = if(currentTarget.key.startsWith("learning_page_"))'''
assert s.count(a)==1,('P115 mandatory route',s.count(a))
s=s.replace(a,b,1)
assert s.count('onPageViewed = if(currentTarget.key.startsWith("learning_page_"))')==1
p.write_text(s,'utf8')
print('P115 FlowReader mandatory index callback restored PASS')
