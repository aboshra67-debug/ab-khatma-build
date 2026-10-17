#!/usr/bin/env python3
"""VC104 regression guards: notification names/colloquial text/lock-screen privacy,
plus immutable Quran, FCM, adhan controls and existing reminder schedules.
"""
import sys
from pathlib import Path
root = Path(sys.argv[1])
java = root / "app/src/main/java/com/ab/khatma"
def read(name): return (java / name).read_text(encoding="utf8")
b = (root/"app/build.gradle.kts").read_text(encoding="utf8")
assert "versionCode = 104" in b
assert "com.google.firebase:firebase-messaging" in b
copy = read("notifications/P80NoticeCopy.kt")
assert "SecureSessionStore(context).load()" in copy
assert "userName" in copy and "SessionStore(context)" in copy
assert "يا $name، $message" in copy
assert "P80NoticeCopy" not in read("ui/screens/QuranScreen.kt")
assert "وردك لسه محتاج تأكيد النهارده" in copy
assert "لم يتم تسجيل القراءة حتى الآن." in copy
assert "النهارده الجمعة" in copy and "سورة الكهف" in copy
for part in ["prayer/AdhanAlarmReceiver.kt","prayer/PrayerReminderWorker.kt",
             "reminders/P53Reminders.kt","secure/SecureReminderWorker.kt",
             "reminders/ReadingReminderWorker.kt","secure/P79Push.kt"]:
    s = read(part)
    assert "P80NoticeCopy" in s, part
    assert "VISIBILITY_PRIVATE" in s, part
    assert "setPublicVersion(" in s, part
alarm = read("prayer/AdhanAlarmReceiver.kt")
assert "AdhanPlaybackService.ACTION_STOP" in alarm
assert "AdhanAlarmScheduler.schedule(context)" in alarm
assert "notificationAllowed" in alarm
assert "P80NoticeCopy.prayer(context, prayer)" in alarm
ward = read("reminders/P53Reminders.kt")
assert "SecureV2Api.today(session.token)" in ward
assert 'if (remaining != null && remaining.isEmpty()) return Result.success()' in ward
assert 'data class P53ReminderSettings' in ward and "P53ReminderScheduler" in ward
assert "P53DailyDua.forToday(ctx)" in ward
secure = read("secure/SecureReminderWorker.kt")
assert "SecureV2Api.ack(session.token, reminder)" in secure
assert "SecureV2Api.due(session.token)" in secure
assert "Periodical" not in secure
push=read("secure/P79Push.kt")
assert "intendedUser != loggedIn.userId" in push
assert "p79_group" in push and "p79_sender" in push
assert "FirebaseMessagingService" in push
assert "private_chat" in push
assert 'setContentText("رسالة خاصة جديدة")' in push  # generic lock-screen version
q=read("ui/screens/QuranScreen.kt")
page=read("ui/screens/P41OfflineMushafPage.kt")
assert 'Text("الخيارات"' in q and "private fun P101ReadingAudioDock(" in q
assert "selectedHighlight = selectedImage," in page
assert "onSelectVerseAt = ::selectAt" in page
assets=root / "app/src/main/assets"
assert len(list((assets/"quran_pages_colored").glob("*.webp"))) == 604
assert len(list((assets/"quran_verse_hitmaps").glob("*.json"))) == 604
print("P80 VC104 static regression: PASS (personal Egyptian copy, local names, private locks, prayer/ward/chat, FCM, Quran)")
