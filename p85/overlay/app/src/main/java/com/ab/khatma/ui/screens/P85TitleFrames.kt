package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import org.json.JSONArray
import kotlin.math.min

internal data class P85TitleFrame(val title: Rect, val frame: Rect)
internal object P85TitleFrames {
    @Volatile private var cache: Map<Int, List<P85TitleFrame>>? = null
    @Synchronized fun page(context: Context, page: Int): List<P85TitleFrame> {
        val all = cache ?: runCatching {
            val json = JSONArray(context.assets.open("p85_surah_title_frames.json").bufferedReader().use { it.readText() })
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                fun rect(name: String): Rect { val a = o.getJSONArray(name); return Rect(a.getDouble(0).toFloat(),a.getDouble(1).toFloat(),a.getDouble(2).toFloat(),a.getDouble(3).toFloat()) }
                o.getInt("page") to P85TitleFrame(rect("title"),rect("frame"))
            }.groupBy({ it.first }, { it.second })
        }.getOrDefault(emptyMap()).also { cache = it }
        return all[page].orEmpty()
    }
}

/** Stroke-only ornament in measured empty margins. It never paints/retypes Quran text. */
private fun DrawScope.p85Ornament(item: P85TitleFrame) {
    val r = item.frame
    val t = item.title
    val gold = if (P53NightMode.enabled) Color(0xFFC6AC72) else Color(0xFFAA8B55)
    val fine = Stroke(1.7f)
    drawRoundRect(gold, r.topLeft, r.size, CornerRadius(5f), style = fine)
    drawRoundRect(gold.copy(alpha=.7f), Offset(r.left+3,r.top+3), Size(r.width-6,r.height-6), CornerRadius(4f), style=Stroke(.9f))
    val left = t.left - 25f
    val right = t.right + 25f
    val panel = Path().apply {
        moveTo(left-16,r.top); lineTo(left,r.top+12); lineTo(left,r.bottom-12); lineTo(left-16,r.bottom)
        moveTo(right+16,r.top); lineTo(right,r.top+12); lineTo(right,r.bottom-12); lineTo(right+16,r.bottom)
    }
    drawPath(panel,gold,style=fine)
    for ((lo,hi) in listOf(r.left+13 to left-23, right+23 to r.right-13)) {
        if (hi-lo<75f) continue
        val center=Offset((lo+hi)/2f,r.center.y)
        val radius=min((hi-lo)*.18f,r.height*.29f)
        for (n in 0 until 8) withTransform({ rotate(n*45f,center) }) {
            drawOval(gold.copy(alpha=.8f),Offset(center.x-radius*.28f,center.y-radius*1.32f),Size(radius*.56f,radius*1.35f),style=Stroke(1.1f))
        }
        drawCircle(gold,radius*.2f,center,style=Stroke(1.4f))
        for (sign in listOf(-1f,1f)) {
            val start=center.x+sign*radius*1.5f
            val edge=if(sign<0)lo else hi
            val wave=Path().apply {
                moveTo(start,center.y)
                cubicTo(start+sign*20, r.top+8,edge-sign*15,r.top+8,edge,center.y)
                cubicTo(edge-sign*15,r.bottom-8,start+sign*20,r.bottom-8,start,center.y)
            }
            drawPath(wave,gold.copy(alpha=.75f),style=Stroke(1.1f))
        }
    }
}

internal fun DrawScope.p85MappedFrames(items: List<P85TitleFrame>, source: Rect, destination: Rect) {
    items.filter { it.title.center.y in source.top..source.bottom }.forEach { item ->
        // Preserve the existing row geometry; only the ornament width follows its visible margins.
        val frame=Rect(maxOf(item.frame.left,source.left+4),item.frame.top,minOf(item.frame.right,source.right-4),item.frame.bottom)
        if (frame.left >= item.title.left-28 || frame.right <= item.title.right+28) return@forEach
        withTransform({
            translate(destination.left,destination.top)
            scale(destination.width/source.width,destination.height/source.height,Offset.Zero)
            translate(-source.left,-source.top)
        }) { p85Ornament(item.copy(frame=frame)) }
    }
}

@Composable
internal fun P85OriginalTitleFrames(items: List<P85TitleFrame>, width: Int, height: Int, modifier: Modifier) {
    if (items.isEmpty()) return
    Canvas(modifier) {
        val fit=min(size.width/width,size.height/height)
        withTransform({ translate((size.width-width*fit)/2f,(size.height-height*fit)/2f); scale(fit,fit,Offset.Zero) }) {
            items.forEach { p85Ornament(it) }
        }
    }
}
