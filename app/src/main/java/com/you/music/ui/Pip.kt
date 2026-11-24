package com.you.music.ui

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.media3.session.MediaController

private tailrec fun Context.pipActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.pipActivity()
    else -> null
}

/**
 * enabled = true ho (video mode me chal raha ho) aur user Home dabaye,
 * to video floating window (PiP) me chalta rahta hai.
 */
@Composable
fun PipEffect(
    enabled: Boolean,
    controller: MediaController?,
    onChanged: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.pipActivity() }
    val enabledNow by rememberUpdatedState(enabled)
    val ctrlNow by rememberUpdatedState(controller)
    val changed by rememberUpdatedState(onChanged)

    DisposableEffect(act) {
        if (act == null) {
            return@DisposableEffect onDispose { }
        }
        val leave = Runnable {
            if (enabledNow && Build.VERSION.SDK_INT >= 26) {
                try {
                    var w = 16
                    var h = 9
                    val vs = ctrlNow?.videoSize
                    if (vs != null && vs.width > 0 && vs.height > 0) {
                        w = vs.width
                        h = vs.height
                    }
                    val r = (w.toFloat() / h).coerceIn(0.42f, 2.39f)
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational((r * 100).toInt(), 100))
                        .build()
                    act.enterPictureInPictureMode(params)
                } catch (e: Throwable) {
                    // PiP na chale to bhi app crash nahi hoga
                }
            }
        }
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            changed(info.isInPictureInPictureMode)
        }
        act.addOnUserLeaveHintListener(leave)
        act.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            act.removeOnUserLeaveHintListener(leave)
            act.removeOnPictureInPictureModeChangedListener(listener)
        }
    }
}
