package com.ab.khatma.prayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class P85AdhanVoice(val id:String,val name:String,val number:Int,val bytes:Long,val sha:String,val seconds:Int) {
    val url get()="https://quran-fjamfcbbeybteyat.z01.azurefd.net/Audio3/Adan/$number.mp3"
}
/** Explicit on-demand listening sources; never a silent replacement of an existing voice. */
object P85NamedAdhan {
    val voices=listOf(
        P85AdhanVoice("abdulbasit","عبد الباسط عبد الصمد",1026,403584,"73bffee51e6f2d24acd52dd5eb9a1e74d3b3cd3ef6bee2db3f9aec62e6da2fef",201),
        P85AdhanVoice("banna","محمود علي البنا",1029,332888,"66f89f1852b33974fb33ae9640c9aa4768d409d1dec57f8ecd82edc320fa8d23",166),
        P85AdhanVoice("minshawi","محمد صديق المنشاوي",4032,503264,"95b48f013fa9f611922cc9edabc576734fc7e0cf0fce0ccf86c53960b975233a",252),
    )
    private val lock=Any()
    fun file(context:Context,id:String):File? {
        val voice=voices.firstOrNull { it.id==id }?:return null
        return File(context.filesDir,"p85_adhan/${voice.id}.mp3").takeIf { it.isFile&&it.length()==voice.bytes }
    }
    private fun sha(file:File):String {
        val d=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b=ByteArray(32768);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)} }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
    suspend fun prepare(context:Context,id:String):File=withContext(Dispatchers.IO) {
        synchronized(lock) {
            val voice=voices.first { it.id==id }
            file(context,id)?.takeIf { sha(it)==voice.sha }?.let { return@synchronized it }
            val dir=File(context.filesDir,"p85_adhan").apply { check(isDirectory||mkdirs()) }
            val part=File(dir,"${voice.id}.part")
            try {
                part.outputStream().use { output ->
                    var written=0L
                    while(written<voice.bytes) {
                        val c=URL(voice.url).openConnection() as HttpURLConnection
                        try {
                            c.connectTimeout=15000;c.readTimeout=20000;c.instanceFollowRedirects=false
                            c.setRequestProperty("User-Agent","Mozilla/5.0")
                            c.setRequestProperty("Referer","https://audio.islamweb.net/")
                            c.setRequestProperty("Range","bytes=$written-${minOf(voice.bytes-1,written+262143)}")
                            val code=c.responseCode
                            check(code==206||(code==200&&written==0L))
                            check(c.contentType?.startsWith("audio/")==true)
                            if(code==206) {
                                val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(c.getHeaderField("Content-Range").orEmpty())?:error("range")
                                check(range.groupValues[1].toLong()==written&&range.groupValues[3].toLong()==voice.bytes)
                            }
                            val before=written
                            c.inputStream.use { input ->
                                val b=ByteArray(32768)
                                while(true) { val n=input.read(b);if(n<0)break;check(written+n<=voice.bytes);output.write(b,0,n);written+=n }
                            }
                            check(written>before)
                        } finally { c.disconnect() }
                    }
                }
                check(part.length()==voice.bytes&&sha(part)==voice.sha)
                val final=File(dir,"${voice.id}.mp3")
                check(part.renameTo(final))
                final
            } finally { part.delete() }
        }
    }
}
