"""P113 follow-up: wire Home buttons through QuranScreen's real onHome route."""
import sys
from pathlib import Path
root=Path(sys.argv[1]); ui=root/'app/src/main/java/com/ab/khatma/ui/screens'
quiz=ui/'P107QuizScreen.kt';quran=ui/'QuranScreen.kt'
assert quiz.is_file() and quran.is_file()
s=quiz.read_text('utf-8');q=quran.read_text('utf-8')
def one(text,old,new,label):
    assert text.count(old)==1,(label,text.count(old))
    return text.replace(old,new,1)
s=one(s,'internal fun P107QuizScreen(repo: QuranRepository, onClose: () -> Unit) {',
      'internal fun P107QuizScreen(repo: QuranRepository, onClose: () -> Unit, onHome: () -> Unit = onClose) {','quiz signature')
s=one(s,'P108IndividualQuiz(repo = repo, onClose = { view = "home" },onHome=onClose)',
      'P108IndividualQuiz(repo = repo, onClose = { view = "home" },onHome=onHome)','individual route')
s=one(s,'onClose = { view = "home" },onHome=onClose)',
      'onClose = { view = "home" },onHome=onHome)','group route')
q=one(q,'P107QuizScreen(repo = repo, onClose = { p107QuizVisible = false })',
      'P107QuizScreen(repo = repo, onClose = { p107QuizVisible = false },\n                onHome = { p107QuizVisible = false; onHome() })','Quran home route')
quiz.write_text(s,'utf-8');quran.write_text(q,'utf-8')
print('P113 Home routing PASS; QuranScreen app home callback preserved, exam unchanged')
