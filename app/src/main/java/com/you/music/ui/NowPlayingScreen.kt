package com.you.music.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import com.you.music.data.Downloads
import com.you.music.data.Likes
import com.you.music.data.MusicRepository
import com.you.music.data.PlaybackMode
import com.you.music.data.Song
import com.you.music.data.VideoQuality

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun String.bigArt(): String =
    replace(Regex("=w\\d+-h\\d+"), "=w800-h800")

private fun qualityLabel(q: Int): String = if (q == 0) "Auto" else "${q}p"

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun NowPlayingScreen(
    state: UiPlayerState,
    controller: MediaController?,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var showLyrics by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var quality by remember { mutableIntStateOf(VideoQuality.get(ctx)) }

    BackHandler(enabled = showLyrics) { showLyrics = false }
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val videoMode by PlaybackMode.video.collectAsState()
    val downloaded by Downloads.downloads.collectAsState()
    val progressMap by Downloads.progress.collectAsState()
    val likedList by Likes.liked.collectAsState()

    // Video mode band ho jaye to fullscreen bhi band
    LaunchedEffect(videoMode) {
        if (!videoMode) fullscreen = false
    }

    // Fullscreen: landscape + system bars chhupao. Band hone par sab wapas.
    val activity = remember(ctx) { ctx.findActivity() }
    DisposableEffect(fullscreen) {
        if (fullscreen && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            insets.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen && activity != null) {
                                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Video / HD na chale to pehle Auto par, phir Song mode par
    LaunchedEffect(state.error) {
        if (state.error != null && videoMode) {
            if (VideoQuality.get(ctx) > 0) {
                VideoQuality.set(ctx, 0)
                quality = 0
                PlaybackMode.reload(controller)
                Toast.makeText(
                    ctx,
                    "HD video nahi chala, Auto quality par wapas",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                PlaybackMode.set(controller, false)
                Toast.makeText(
                    ctx,
                    "Is gaane ka video nahi mila, Song mode me chala diya",
                    Toast.LENGTH_LONG
                ).show()
            }
            state.error = null
        }
    }

    val progress =
        if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val active = MaterialTheme.colorScheme.primary

    val mediaId = if (state.title.isNotEmpty()) controller?.currentMediaItem?.mediaId else null
    val isLiked = mediaId != null && likedList.any { it.url == mediaId }

    if (fullscreen) {
        FullscreenVideo(state, controller, onExit = { fullscreen = false })
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close")
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !videoMode,
                        onClick = { PlaybackMode.set(controller, false) },
                        label = { Text("Song") }
                    )
                    FilterChip(
                        selected = videoMode,
                        onClick = { PlaybackMode.set(controller, true) },
                        label = { Text("Video") }
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = "Lyrics",
                        tint = if (showLyrics) active else inactive
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            when {
                showLyrics -> LyricsView(
                    state = state,
                    controller = controller,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                videoMode -> VideoArea(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )

                else -> Art(
                    state.artwork?.bigArt(),
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(28.dp)
                        )
                        .clickable { showLyrics = true },
                    28.dp
                )
            }

            // Options icon: Download, Full screen, Quality
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = "Options",
                            tint = if (videoMode) active else inactive
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val url = mediaId
                        val isDownloaded = url != null && downloaded.any { it.url == url }
                        val prog = if (url != null) progressMap[url] else null

                        DropdownMenuItem(
                            text = {
                                Text(
                                    when {
                                        prog != null -> "Downloading ${(prog * 100).toInt()}%"
                                        isDownloaded -> "Remove download"
                                        else -> "Download"
                                    }
                                )
                            },
                            enabled = url != null && prog == null,
                            onClick = {
                                menuOpen = false
                                if (url != null) {
                                    Downloads.toggle(
                                        Song(state.title, state.artist, url, state.artwork)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Full screen") },
                            onClick = {
                                menuOpen = false
                                if (!videoMode) PlaybackMode.set(controller, true)
                                fullscreen = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Video quality: ${qualityLabel(quality)}") },
                            onClick = {
                                menuOpen = false
                                showQuality = true
                            }
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = inactive,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    if (mediaId != null) {
                        Likes.toggle(Song(state.title, state.artist, mediaId, state.artwork))
                    }
                }) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) active else inactive
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            WavySeekbar(
                progress = progress,
                isPlaying = state.isPlaying,
                onSeek = { f ->
                    if (state.duration > 0) {
                        controller?.seekTo((f * state.duration).toLong())
                    }
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(state.position), style = MaterialTheme.typography.bodySmall)
                Text(fmtTime(state.duration), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.size(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffle) active else inactive
                    )
                }
                IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(32.dp)
                    )
                }
                FilledIconButton(
                    onClick = {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = {
                    controller?.let {
                        it.repeatMode = when (it.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }
                }) {
                    Icon(
                        if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (state.repeat == Player.REPEAT_MODE_OFF) inactive else active
                    )
                }
            }

            if (state.error != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "Player error: ${state.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red
                )
            }
        }
    }

    if (showQuality) {
        QualityDialog(
            url = mediaId,
            current = quality,
            videoMode = videoMode,
            onPick = { q ->
                quality = q
                VideoQuality.set(ctx, q)
                showQuality = false
                if (videoMode) PlaybackMode.reload(controller)
            },
            onDismiss = { showQuality = false }
        )
    }
}

@Composable
private fun QualityDialog(
    url: String?,
    current: Int,
    videoMode: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var heights by remember { mutableStateOf<List<Int>?>(null) }

    LaunchedEffect(url) {
        heights = if (url == null) {
            emptyList()
        } else {
            try {
                MusicRepository.videoHeights(url)
            } catch (e: Throwable) {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video quality") },
        text = {
            Column {
                QualityRow("Auto (360p, kam data)", current == 0) { onPick(0) }

                val list = heights
                if (list == null) {
                    Text(
                        "HD options load ho rahe hain...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else if (list.isEmpty()) {
                    Text(
                        "Is video ke liye HD options nahi mile",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    list.forEach { h ->
                        QualityRow("${h}p", current == h) { onPick(h) }
                    }
                }

                Text(
                    if (videoMode) "1080p kuch phones par atak sakta hai. 480p ya 720p behtar."
                    else "Ye Video mode me lagega.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Poori screen ka video. Tap karke controls dikhao/chhupao. */
@Composable
private fun FullscreenVideo(
    state: UiPlayerState,
    controller: MediaController?,
    onExit: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val progress =
        if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        VideoArea(controller, Modifier.fillMaxSize(), rounded = false)

        if (controlsVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )

            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Rounded.FullscreenExit,
                    contentDescription = "Exit full screen",
                    tint = Color.White
                )
            }

            FilledIconButton(
                onClick = {
                    controller?.let { if (it.isPlaying) it.pause() else it.play() }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Slider(
                    value = dragValue ?: progress,
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        val f = dragValue
                        if (f != null && state.duration > 0) {
                            controller?.seekTo((f * state.duration).toLong())
                        }
                        dragValue = null
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        fmtTime(state.position),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        fmtTime(state.duration),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** Video dikhane ki jagah. Video ke aspect ratio ke hisaab se size leta hai. */
@Composable
private fun VideoArea(
    controller: MediaController?,
    modifier: Modifier,
    rounded: Boolean = true
) {
    var ratio by remember { mutableStateOf(16f / 9f) }

    DisposableEffect(controller) {
        if (controller == null) {
            return@DisposableEffect onDispose { }
        }
        val applySize = { size: VideoSize ->
            if (size.width > 0 && size.height > 0) {
                ratio = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        applySize(controller.videoSize)
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                applySize(videoSize)
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context -> TextureView(context) },
            update = { view -> controller?.setVideoTextureView(view) },
            onRelease = { view -> controller?.clearVideoTextureView(view) },
            modifier = Modifier
                .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                .then(if (rounded) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
                .background(Color.Black)
        )
    }
}
