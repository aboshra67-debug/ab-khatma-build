package com.ab.khatma.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/** P86: Decorates only the empty bottom margin of the existing 604-page artwork.
 * Never edits the original bitmap or the verse-hit coordinates. Odd = right, even = left.
 */
internal object P86FooterRules {
    fun onRight(page: Int): Boolean = page in 1..604 && page % 2 == 1
    fun arabicNumber(page: Int): String = page.toString().map { c ->
        if (c in '0'..'9') ('٠'.code + c.code - '0'.code).toChar() else c
    }.joinToString("")
}

/** The caller supplies the SAME page color filter as the underlying image. */
internal fun DrawScope.p86OriginalFooter(page: ImageBitmap, pageNumber: Int, paperFilter: ColorFilter?) {
    if (pageNumber !in 1..604 || page.width < 600 || page.height < 900) return
    val w = page.width.toFloat(); val h = page.height.toFloat()
    // Sample a blank area from this exact immutable page to erase ONLY its center folio.
    // Page text and all numbered ayah medallions lie far above this bottom margin.
    val dstX = (w * .417f).roundToInt()
    val dstY = (h * .934f).roundToInt()
    val rectW = (w * .166f).roundToInt()
    val rectH = (h * .050f).roundToInt()
    drawImage(page, srcOffset = IntOffset((w * .06f).roundToInt(), dstY),
        srcSize = IntSize(rectW, rectH), dstOffset = IntOffset(dstX, dstY),
        dstSize = IntSize(rectW, rectH), colorFilter = paperFilter)

    val cx = w * (if (P86FooterRules.onRight(pageNumber)) .884f else .116f)
    val cy = h * .958f
    val halfW = w * .052f
    val halfH = h * .022f
    val gold = if (P53NightMode.enabled) Color(0xFFE0C789) else Color(0xFF9D7D49)
    val dark = if (P53NightMode.enabled) 0xFFE4D1AD.toInt() else 0xFF272015.toInt()
    drawRoundRect(gold, Offset(cx-halfW,cy-halfH), Size(halfW*2,halfH*2),
        androidx.compose.ui.geometry.CornerRadius(halfH*.45f), style=Stroke(2.4f))
    drawRoundRect(gold.copy(alpha=.85f), Offset(cx-halfW+6,cy-halfH+6), Size(halfW*2-12,halfH*2-12),
        androidx.compose.ui.geometry.CornerRadius(halfH*.4f), style=Stroke(1.15f))
    for (side in listOf(-1f,1f)) {
        val x=cx+side*(halfW+13f)
        drawLine(gold,Offset(x-10f,cy),Offset(x+10f,cy),strokeWidth=2f)
        drawLine(gold,Offset(x,cy-10f),Offset(x,cy+10f),strokeWidth=2f)
        drawCircle(gold,4.2f,Offset(x,cy))
    }
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=dark
        textAlign=Paint.Align.CENTER
        textSize=(h*.021f).coerceAtLeast(24f)
        typeface=Typeface.create("serif",Typeface.BOLD)
    }
    drawContext.canvas.nativeCanvas.drawText(P86FooterRules.arabicNumber(pageNumber),cx,cy+paint.textSize*.36f,paint)
}

/** Map the source-space ornament with the image's existing ContentScale.Fit. */
@Composable
internal fun P86PageFooter(page: ImageBitmap, pageNumber: Int, paperFilter: ColorFilter?, modifier: Modifier) {
    Canvas(modifier) {
        val fit=min(size.width/page.width,size.height/page.height)
        withTransform({
            translate((size.width-page.width*fit)/2f,(size.height-page.height*fit)/2f)
            scale(fit,fit,Offset.Zero)
        }) { p86OriginalFooter(page,pageNumber,paperFilter) }
    }
}
