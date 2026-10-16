package com.ab.khatma.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/** P67: the Mushaf page can be shown on a snow-white background. The page artwork is never changed:
 * only a display colour filter turns the cream paper colour (255, 249, 233) into white while it is drawn. */
internal object P67Look {
    private const val PREFS = "p67_look"
    var white by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        white = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("white_page", true)
        }.getOrDefault(true)
    }

    fun toggle(context: Context) {
        load(context)
        white = !white
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("white_page", white).apply()
        }
    }

    val pageBackground: Color get() = if (white) Color.White else Color(0xFFFFF9E9)

    /** cream -> white, black ink stays black. */
    val pageFilter: ColorFilter = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 255f / 249f, 0f, 0f, 0f,
                0f, 0f, 255f / 233f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}
