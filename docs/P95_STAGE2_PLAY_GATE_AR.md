# AB Khatma — Stage 2 pre-Play release gate (P95 / VC119)
Date: 2026-10-19
Scope: READ-ONLY verification; P94 and P95 runtime binaries preserved.

## Actual baseline
- Android branch: release/p95-vc119-preplay-hardening-safe.
- Android build run: 39270578361, SUCCESS; 19 Android emulator instrumentation tests passed.
- Server branch: release/preplay-p95-production-gates-safe.
- Server run: 39271135223, workflow overall FAILED **intentionally** because legacy-production-blocker failed. Isolated PostgreSQL 16 and PostgreSQL 18 tests (93 each) plus staging check succeeded.
- Staging: Railway project AB Khatma V2 Staging, khatma-v2-api, pinned release/vc107-report-review-safe, PostgreSQL 18 online.
- Production: Railway project truthful-clarity, legacy balanced-creativity from main (2026-08-29), and an additional offline AB-Khatma-Server service.
- Client build in P95 workflow uses the legacy production V1 URL AND a staging V2 URL. A release candidate MUST NOT have live public users bound to an experimental environment or unprotected V1.
- Never promote any new server over existing database without actual recoverable, encrypted logical backup and a verified restore plus rollback planning. Presence of a Railway Postgres volume is not a verified backup.

## Code review and Play policy gates
| Gate | Current evidence | Required next action |
| --- | --- | --- |
| Server security | P95 anonymous production checks block release; staging passes | Design, test, and verify production V1 shutdown and V2 upgrade in isolation |
| Production data safety | No verified restore evidence; no production data was accessed | Perform pg_dump + restore drill under operator-approved private credentials, encrypted off-host storage |
| Account erasure | P54 has in-app authenticated account deletion and a web form accepting password-based accounts only; Google web clients are instructed to return to the app | Provide working, authenticated, standalone web deletion request for Google-login users, without requiring installation; avoid insecure unauthenticated deletion |
| UI | 19 emulator cases passed; these do not exercise every possible button, screen, screen size, or error state | Inspect stage2 audit findings individually; then add targeted regression tests for confirmed defects |
| Audio | Online download and airplane-mode replay tested; playback to end, source licenses and all reciters not fully proven | Verify complete adhan playback, error recovery, all reader choices, track license provenance, and consent to redistribute |
| Quran | 604 colored, 604 plain, 604 hit-map files checked in P95 workflow | Verify screen visuals, mapping accuracy, verse integrity on phones; never modify canonical Mushaf assets without authoritative proof |
| Android target | Check actual app/build.gradle.kts target SDK before uploading | Google Play: API 36+ as of 2026-08-31 |
| Release package | Unsigned review APK available in P95 successful workflow; no signed AAB release approved | Build AAB from verified source, sign only with protected original key in trusted environment, inspect merged manifest, test Play-delivered APK |
| Data safety | Real network use for auth, chats, FCM, caching | Complete Google Play Data Safety declaration and hosted privacy policy based on actual code and data retention |

## Every-screen acceptance matrix (NOT all passed)
1. Startup/splash after clean install, relaunch, background->foreground, first-run interactive guide, help from every supported screen.
2. Google sign-in and password sign-in; expired access token renew; unreachable backend, offline errors and recovery without killing app.
3. Home, navigation drawer/bottom nav, account, settings, privacy, consent, account deletion including adverse network behavior.
4. Quran page 1 / 187 / 604, zoom, landscape if allowed, font/contrast, narration control dock, highlighting and verse hit-map; Arabic harakat not clipped.
5. Page turning, reader selection, full audio duration, resumed downloads and airplane-mode replay, cache validation and eviction.
6. Prayers location permissions; selected cities, time-zone/DST, exact alarm/notification permissions, audible adhan **to the final second**.
7. Adhkar categories, per-item mark complete, restore progress, scroll position and accessibility.
8. Ibtehal categories/player controls, adjacent screen nav, play inline without surprising navigation, repeat/cache error.
9. Khatmas create/join, leader approval, page and juz assignments, group member list, real-time/private chat, attachments if present, report/block/rate limits; privacy of non-members.
10. Notifications while app foregrounded/backgrounded/terminated, account-signed-out and blocked-member behavior.
11. Arabic copy, truncation, icons on compact phones, text scaling 200%, contrast, RTL placement, Back/Home labels.

## Rule for phase-two fixes
- No code modifications are authorized by a flagged string alone; inspect reachable UI and demonstrate failing behavior first.
- Safe Patch only; avoid changing MainActivity, database schema, original Quran page files, signature, package ID or Firebase project unintentionally.
- Increment versionCode only for a separately build-verified new artifact, not for a documentation-only audit.
- An automated static source audit is NOT proof of hands-on review or audio ownership.
- This document and associated reports do NOT authorize deployment to live Railway, releasing to Google Play, or wiping/resetting any device.

## Authoritative policy references
- Google Play Target API requirement: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en
- Account deletion (web as well as in-app): https://support.google.com/googleplay/android-developer/answer/13327111?hl=en
- Android App Bundle for new Google Play apps: https://support.google.com/googleplay/android-developer/answer/9844279?hl=en
