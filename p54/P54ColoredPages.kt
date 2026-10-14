package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** P54: the coloured Mushaf pages (≈112 MB) are no longer inside the APK, so the app fits
 * Google Play's size limit. They are downloaded once, on demand, verified by SHA-256 and
 * kept in private app storage. The plain Madani pages stay bundled and work offline. */
object P54ColoredPages {
    const val DIR = "quran_pages_colored"
    private const val MARKER = ".complete"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    var ready by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var askVisible by mutableStateOf(false)
    var sizeMb by mutableStateOf(112)
        private set

    fun dir(ctx: Context) = File(ctx.filesDir, DIR)

    fun init(ctx: Context) {
        ready = File(dir(ctx), MARKER).exists()
        runCatching { config(ctx) }.getOrNull()?.let { sizeMb = (it.optLong("bytes") / 1_048_576L).toInt().coerceAtLeast(1) }
    }

    /** The page file to draw, or null to fall back to the bundled assets. */
    fun pageFile(ctx: Context, name: String): File? =
        if (ready) File(dir(ctx), name).takeIf { it.isFile } else null

    private fun config(ctx: Context): JSONObject =
        JSONObject(ctx.assets.open("p54_colored_pages.json").bufferedReader().use { it.readText() })

    fun start(ctx: Context) {
        if (downloading || ready) return
        val app = ctx.applicationContext
        downloading = true
        error = null
        progress = 0f
        job = scope.launch {
            val result = runCatching { download(app) }
            withContext(Dispatchers.Main) {
                downloading = false
                result.onSuccess { ready = true }
                    .onFailure { error = "تعذّر التحميل. تأكد من الإنترنت ثم حاول تاني — هيكمّل من مكان ما وقف." }
            }
        }
    }

    private fun download(ctx: Context) {
        val cfg = config(ctx)
        val url = cfg.getString("url")
        val sha = cfg.getString("sha256").lowercase()
        val total = cfg.optLong("bytes", 0L)
        val part = File(ctx.cacheDir, "p54_colored.zip.part")
        // Resume an interrupted download.
        var have = if (part.exists()) part.length() else 0L
        if (total > 0 && have > total) { part.delete(); have = 0L }
        if (total == 0L || have < total) {
            var link = url
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = URL(link).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = false
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                if (code in 300..399 && hops < 5) {
                    link = conn.getHeaderField("Location") ?: throw IllegalStateException("redirect")
                    require(link.startsWith("https://")) { "insecure redirect" }
                    conn.disconnect(); hops++; continue
                }
                if (code == 200 && have > 0) { have = 0L; part.delete() } // server ignored Range
                require(code == 200 || code == 206) { "HTTP $code" }
                break
            }
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, have > 0).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = have
                    var lastUi = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastUi > 512 * 1024) {
                            lastUi = done
                            val p = done.toFloat() / total
                            scope.launch(Dispatchers.Main) { progress = p.coerceIn(0f, 0.99f) }
                        }
                    }
                }
            }
            conn.disconnect()
        }
        // Verify before anything is used.
        val md = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { s ->
            val b = ByteArray(64 * 1024)
            while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) }
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (got != sha) { part.delete(); throw IllegalStateException("checksum mismatch") }

        val target = dir(ctx)
        val tmp = File(ctx.filesDir, "$DIR.tmp").apply { deleteRecursively(); mkdirs() }
        var pages = 0
        ZipInputStream(part.inputStream().buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                if (e.isDirectory || name.isBlank() || name.contains("..")) continue
                File(tmp, name).outputStream().use { zip.copyTo(it) }
                if (name.endsWith(".webp")) pages++
            }
        }
        require(pages >= 604) { "incomplete archive" }
        target.deleteRecursively()
        require(tmp.renameTo(target)) { "rename failed" }
        File(target, MARKER).writeText(sha)
        part.delete()
    }
}

/** Download prompt shown when «ملون» is chosen before the coloured pages are on the phone. */
@Composable
fun P54ColoredGate(onReady: () -> Unit) {
    val context = LocalContext.current
    if (!P54ColoredPages.askVisible) return
    if (P54ColoredPages.ready) {
        androidx.compose.runtime.LaunchedEffect(Unit) { P54ColoredPages.askVisible = false; onReady() }
        return
    }
    AlertDialog(
        onDismissRequest = { if (!P54ColoredPages.downloading) P54ColoredPages.askVisible = false },
        title = { Text("المصحف الملوّن") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الصفحات الملونة بتتحمّل مرة واحدة بس (حوالي ${P54ColoredPages.sizeMb} ميجا) وبعدها تشتغل من غير نت. يُفضّل واي فاي.")
                if (P54ColoredPages.downloading) {
                    LinearProgressIndicator(progress = { P54ColoredPages.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(P54ColoredPages.progress * 100).toInt()}% — تقدر تكمّل قراءة عادي، وهتتفعّل لوحدها لما تخلص.")
                }
                P54ColoredPages.error?.let { Text(it, color = Color(0xFFB3261E)) }
            }
        },
        confirmButton = {
            if (!P54ColoredPages.downloading) TextButton(onClick = { P54ColoredPages.start(context) }) {
                Text(if (P54ColoredPages.error != null) "إعادة المحاولة" else "تحميل")
            }
        },
        dismissButton = {
            TextButton(onClick = { P54ColoredPages.askVisible = false }) {
                Text(if (P54ColoredPages.downloading) "إخفاء" else "لاحقًا")
            }
        },
    )
}
