package com.ab.khatma.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

private data class P41Focus(val scale: Float, val centerX: Float, val centerY: Float)
private data class P41PagePayload(val pageNumber: Int, val image: ImageBitmap, val decorated: Boolean, val focus: P41Focus, val frames: List<P85TitleFrame>)

/** VC96: read-only, pixel-measured 15-line presentation. No text is generated or retyped.
 * The original immutable bitmap is sampled line-by-line; special pages fall back to
 * the untouched full-page renderer. Verse hitmaps remain on their original renderer.
 */
private data class P76MarginNote(val side: Int, val x0: Int, val y0: Int, val x1: Int, val y1: Int)
private data class P76PageLines(val x0: Int, val x1: Int, val rows: List<Pair<Int,Int>>, val notes: List<P76MarginNote>)

private object P76ReadingGeometry {
    private var cache: org.json.JSONObject? = null
    @Synchronized fun forPage(context: android.content.Context, page: Int): P76PageLines? {
        val root = cache ?: runCatching {
            org.json.JSONObject(context.assets.open("p76_verse_line_layout_604.json")
                .bufferedReader().use { it.readText() })
        }.getOrNull()?.also { cache = it } ?: return null
        val obj = root.optJSONObject(page.toString()) ?: return null
        val x = obj.optJSONArray("x") ?: return null
        val lines = obj.optJSONArray("lines") ?: return null
        if (lines.length() != 15) return null
        val rows = (0 until 15).mapNotNull { i ->
            val y = lines.optJSONArray(i) ?: return@mapNotNull null
            val lo = y.optInt(0)
            val hi = y.optInt(1)
            if (lo >= hi || lo < 0 || hi > 2038) null else lo to hi
        }
        if (rows.size != 15) return null
        val notesJson = obj.optJSONArray("notes")
        val notes = (0 until (notesJson?.length() ?: 0)).mapNotNull { i ->
            val n = notesJson?.optJSONArray(i) ?: return@mapNotNull null
            P76MarginNote(n.optInt(0),n.optInt(1),n.optInt(2),n.optInt(3),n.optInt(4))
                .takeIf { it.x0 < it.x1 && it.y0 < it.y1 }
        }
        return P76PageLines(x.optInt(0),x.optInt(1),rows,notes)
            .takeIf { it.x0 < it.x1 && it.x0 >= 0 && it.x1 <= 1260 }
    }
}

/** VC97: inverses VC96's measured line draw geometry for the original 604 hitmaps.
 * This does not infer verses from visual text; it only transforms taps into the
 * original image coordinates consumed by P45VerseHitMap. Blank gaps are not taps.
 */
private fun p97ReadingHitPoint(
    position: Offset, viewWidth: Int, viewHeight: Int,
    imageWidth: Int, imageHeight: Int, lines: P76PageLines?,
): Offset? {
    if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
    val w = viewWidth.toFloat()
    val h = viewHeight.toFloat()
    if (lines == null) {
        // Unmodified full-image page; coordinates use ContentScale.Fit, not a guess.
        val fit = min(w / imageWidth, h / imageHeight)
        val dw = imageWidth * fit
        val dh = imageHeight * fit
        val nx = (position.x - (w - dw) / 2f) / dw
        val ny = (position.y - (h - dh) / 2f) / dh
        return Offset(nx, ny).takeIf { nx in 0f..1f && ny in 0f..1f }
    }
    if (viewHeight < 360 || viewWidth < 260) return null
    val headerSrcH = (imageHeight * .108f).roundToInt().coerceAtLeast(1)
    val stripScale = w / imageWidth.toFloat()
    val headerDstH = (headerSrcH * stripScale).roundToInt()
    val headerY = (h * .032f).roundToInt()
    val footerSrcY = (imageHeight * .93f).roundToInt()
    val footerDstH = max(1, ((imageHeight - footerSrcY) * stripScale).roundToInt())
    val footerY = viewHeight - footerDstH - (h * .019f).roundToInt()
    val textTop = max(headerY + headerDstH + 12, (h * .110f).roundToInt())
    val textBottom = min(footerY - 12, (h * .915f).roundToInt())
    if (textBottom <= textTop || lines.x1 <= lines.x0) return null
    val pitch = (textBottom - textTop).toFloat() / 15f
    val tallest = lines.rows.maxOf { it.second - it.first }.toFloat().coerceAtLeast(1f)
    val normalScale = min((w - 24f) / (lines.x1 - lines.x0).toFloat(), pitch * .96f / tallest)
    for ((index, band) in lines.rows.withIndex()) {
        val y0 = band.first.coerceIn(0, imageHeight - 1)
        val y1 = band.second.coerceIn(y0 + 1, imageHeight)
        val centerSrc = (y0 + y1) / 2f
        val note = lines.notes.firstOrNull { centerSrc >= it.y0 - 22 && centerSrc <= it.y1 + 22 }
        val noteSpace = note?.let { ((it.x1 - it.x0) * normalScale).roundToInt() + 13 } ?: 0
        val rowScale = (if (note != null) min(normalScale, (w - noteSpace - 24f) /
            (lines.x1 - lines.x0).toFloat()) else normalScale).coerceAtLeast(.1f)
        val rowWidth = max(1, ((lines.x1 - lines.x0) * rowScale).roundToInt())
        val rowHeight = max(1, ((y1 - y0) * rowScale).roundToInt())
        val startX = when (note?.side) {
            0 -> viewWidth - rowWidth - 12
            1 -> 12
            else -> (viewWidth - rowWidth) / 2
        }
        val centerY = textTop + (index + .5f) * pitch
        val startY = (centerY - rowHeight / 2f).roundToInt()
        if (position.x >= startX && position.x < startX + rowWidth &&
            position.y >= startY && position.y < startY + rowHeight) {
            val origX = lines.x0 + (position.x - startX) * (lines.x1 - lines.x0) / rowWidth
            val origY = y0 + (position.y - startY) * (y1 - y0) / rowHeight
            return Offset(origX / imageWidth, origY / imageHeight)
        }
    }
    return null
}

@Composable
private fun P76ReadingOnlyPage(
    page: ImageBitmap,
    pageNumber: Int,
    frames: List<P85TitleFrame>,
    lines: P76PageLines?,
    paperFilter: ColorFilter?,
    selectedHighlight: ImageBitmap?,
    onSelectVerseAt: (Offset) -> Unit,
) {
    if (lines == null) {
        // Special pages: preserve original ContentScale.Fit and map the same accurate
        // (half-resolution) highlight to exactly the same screen rectangle.
        Box(Modifier.fillMaxSize().pointerInput(pageNumber, page.width, page.height) {
            detectTapGestures(onTap = { position ->
                p97ReadingHitPoint(position, size.width, size.height,
                    page.width, page.height, null)?.let(onSelectVerseAt)
            })
        }) {
            Image(bitmap = page, contentDescription = "صفحة المصحف $pageNumber",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                colorFilter = paperFilter)
            P85OriginalTitleFrames(frames, page.width, page.height, Modifier.fillMaxSize())
            selectedHighlight?.let { mask ->
                Image(bitmap = mask, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        return
    }
    Canvas(Modifier.fillMaxSize().pointerInput(pageNumber, lines, page.width, page.height) {
        detectTapGestures(onTap = { position ->
            p97ReadingHitPoint(position, size.width, size.height,
                page.width, page.height, lines)?.let(onSelectVerseAt)
        })
    }) {
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        if (page.width <= 0 || page.height <= 0 || h < 360 || w < 260) return@Canvas
        val headerSrcH = (page.height * .108f).roundToInt().coerceAtLeast(1)
        val stripScale = w.toFloat() / page.width.toFloat()
        val headerDstH = (headerSrcH * stripScale).roundToInt()
        val headerY = (h * .032f).roundToInt()
        drawImage(
            image = page, srcOffset = IntOffset.Zero, srcSize = IntSize(page.width, headerSrcH),
            dstOffset = IntOffset(0, headerY), dstSize = IntSize(w, headerDstH),
            colorFilter = paperFilter,
        )
        val footerSrcY = (page.height * .93f).roundToInt()
        val footerHeight = page.height - footerSrcY
        val footerDstH = max(1, (footerHeight * stripScale).roundToInt())
        val footerY = h - footerDstH - (h * .019f).roundToInt()
        drawImage(
            image = page, srcOffset = IntOffset(0, footerSrcY),
            srcSize = IntSize(page.width, footerHeight),
            dstOffset = IntOffset(0, footerY), dstSize = IntSize(w, footerDstH),
            colorFilter = paperFilter,
        )
        val textTop = max(headerY + headerDstH + 12, (h * .110f).roundToInt())
        val textBottom = min(footerY - 12, (h * .915f).roundToInt())
        if (textBottom <= textTop || lines.x1 <= lines.x0) return@Canvas
        val pitch = (textBottom - textTop).toFloat() / 15f
        val maxSourceBand = lines.rows.maxOf { it.second - it.first }.toFloat().coerceAtLeast(1f)
        val normalScale = min(
            (w - 24f) / (lines.x1 - lines.x0).toFloat(),
            pitch * .96f / maxSourceBand,
        )
        for ((i, band) in lines.rows.withIndex()) {
            val y0 = band.first.coerceIn(0,page.height-1)
            val y1 = band.second.coerceIn(y0+1,page.height)
            val centerSrc = (y0 + y1)/2f
            val activeNote = lines.notes.firstOrNull { n -> centerSrc >= n.y0 - 22 && centerSrc <= n.y1 + 22 }
            val noteSpace = activeNote?.let { n ->
                ((n.x1 - n.x0) * normalScale).roundToInt() + 13
            } ?: 0
            val sideScale = if (activeNote != null) min(normalScale,
                (w - noteSpace - 24f) / (lines.x1 - lines.x0).toFloat()) else normalScale
            val rowScale = sideScale.coerceAtLeast(.1f)
            val rowW = max(1, ((lines.x1 - lines.x0)*rowScale).roundToInt())
            val rowH = max(1, ((y1 - y0)*rowScale).roundToInt())
            val startX = when (activeNote?.side) {
                0 -> w - rowW - 12
                1 -> 12
                else -> (w-rowW)/2
            }
            val centerY = textTop + (i + .5f)*pitch
            val rowDestination = IntOffset(startX, (centerY-rowH/2f).roundToInt())
            val rowSize = IntSize(rowW,rowH)
            drawImage(
                image = page,
                srcOffset = IntOffset(lines.x0, y0),
                srcSize = IntSize(lines.x1 - lines.x0, y1 - y0),
                dstOffset = rowDestination,
                dstSize = rowSize,
                colorFilter = paperFilter,
            )
            p85MappedFrames(frames,
                androidx.compose.ui.geometry.Rect(lines.x0.toFloat(), y0.toFloat(), lines.x1.toFloat(), y1.toFloat()),
                androidx.compose.ui.geometry.Rect(rowDestination.x.toFloat(), rowDestination.y.toFloat(),
                    (rowDestination.x + rowW).toFloat(), (rowDestination.y + rowH).toFloat()))
            // VC101: render the ORIGINAL accurate verse highlight through the exact
            // same 15-line mapping as the Quran image. Never paint a guessed row.
            selectedHighlight?.let { mask ->
                val mx0 = (lines.x0.toLong() * mask.width / page.width).toInt().coerceIn(0,mask.width - 1)
                val mx1 = (lines.x1.toLong() * mask.width / page.width).toInt().coerceIn(mx0 + 1,mask.width)
                val my0 = (y0.toLong() * mask.height / page.height).toInt().coerceIn(0,mask.height - 1)
                val my1 = (y1.toLong() * mask.height / page.height).toInt().coerceIn(my0 + 1,mask.height)
                drawImage(
                    image = mask,
                    srcOffset = IntOffset(mx0,my0),
                    srcSize = IntSize(mx1 - mx0,my1 - my0),
                    dstOffset = rowDestination,
                    dstSize = rowSize,
                )
            }
        }
        // Keep the source's 'ربع الجزء' / similar outer-margin indications.
        for (note in lines.notes) {
            val noteIndex = lines.rows.indices.minByOrNull { i ->
                kotlin.math.abs((lines.rows[i].first + lines.rows[i].second) - (note.y0 + note.y1))
            } ?: continue
            val sourceW = note.x1 - note.x0
            val sourceH = note.y1 - note.y0
            if (sourceW < 1 || sourceH < 1) continue
            val nScale = min(normalScale, (w * .13f) / sourceW)
            val nw = max(1, (sourceW*nScale).roundToInt())
            val nh = max(1, (sourceH*nScale).roundToInt())
            val nx = if (note.side == 0) 5 else w-nw-5
            val ny = (textTop+(noteIndex+.5f)*pitch - nh/2f).roundToInt()
            drawImage(
                image = page,
                srcOffset = IntOffset(note.x0,note.y0),
                srcSize = IntSize(sourceW,sourceH),
                dstOffset = IntOffset(nx,ny),
                dstSize = IntSize(nw,nh),
                colorFilter = paperFilter,
            )
        }
    }
}

/** Measured once offline from the 604 original images; decoding a page never scans pixels on the UI thread. */
private object P41Geometry {
    @Volatile private var cache: org.json.JSONObject? = null
    @Synchronized fun forPage(context: android.content.Context, page: Int, decorated: Boolean): P41Focus {
        val root = cache ?: runCatching {
            org.json.JSONObject(context.assets.open("p71_mushaf_focus_geometry.json")
                .bufferedReader().use { it.readText() })
        }.getOrNull()?.also { cache = it }
        val entry = root?.optJSONObject(if (decorated) "colored" else "plain")
            ?.optJSONArray(page.toString())
        return P41Focus(
            entry?.optDouble(0, 1.0)?.toFloat()?.coerceIn(1f, 1.32f) ?: 1f,
            entry?.optDouble(1, 0.5)?.toFloat()?.coerceIn(0f, 1f) ?: 0.5f,
            entry?.optDouble(2, 0.5)?.toFloat()?.coerceIn(0f, 1f) ?: 0.5f,
        )
    }
}

/**
 * P41: displays authentic 604-page pre-generated Madani Mushaf artwork bundled
 * with the APK, without requests to Firebase, Railway or any other server.
 * Never rasterize/transcribe Quran text from our Uthmani JSON to imitate a printed page.
 * Decode only the visible page (+ the pager's nearest neighbors) off the UI thread.
 */
@Composable
internal fun P41OfflineMushafPage(
    pageNumber: Int,
    colored: Boolean,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onToggleControls: () -> Unit,
    inkTool: P44InkTool = P44InkTool.MOVE,
    onPlayVerse: (Int, Int) -> Unit = { _, _ -> },
    playingKey: String? = null,
    readingMode: Boolean = false,
) {
    val context = LocalContext.current
    val loadedPage by produceState<P41PagePayload?>(initialValue = null, pageNumber, colored) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
                val source = if (colored) "quran_pages_colored" else "quran_pages"
                val name = "%03d.webp".format(java.util.Locale.ROOT, pageNumber)
                val downloaded = if (!colored) P55PlainPages.pageFile(context, name) else null
                val plainFile = downloaded?.takeIf { it.isFile && it.length() > 0L }
                val bitmap = plainFile?.let { f ->
                    runCatching { f.inputStream().use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
                } ?: runCatching {
                    context.assets.open("$source/$name").use { BitmapFactory.decodeStream(it, null, opts) }
                }.getOrNull() ?: context.assets.open("quran_pages_colored/$name").use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                bitmap?.let { decoded ->
                    val decorated = colored || plainFile == null
                    P41PagePayload(pageNumber, decoded.asImageBitmap(), decorated,
                        P41Geometry.forPage(context.applicationContext, pageNumber, decorated),
                        if (decorated) P85TitleFrames.page(context.applicationContext, pageNumber) else emptyList())
                }
            }.getOrNull()
        }
    }
    // Publish artwork and its measured decoration atomically. produceState keeps its
    // previous value while a new key loads, so never label/decorate a stale page.
    val visiblePayload=loadedPage?.takeIf { it.pageNumber==pageNumber }
    val titleFrames=visiblePayload?.frames.orEmpty()
    val page=visiblePayload?.image
    val decorated=visiblePayload?.decorated==true
    val readingLines = remember(pageNumber, decorated, readingMode) {
        if (readingMode && decorated) P76ReadingGeometry.forPage(context.applicationContext, pageNumber) else null
    }
    val focus = visiblePayload?.focus ?: P41Focus(1f, 0.5f, 0.5f)
    var x by remember(pageNumber) { mutableFloatStateOf(0f) }
    var zoomHintVisible by remember(pageNumber) { mutableStateOf(false) }
    LaunchedEffect(zoom) {
        if (zoom > 1.01f) { zoomHintVisible = true; kotlinx.coroutines.delay(2200); zoomHintVisible = false }
        else zoomHintVisible = false
    }
    var y by remember(pageNumber) { mutableFloatStateOf(0f) }
    var viewport by remember(pageNumber) { mutableStateOf(IntSize.Zero) }
    // Keep the whole printed page reachable by ordinary one-finger paging on short screens.
    val fittedAutomatic = remember(page, viewport, focus) {
        if (page != null && viewport.width > 0 && viewport.height > 0) {
            val fit = min(viewport.width.toFloat() / page.width, viewport.height.toFloat() / page.height)
            val maximum = viewport.height.toFloat() / (page.height * fit).coerceAtLeast(1f)
            min(focus.scale, maximum).coerceAtLeast(1f)
        } else 1f
    }
    val actualScale = zoom * fittedAutomatic
    val hitMap by produceState<P45VerseHitMap?>(initialValue = null, pageNumber, decorated) {
        value = if (decorated) P45VerseHitMap.load(context,pageNumber) else null
    }
    var selectedVerse by remember(pageNumber) { mutableStateOf<Pair<Int,Int>?>(null) }
    var hint by remember(pageNumber) { mutableStateOf<String?>(null) }
    // P58: the verse being recited is highlighted, so the eye can follow the reciter.
    val playingVerse: Pair<Int, Int>? = remember(playingKey) {
        val parts = playingKey?.split(":")
        val s = parts?.getOrNull(0)?.toIntOrNull()
        val a = parts?.getOrNull(1)?.toIntOrNull()
        if (parts != null && parts.size == 2 && s != null && a != null) s to a else null
    }
    val selectedImage by produceState<ImageBitmap?>(initialValue = null, hitMap, selectedVerse, playingVerse) {
        value = withContext(Dispatchers.Default) { hitMap?.highlight(playingVerse ?: selectedVerse) }
    }
    fun selectAt(norm: androidx.compose.ui.geometry.Offset) {
        if (!decorated) {
            selectedVerse = null
            hint = "للتحديد المباشر اختر «ملون»؛ خريطة الآيات تطابق هذه الطبعة فقط."
            return
        }
        val verse = hitMap?.find(norm.x,norm.y)
        if (verse != null) {
            // P57: touching a verse plays it right away; touching it again (or another one) is handled by the player.
            selectedVerse = verse
            hint = null
            onPlayVerse(verse.first, verse.second)
        } else {
            selectedVerse = null
            hint = if (hitMap == null) "جارٍ تحميل خريطة الآيات…" else "اضغط على كلمات الآية داخل الصفحة."
        }
    }

    // Actual scaled FIT bounds. Fixed pixel bounds previously allowed large blank
    // margins and stale offsets when the minus button reduced magnification.
    fun limits(scale: Float): Pair<Float, Float> {
        val art = page ?: return 0f to 0f
        if (viewport.width <= 0 || viewport.height <= 0) return 0f to 0f
        val fitting = min(viewport.width.toFloat() / art.width, viewport.height.toFloat() / art.height)
        val artW = art.width * fitting * scale
        val artH = art.height * fitting * scale
        return ((artW - viewport.width) / 2f).coerceAtLeast(0f) to
            ((artH - viewport.height) / 2f).coerceAtLeast(0f)
    }
    fun focusOffsets(scale: Float): Pair<Float, Float> {
        val art = page ?: return 0f to 0f
        if (viewport.width <= 0 || viewport.height <= 0) return 0f to 0f
        val fitting = min(viewport.width.toFloat() / art.width, viewport.height.toFloat() / art.height)
        val dx = (0.5f - focus.centerX) * art.width * fitting * scale
        val dy = (0.5f - focus.centerY) * art.height * fitting * scale
        return dx to dy
    }
    LaunchedEffect(actualScale, pageNumber, viewport, page) {
        val (maxX, maxY) = limits(actualScale)
        x = x.coerceIn(-maxX, maxX)
        y = y.coerceIn(-maxY, maxY)
        if (actualScale <= 1.01f) { x = 0f; y = 0f }
    }
    val transformState = rememberTransformableState { factor, pan, _ ->
        val next = (zoom * factor).coerceIn(1f, 3.5f)
        if (next != zoom) onZoomChange(next)
        if (next * fittedAutomatic > 1.01f) {
            val (maxX, maxY) = limits(next * fittedAutomatic)
            x = (x + pan.x).coerceIn(-maxX, maxX)
            y = (y + pan.y).coerceIn(-maxY, maxY)
        } else { x = 0f; y = 0f }
    }
    P67Look.load(androidx.compose.ui.platform.LocalContext.current)
    Box(
        Modifier.fillMaxSize()
            .background(if (P53NightMode.enabled) P53NightMode.pageBackground else P67Look.pageBackground)
            .onSizeChanged { viewport = it }
            .clipToBounds()
            // Automatic page-fit should never steal a one-finger horizontal swipe from HorizontalPager.
            // Panning is reserved for deliberate manual zoom; dedicated prev/next always work.
            .transformable(state = transformState, enabled = !readingMode && inkTool == P44InkTool.MOVE,
                canPan = { !readingMode && zoom > 1.04f }),
        contentAlignment = Alignment.Center,
    ) {
        if (page != null) {
            if (readingMode) {
                P76ReadingOnlyPage(
                    page = page,
                    pageNumber = pageNumber,
                    frames = if (decorated) titleFrames else emptyList(),
                    lines = readingLines,
                    paperFilter = if (P53NightMode.enabled) P53NightMode.pageFilter
                        else if (P67Look.white) P67Look.pageFilter else null,
                    onSelectVerseAt = ::selectAt,
                    selectedHighlight = selectedImage,
                )
            } else {
            Image(
                bitmap = page,
                contentDescription = "مصحف المدينة المنورة، صفحة $pageNumber",
                colorFilter = if (P53NightMode.enabled) P53NightMode.pageFilter else if (P67Look.white) P67Look.pageFilter else null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(
                    scaleX = actualScale,
                    scaleY = actualScale,
                    translationX = focusOffsets(actualScale).first + x,
                    translationY = focusOffsets(actualScale).second + y,
                ),
            )
            if (decorated) P85OriginalTitleFrames(titleFrames, page.width, page.height,
                Modifier.fillMaxSize().graphicsLayer(
                    scaleX=actualScale, scaleY=actualScale,
                    translationX=focusOffsets(actualScale).first+x,
                    translationY=focusOffsets(actualScale).second+y))
            // The decorated edition places headings close to the paper edges. The
            // lines can be magnified without cropping these headings by repainting
            // ONLY original, unmodified header/footer pixels at their natural size.
            // Original verse bitmap and the synchronized hitmap remain untouched.
            // A separate, per-page ink canvas shares the exact page zoom/pan transform.
            // Switching original/colored never modifies the immutable Quran artwork.
            if (!readingMode) P44InkOverlay(
                pageNumber = pageNumber,
                tool = inkTool,
                bitmapWidth = page.width,
                bitmapHeight = page.height,
                zoom = actualScale,
                panX = focusOffsets(actualScale).first + x,
                panY = focusOffsets(actualScale).second + y,
                onSelectVerseAt = ::selectAt,
                selectedHighlight = selectedImage,
                onDoubleTap = {
                    // Zoomed in: back to normal. Normal size: toggle full screen.
                    if (zoom > 1.01f) { onZoomChange(1f); x = 0f; y = 0f } else onToggleControls()
                },
                modifier = Modifier.fillMaxSize(),
            )
            }
        } else {
            CircularProgressIndicator(color = Color(0xFF907032))
        }
        if (!readingMode && hint != null && inkTool == P44InkTool.MOVE) {
            Text(hint.orEmpty(),Modifier.align(Alignment.BottomCenter).background(Color(0xEFFFFAEC)).padding(8.dp),
                color=Color(0xFF705026),fontSize=12.sp,textAlign=TextAlign.Center)
        }
        if (!readingMode && page != null && zoomHintVisible && zoom > 1.01f && inkTool == P44InkTool.MOVE && selectedVerse == null && hint == null) {
            Text("${"%.1f".format(java.util.Locale.ROOT, zoom)}×  •  حرّك الصفحة بإصبعك • اضغط مرتين للإعادة",
                Modifier.align(Alignment.BottomCenter).background(Color(0xDDFFFAEC)).padding(6.dp),
                color = Color(0xFF473A20), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}
