package com.you.music.ui

import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import com.you.music.data.Likes
import com.you.music.data.PlaybackMode
import com.you.music.data.Song

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun String.bigArt(): String =
    replace(Regex("=w\\d+-h\\d+"), "=w800-h800")

@Composable
fun NowPlayingScreen(
    state: UiPlayerState,
    controller: MediaController?,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var showLyrics by remember { mutableStateOf(false) }
    BackHandler(enabled = showLyrics) { showLyrics = false }

    val videoMode by PlaybackMode.video.collectAsState()

    // Video na mile to wapas Song mode me
    LaunchedEffect(state.error) {
        if (state.error != null && videoMode) {
            PlaybackMode.set(controller, false)
            Toast.makeText(
                ctx,
                "Is gaane ka video nahi mila, Song mode me chala diya",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val progress =
        if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val active = MaterialTheme.colorScheme.primary

    val likedList by Likes.liked.collectAsState()
    val mediaId = if (state.title.isNotEmpty()) controller?.currentMediaItem?.mediaId else null
    val isLiked = mediaId != null && likedList.any { it.url == mediaId }

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

        Spacer(Modifier.size(16.dp))

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

        Spacer(Modifier.size(20.dp))

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

/** Video dikhane ki jagah. Video ke aspect ratio ke hisaab se size leta hai. */
@Composable
private fun VideoArea(controller: MediaController?, modifier: Modifier) {
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
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        )
    }
}
