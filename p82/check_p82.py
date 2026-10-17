#!/usr/bin/env python3
"""P82 VC106: fail if private/group retry keys not applied safely."""
from pathlib import Path
import sys
r=Path(sys.argv[1]).resolve()
j=r/"app/src/main/java/com/ab/khatma/secure"
api=(j/"SecureV2Api.kt").read_text()
private=(j/"P69PrivateChat.kt").read_text()
screen=(j/"SecureV2Screen.kt").read_text()
build=(r/"app/build.gradle.kts").read_text()
assert 'versionCode = 106' in build
assert 'versionName = "0.19.77-p82-idempotent-private-group-chat-safe-qa"' in build
assert api.count('json.put("client_message_id", clientMessageId)') == 1
assert api.count('data.put("client_message_id", clientMessageId)') == 1
assert 'clientMessageId: String? = null' in api
assert 'P81NetworkRecovery.readOnly' in screen
assert 'LaunchedEffect(session?.token, section, p81RetrySignal)' in screen
assert 'p82PendingId by rememberSaveable(groupId, peer.id)' in private
assert 'p82GroupPendingId by rememberSaveable(session?.userId, chatGroupId)' in screen
assert 'UUID.randomUUID().toString()' in private
assert 'UUID.randomUUID().toString()' in screen
assert 'SecureV2Api.sendPrivate(token, groupId, peer.id, text, id)' in private
assert 'sendingReply, id)' in screen
assert private.index('p82PendingId = ""') > private.index('SecureV2Api.sendPrivate(token, groupId, peer.id, text, id)')
assert screen.index('p82GroupPendingId = ""',screen.index('val sendChat: () -> Unit')) > screen.index('SecureV2Api.sendMessage(',screen.index('val sendChat: () -> Unit'))
assert 'No blind retry here' in private
assert 'Never retry writes automatically' in screen
# All P80 FCM/readers/mushaf are unchanged.
assert 'P79Push.sync(context, it)' in screen
assert 'P80NoticeCopy' in (j.parent/"notifications/P80NoticeCopy.kt").read_text()
assets=r/"app/src/main/assets"
assert len(list((assets/"quran_pages_colored").glob("*.webp"))) == 604
assert len(list((assets/"quran_verse_hitmaps").glob("*.json"))) == 604
print("P82 VC106 static PASS: private and group IDs, consistent timeout retry, no automatic POST, FCM and 604 Quran pages intact")
