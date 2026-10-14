package com.ab.khatma.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** P55: full-screen Mushaf reading. Only the frame around the page is hidden — page swipe,
 * verse touch, pen/highlight/eraser and zoom keep working exactly as before. */
object P55Reader {
    private const val PREFS = "p55_reader"
    var full by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        full = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("full", false)
    }

    fun set(ctx: Context, value: Boolean) {
        full = value
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("full", value).apply()
    }
}

private fun Context.p55Activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Hides the status and navigation bars while [enabled]; a swipe from the edge shows them briefly. */
@Composable
fun P55ImmersiveEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.p55Activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            if (enabled) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
fun P55FullscreenButton(accent: Color, onEnter: () -> Unit) {
    val context = LocalContext.current
    TextButton(
        onClick = { P55Reader.set(context, true); onEnter() },
        colors = ButtonDefaults.textButtonColors(contentColor = accent),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Icon(Icons.Rounded.Fullscreen, null)
        Text("ملء الشاشة", fontSize = 12.sp)
    }
}

@Composable
fun P55ExitFullscreenFab(onExit: () -> Unit) {
    val context = LocalContext.current
    SmallFloatingActionButton(
        onClick = { P55Reader.set(context, false); onExit() },
        containerColor = Color(0xFF064D3B),
        contentColor = Color.White,
        modifier = Modifier.alpha(0.55f),
    ) { Icon(Icons.Rounded.FullscreenExit, "الخروج من ملء الشاشة") }
}
