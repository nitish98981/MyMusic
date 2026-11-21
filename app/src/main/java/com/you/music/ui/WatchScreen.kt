package com.you.music.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.you.music.data.Channel
import com.you.music.data.Follows
import com.you.music.data.Likes
import com.you.music.data.MusicRepository
import com.you.music.data.Pending
import com.you.music.data.PlaybackMode
import com.you.music.data.Song
import com.you.music.data.VideoQuality
import com.you.music.data.channelOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@Composable
fun WatchScreen(
    state: UiPlayerState,
    controller: MediaController?,
    downloadedVideos: List<Song>,
    pending: List<Pending>,
    onClose: () -> Unit,
    onDownload: (Song) -> Unit
) {
    val ctx = LocalContext.current
    Follows.init(ctx)
    val follows by Follows.channels.collectAsState()
    val likedList by Likes.liked.collectAsState()
    var channel by remember { mutableStateOf<Channel?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }

    LaunchedEffect(state.mediaId) {
        channel = null
        if (state.mediaId.isNotEmpty()) {
            channel = MusicRepository.channelOf(state.mediaId)
        }
    }

    BackHandler(enabled = fullscreen) { fullscreen = false }

    DisposableEffect(fullscreen) {
        if (!fullscreen) {
            return@DisposableEffect onDispose { }
        }
        val act = ctx.findHostActivity()
        val window = act?.window
        if (act != null && window != null) {
            val ctrl = WindowCompat.getInsetsController(window, window.decorView)
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (act != null && window != null) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val song = Song(state.title, state.artist, state.mediaId, state.artwork)
    val saved = downloadedVideos.any { it.url == song.url }
    val pend = pending.firstOrNull { it.video && it.song.url == song.url }
    val isLiked = likedList.any { it.url == song.url }

    if (fullscreen) {
        PlayerBox(
            state = state,
            controller = controller,
            fullscreen = true,
            onFullscreen = { fullscreen = false },
            onSettings = { showQuality = true },
            modifier = Modifier.fillMaxSize()
        )
    } else {
                Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }

            PlayerBox(
                state = state,
                controller = controller,
                fullscreen = false,
                onFullscreen = { fullscreen = true },
                onSettings = { showQuality = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            // Like + Download
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { Likes.toggle(song) },
                    enabled = state.mediaId.isNotEmpty()
                ) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isLiked) "Liked" else "Like")
                }
                OutlinedButton(
                    onClick = { onDownload(song) },
                    enabled = !saved && pend == null && state.mediaId.isNotEmpty()
                ) {
                    Text(
                        when {
                            saved -> "Downloaded"
                            pend != null ->
                                (if (pend.paused) "Paused " else "") +
                                    (pend.progress * 100).toInt() + "%"
                            else -> "Download"
                        }
                    )
                }
            }

            Text(
                state.title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Channel logo + naam + Follow
            val ch = channel
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ch?.avatar != null) {
                    AsyncImage(
                        model = ch.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((ch?.name ?: state.artist).take(1).uppercase())
                    }
                }
                Text(
                    ch?.name ?: state.artist,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ch != null) {
                    val following = follows.any { it.url == ch.url }
                    if (following) {
                        OutlinedButton(onClick = { Follows.toggle(ch) }) { Text("Following") }
                    } else {
                                                Button(onClick = { Follows.toggle(ch) }) { Text("Follow") }
                    }
                }
            }

            RelatedSection(state.mediaId, controller)
        }
    }

    if (showQuality) {
        PlaybackQualityDialog(
            url = state.mediaId,
            controller = controller,
            onDismiss = { showQuality = false }
        )
    }
}

@Composable
private fun PlayerBox(
    state: UiPlayerState,
    controller: MediaController?,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val duration = maxOf(state.duration, 1L).toFloat()

    // 3.5 second baad controls apne aap chhup jate hain
    LaunchedEffect(controls, state.isPlaying, drag, tick) {
        if (controls && state.isPlaying && drag == null) {
            delay(3500)
            controls = false
        }
    }

    val seekBy: (Long) -> Unit = { delta ->
        controller?.let {
            val target = (it.currentPosition + delta).coerceAtLeast(0L)
            val d = it.duration
            it.seekTo(if (d > 0) minOf(target, d) else target)
        }
    }

    Box(modifier.background(Color.Black)) {
        VideoSurface(controller, Modifier.fillMaxSize())

        // Tap = controls dikhao/chhupao, double tap = 10 second aage/peeche
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(controller) {
                    detectTapGestures(
                        onTap = { controls = !controls },
                        onDoubleTap = { off ->
                            if (off.x < size.width / 2f) seekBy(-10_000L) else seekBy(10_000L)
                            controls = true
                            tick++
                        }
                    )
                }
        )

        if (state.buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        AnimatedVisibility(
            visible = controls,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                // Beech me: prev, play/pause, next
                if (!state.buffering) {
                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        IconButton(
                            onClick = {
                                controller?.seekToPreviousMediaItem()
                                tick++
                            },
                            modifier = Modifier.size(48.dp)
                        ) { GlyphPrev(Modifier.size(28.dp)) }

                        IconButton(
                            onClick = {
                                controller?.let { if (it.isPlaying) it.pause() else it.play() }
                                tick++
                            },
                            modifier = Modifier.size(60.dp)
                        ) {
                            if (state.isPlaying) GlyphPause(Modifier.size(40.dp))
                            else GlyphPlay(Modifier.size(40.dp))
                        }

                        IconButton(
                            onClick = {
                                controller?.seekToNextMediaItem()
                                tick++
                            },
                            modifier = Modifier.size(48.dp)
                        ) { GlyphNext(Modifier.size(28.dp)) }
                    }
                }

                // Neeche: 0:00 ---- 2:05:15 aur Settings + Full screen
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            fmt((drag ?: state.position.toFloat()).toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = (drag ?: state.position.toFloat()).coerceIn(0f, duration),
                            onValueChange = {
                                drag = it
                                tick++
                            },
                            onValueChangeFinished = {
                                drag?.let { controller?.seekTo(it.toLong()) }
                                drag = null
                            },
                            valueRange = 0f..duration,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        )
                        Text(
                            fmt(state.duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            tick++
                            onSettings()
                        }) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Quality",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onFullscreen) {
                            GlyphFullscreen(Modifier.size(24.dp), exit = fullscreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackQualityDialog(
    url: String,
    controller: MediaController?,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var heights by remember { mutableStateOf<List<Int>?>(null) }
    val current = VideoQuality.get(ctx)

    LaunchedEffect(url) {
        heights = try {
            if (url.isEmpty()) emptyList()
            else MusicRepository.videoHeights(url).filter { it in 240..1080 }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    val pick: (Int) -> Unit = { q ->
        VideoQuality.set(ctx, q)
        PlaybackMode.reload(controller)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video quality") },
        text = {
            Column {
                Text(
                    "Auto (360p)" + if (current == 0) "  ✓" else "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pick(0) }
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                val list = heights
                if (list == null) {
                    Text(
                        "HD quality dhundh raha hoon...",
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    list.forEach { h ->
                        Text(
                            "${h}p (HD)" + if (current == h) "  ✓" else "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pick(h) }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
