package com.you.music.ui

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.you.music.data.Channel
import com.you.music.data.Follows
import com.you.music.data.MusicRepository
import com.you.music.data.Pending
import com.you.music.data.Song
import com.you.music.data.channelOf

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
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
    var channel by remember { mutableStateOf<Channel?>(null) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val textureRef = remember { arrayOfNulls<TextureView>(1) }

    LaunchedEffect(state.mediaId) {
        channel = null
        if (state.mediaId.isNotEmpty()) {
            channel = MusicRepository.channelOf(state.mediaId)
        }
    }

    LaunchedEffect(controller) {
        val tv = textureRef[0]
        if (tv != null) controller?.setVideoTextureView(tv)
    }

    DisposableEffect(controller) {
        onDispose {
            val tv = textureRef[0]
            if (tv != null) controller?.clearVideoTextureView(tv)
        }
    }

    val song = Song(state.title, state.artist, state.mediaId, state.artwork)
    val saved = downloadedVideos.any { it.url == song.url }
    val pend = pending.firstOrNull { it.video && it.song.url == song.url }
    val duration = maxOf(state.duration, 1L).toFloat()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }

        // Mini video player
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { c ->
                    TextureView(c).also { tv ->
                        textureRef[0] = tv
                        controller?.setVideoTextureView(tv)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (state.buffering) CircularProgressIndicator()
        }

        // 0:00 ---- 29:00
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(fmt((drag ?: state.position.toFloat()).toLong()), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = (drag ?: state.position.toFloat()).coerceIn(0f, duration),
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { controller?.seekTo(it.toLong()) }
                    drag = null
                },
                valueRange = 0f..duration,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(fmt(state.duration), style = MaterialTheme.typography.labelMedium)
        }

        // Play/Pause + Download
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }) {
                Text(if (state.isPlaying) "Pause" else "Play")
            }
            OutlinedButton(
                onClick = { onDownload(song) },
                enabled = !saved && pend == null && state.mediaId.isNotEmpty()
            ) {
                Text(
                    when {
                        saved -> "Downloaded"
                        pend != null ->
                            (if (pend.paused) "Paused " else "") + (pend.progress * 100).toInt() + "%"
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
    }
}
