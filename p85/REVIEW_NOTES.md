# AB Khatma P85 / VC109 review build

Base: exact P84 commit b7a5f663caee46343098b2132fe790a9d22290c0 and immutable full source artifact from run 39141590156. APK only; no AAB or Play publication.

The owner's requested scope is a one-time guide after successful sign-in, manual guide access, ornamental surah-title frames, improved full adhan playback, additional named adhan choices, and an Ibtehal section. Existing authenticated installations are marked as already introduced; the guide state is local and per account on this phone. Reinstalling/clearing data can show it again. The guide does not perform account/group writes.

Original session/auth APIs, Quran recitation player, all original Quran images/text/hitmaps, existing audio files, scheduling preferences and package identity are preserved byte for byte. The small alarm-intent addition supplies the original scheduled epoch for duplicate playback protection. Media playback changes are isolated to the adhan service.

Additional adhan recordings are fetched on explicit selection from their public Islamweb source pages, verified by exact size and SHA-256, and retained in private app storage for offline playback. Previous voice selection is changed only after verification and if the user has not changed/stopped/dismissed that selection. Full downloadable recordings are not bundled in the APK/source. Public availability is not a verified redistribution/publication license; recording permissions must be settled before public release.

Verified source pages on 2026-10-18:
- Abdul Basit: https://audio.islamweb.net/audio/index.php?page=audioinfo&audioid=122664 (200.836 seconds)
- Mahmoud Ali Al Banna: https://audio.islamweb.net/audio/index.php?page=audioinfo&audioid=122666 (166.024 seconds)
- Muhammad Siddiq Al Minshawi: https://audio.islamweb.net/audio/index.php?page=audioinfo&audioid=88639 (251.632 seconds)
- EBTEHALAT broadcaster: https://zeno.fm/radio/EBTEHALAT1/ ; current public live stream returned audio/mpeg and decoded as MP3. Broadcaster may change content, availability, and advertising. User-initiated playback is screen-scoped; external government archive links open the source.

All114 source-title regions were visually inspected. Frame strokes add no replacement Quran text or altered artwork. Their source coordinates are transformed through the existing normal-page or15-band reading mappings; verse hitmaps and page layout arithmetic are unchanged.

Checks: platform-free guide/playback decisions; retained session-race unit tests; native Android guide navigation/once-only checks, original reader/frame screenshots, and a full downloaded251.632s Minshawi playback test including duplicate scheduled start and screen-off operation. CI result artifacts govern actual pass/fail; this file is not a claim that a run has passed. Physical OEM phones remain untested here.

The server is unchanged. Original local-only signing is required with package com.ab.khatma.preview and certificate SHA-256 3248c1ebdd2973a992a453919a4207317cdecfc4cf0c7e8dc28dd7d9a5468b41. No original signing material is in this source.
