from pathlib import Path

root = Path(__file__).resolve().parents[1]
def edit(path, old, new):
    file = root / path
    data = file.read_text(encoding="utf-8")
    if data.count(old) != 1:
        raise RuntimeError(f"Unsafe P92 patch: expected exactly one match in {path}, found {data.count(old)}")
    file.write_text(data.replace(old, new, 1), encoding="utf-8")

media = "app/src/main/java/com/ab/khatma/media/P91OfflineAudio.kt"
edit(media, "    private const val RESERVE_BYTES = 24L * 1024 * 1024\n",
    "    private const val RESERVE_BYTES = 24L * 1024 * 1024\n    private const val MAX_REDIRECTS = 4\n")
edit(media, "    fun saved(context: Context, url: String): File? {", """    /** Verify redirect targets without accepting unrelated hosts or nonstandard ports. */
    internal fun trustedRedirect(source: URL, target: URL): Boolean {
        val origin = source.host.lowercase()
        val host = target.host.lowercase()
        val sameHost = host == origin
        val archiveMirror = origin == "archive.org" && (host == "archive.org" ||
            (host.endsWith(".archive.org") && host.length > ".archive.org".length))
        return target.protocol == "https" && target.port == -1 && target.userInfo == null &&
            (sameHost || archiveMirror)
    }

    fun saved(context: Context, url: String): File? {""")

path = root / media
source = path.read_text(encoding="utf-8")
start_marker = "                    // Only trusted source URLs can initiate the request; external redirects are rejected."
end_marker = "                    check(connection.responseCode == 200)"
if source.count(start_marker) != 1 or source.count(end_marker) != 1:
    raise RuntimeError("Unexpected P91 HTTP downloader: stop, do not overwrite")
start = source.index(start_marker)
end = source.index(end_marker, start)
new = """                    // Follow a bounded chain only within the original trusted origin. Archive
                    // distributes files across HTTPS *.archive.org media nodes, not just dn*.
                    var nextUrl = URL(url)
                    var redirects = 0
                    while (true) {
                        connection = (nextUrl.openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = false
                            connectTimeout = 12000
                            readTimeout = 18000
                            setRequestProperty("User-Agent", "ABKhatma/92")
                            setRequestProperty("Accept", "audio/mpeg, audio/*, application/octet-stream")
                        }
                        val code = connection.responseCode
                        if (code !in 300..399) break
                        check(redirects++ < MAX_REDIRECTS) { "تجاوز المصدر عدد التحويلات المسموح" }
                        val target = connection.getHeaderField("Location") ?: error("مصدر الصوت غير متاح")
                        val resolved = URL(nextUrl, target)
                        check(trustedRedirect(URL(url), resolved)) { "إعادة توجيه غير آمنة" }
                        connection.disconnect()
                        connection = null
                        nextUrl = resolved
                    }
"""
path.write_text(source[:start] + new + source[end:], encoding="utf-8")
edit(media, '                    check(space > RESERVE_BYTES + (if (size > 0) size else MAX_BYTES)) { "مساحة الموبايل غير كافية" }', '''                    check(space > RESERVE_BYTES + (if (size > 0) size else 1L * 1024 * 1024)) {
                        "مساحة الموبايل غير كافية"
                    }''')
edit(media, '                                check(written <= MAX_BYTES) { "تجاوز حجم التسجيل الآمن" }',
'''                                check(written <= MAX_BYTES) { "تجاوز حجم التسجيل الآمن" }
                                if (size <= 0 && written % (2 * 1024 * 1024) < n) {
                                    check(StatFs(folder.absolutePath).availableBytes > RESERVE_BYTES + n) {
                                        "مساحة الموبايل غير كافية"
                                    }
                                }''')

edit("app/src/main/java/com/ab/khatma/ui/screens/P85IbtehalScreen.kt",
    'Text("$count متاح الآن" + (if(archived>0) " • $archived بالأرشيف" else ""),fontSize=10.sp,color=P87Gold)',
    'Text((if(count>0) "$count متاح للاستماع" else "أرشيف فقط — أضف تسجيلك") + (if(archived>0 && count>0) " • $archived بالأرشيف" else ""),fontSize=10.sp,color=P87Gold)')
edit("app/src/main/java/com/ab/khatma/guide/P85Guide.kt",
    'GuideStep("", "استمع بلا إنترنت", "بعد اكتمال التحميل تظهر علامة ✓ محفوظ على الموبايل وتقدر تسمعه في أي وقت بدون شبكة."),',
    'GuideStep("", "استمع بلا إنترنت", "بعد اكتمال التحميل تظهر علامة ✓ محفوظ على الموبايل وتقدر تسمعه في أي وقت بدون شبكة. عناوين الأرشيف من غير صوت مباشر تحتاج ملفًا محليًا تختاره أنت."),')
edit("app/build.gradle.kts", "versionCode = 115", "versionCode = 116")
edit("app/build.gradle.kts", "0.19.86-p91-offline-guide-safe", "0.19.87-p92-audio-redirect-safe")

(root / "P92_SAFE_PATCH_NOTES_AR.txt").write_text("""AB Khatma P92 / VC116 — safe changes based strictly on P91 VC115

- Keep exact package, existing Firebase/backend settings and all original audio catalogs.
- Retain permanent per-file sandbox offline storage in filesDir.
- Fix HTTPS archive.org media CDN redirects by permitting vetted archive.org hosts only, max four redirects.
- Keep redirects to other providers, non-HTTPS URLs, credentials and custom ports rejected.
- Adjust disk-space preflight for chunked recordings with unknown Content-Length, while checking reserved space during downloads.
- Label non-playable archive-only ibtehal catalogs accurately; keep local owned recordings.
- Clarify offline guide limitations.
- Version 116; no deletions to functioning code, data or audio catalog.
- Signed APK and live Internet playback verification must be separately confirmed.
- Added native emulator disconnected replay + redirect guard tests, executed by GitHub workflow.
""", encoding="utf-8")

test = root / "app/src/androidTest/java/com/ab/khatma/guide/P92OfflineAfterDisconnectDeviceTest.kt"
if test.exists():
    raise RuntimeError("P92 device test unexpectedly exists in baseline")
test.write_text("""package com.ab.khatma.guide

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ab.khatma.media.P91OfflineAudio
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

/** Run after P91 online integration test with Wi-Fi and mobile data disabled by CI. */
@RunWith(AndroidJUnit4::class)
class P92OfflineAfterDisconnectDeviceTest {
    private val url = "https://cdn.mp3quran.net/audio/muhammad-refaat/r1/001.mp3"

    @Test fun savedRecitationStartsWithoutNetworkAndSurvivesRepeatedAccess() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val stored = P91OfflineAudio.saved(ctx, url)
        assertNotNull("The P91 online integration test must download this exact track first", stored)
        val same = runBlocking { withTimeout(1_500L) { P91OfflineAudio.acquire(ctx, url) } }
        assertEquals(stored!!.absolutePath, same.absolutePath)
        assertTrue(same.isFile && same.length() > 100_000L)
        val meta = MediaMetadataRetriever()
        try {
            meta.setDataSource(same.absolutePath)
            assertTrue((meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 10_000L)
        } finally { meta.release() }
    }

    @Test fun secureArchiveMirrorRedirectsAndRejectsOtherHosts() {
        val origin = URL("https://archive.org/download/item/track.mp3")
        assertTrue(P91OfflineAudio.trustedRedirect(origin, URL("https://ia800500.us.archive.org/download/item/track.mp3")))
        assertTrue(P91OfflineAudio.trustedRedirect(origin, URL("https://dn720001.ca.archive.org/download/item/track.mp3")))
        assertFalse(P91OfflineAudio.trustedRedirect(origin, URL("http://ia800500.us.archive.org/download/item/track.mp3")))
        assertFalse(P91OfflineAudio.trustedRedirect(origin, URL("https://ia800500.us.archive.org.evil.test/music.mp3")))
        assertFalse(P91OfflineAudio.trustedRedirect(origin, URL("https://cdn.mp3quran.net/track.mp3")))
        assertFalse(P91OfflineAudio.trustedRedirect(origin, URL("https://archive.org:8443/download/item/track.mp3")))
        assertFalse(P91OfflineAudio.trustedRedirect(origin, URL("https://user:pass@archive.org/download/item/track.mp3")))
    }
}
""", encoding="utf-8")
print("P92 safe changes applied; no media catalogs, account state, assets, or backend modified")


live = root / "app/src/androidTest/java/com/ab/khatma/guide/P92LiveAudioDeviceTest.kt"
if live.exists():
    raise RuntimeError("P92 live test unexpectedly exists in P91 baseline")
live.write_text((Path(__file__).parent / "p92_live_audio_test.kt").read_text(encoding="utf-8"), encoding="utf-8")

offline_path = "app/src/androidTest/java/com/ab/khatma/guide/P92OfflineAfterDisconnectDeviceTest.kt"
edit(offline_path,
    '    private val url = "https://cdn.mp3quran.net/audio/muhammad-refaat/r1/001.mp3"',
    '    private val url = "https://cdn.mp3quran.net/audio/muhammad-refaat/r1/001.mp3"\\n    private val archiveUrl = "https://archive.org/download/07._20210119/03.%D8%AD%D9%8A%D9%86%20%D9%8A%D9%87%D8%AF%D9%8A%20%D8%A7%D9%84%D8%B5%D8%A8%D8%AD%20%D8%A7%D8%B4%D8%B1%D8%A7%D9%82%20%D8%B3%D9%86%D8%A7%D9%87.mp3"'.replace('\\n', '\n'))
edit(offline_path,
    "    @Test fun secureArchiveMirrorRedirectsAndRejectsOtherHosts() {",
    """    @Test fun archiveIbtehalAndAdhanRemainAvailableOffline() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ibtehal = P91OfflineAudio.saved(ctx, archiveUrl)
        assertNotNull("Online archive test must save ibtehal before disconnect", ibtehal)
        assertEquals(ibtehal!!.absolutePath, runBlocking {
            withTimeout(1_500L) { P91OfflineAudio.acquire(ctx, archiveUrl) }
        }.absolutePath)
        val adhan = com.ab.khatma.prayer.P85NamedAdhan.file(ctx, "refaat_radio")
        assertNotNull("Online adhan test must save adhan before disconnect", adhan)
    }

    @Test fun secureArchiveMirrorRedirectsAndRejectsOtherHosts() {""")

with (root / "P92_SAFE_PATCH_NOTES_AR.txt").open("a", encoding="utf-8") as file:
    file.write("- Live archive.org ibtehal and IslamWeb Refaat adhan download/decode gates; offline cached replay checks.\\n")
