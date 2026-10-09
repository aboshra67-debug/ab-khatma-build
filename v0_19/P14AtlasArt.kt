package com.ab.khatma.secure

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** P14 artwork atlas: 90px tiles, 4 columns, 2 rows, extracted from approved UI. */
@Composable
fun KhatmaArt(index: Int, modifier: Modifier = Modifier) {
    val bitmap = ImageBitmap.imageResource(com.ab.khatma.R.drawable.p14_art_atlas)
    val painter = remember(bitmap, index) {
        BitmapPainter(bitmap, IntOffset((index % 4)*90, (index / 4)*90), IntSize(90,90))
    }
    Image(painter = painter, contentDescription = null, modifier = modifier,
        contentScale = ContentScale.Crop)
}
