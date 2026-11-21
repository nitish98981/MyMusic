package com.you.music.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import com.you.music.data.PlaybackMode

tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

/**
 * App background / screen lock se wapas aane par agar pehle video mode tha
 * to video mode wapas laata hai.
 */
@Composable
fun RestoreVideoOnReturn(controller: MediaController?) {
    val ctx = LocalContext.current
    DisposableEffect(ctx, controller) {
        val owner = ctx.findLifecycleOwner()
        var wasVideo = false
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> wasVideo = PlaybackMode.video.value
                Lifecycle.Event.ON_START -> {
                    if (wasVideo && !PlaybackMode.video.value) {
                        PlaybackMode.set(controller, true)
                    }
                }
                else -> {}
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
}

/** Video dikhata hai, sahi aspect ratio ke saath (kheenchta nahi) */
@Composable
fun VideoSurface(controller: MediaController?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var aspect by remember { mutableStateOf(16f / 9f) }
    var gen by remember { mutableIntStateOf(0) }
    val tvRef = remember { arrayOfNulls<TextureView>(1) }

    // App wapas khulne par naya TextureView banao, warna video black rehta hai
    DisposableEffect(ctx) {
        val owner = ctx.findLifecycleOwner()
        var ready = false
        val obs = LifecycleEventObserver { _, e ->
            if (ready && e == Lifecycle.Event.ON_START) gen++
        }
        owner?.lifecycle?.addObserver(obs)
        ready = true
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    DisposableEffect(controller) {
        if (controller == null) {
            return@DisposableEffect onDispose { }
        }
        val update: (VideoSize) -> Unit = { size ->
            if (size.width > 0 && size.height > 0) {
                aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        update(controller.videoSize)
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                update(videoSize)
            }
        }
        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
            val tv = tvRef[0]
            if (tv != null) controller.clearVideoTextureView(tv)
        }
    }

    LaunchedEffect(controller) {
        val tv = tvRef[0]
        if (tv != null) controller?.setVideoTextureView(tv)
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        key(gen) {
            AndroidView(
                factory = { c ->
                    TextureView(c).also { tv ->
                        tvRef[0] = tv
                        controller?.setVideoTextureView(tv)
                    }
                },
                modifier = Modifier.aspectRatio(aspect)
            )
        }
    }
}

/** Watch screen se back karne par neeche ye chhota video window rehta hai. Double tap = poora video */
@Composable
fun MiniVideoBar(
    state: UiPlayerState,
    controller: MediaController?,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val toggle: () -> Unit = {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 128.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            VideoSurface(controller, Modifier.fillMaxSize())
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(controller) {
                        detectTapGestures(
                            onTap = { toggle() },
                            onDoubleTap = { onOpen() }
                        )
                    }
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onOpen() })
                }
        ) {
            Text(
                state.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                state.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = toggle) {
            if (state.isPlaying) {
                GlyphPause(Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface)
            } else {
                GlyphPlay(Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface)
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Close")
        }
    }
}
