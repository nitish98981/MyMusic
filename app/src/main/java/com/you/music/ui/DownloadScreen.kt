package com.you.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.you.music.data.Pending
import com.you.music.data.Song

@Composable
fun DownloadScreen(
    songs: List<Song>,
    videos: List<Song>,
    pending: List<Pending>,
    onBack: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onPlayVideo: (List<Song>, Int) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onDeleteVideo: (Song) -> Unit,
    onPause: (Pending) -> Unit,
    onResume: (Pending) -> Unit,
    onCancel: (Pending) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) }
    val vPending = pending.filter { it.video }
    val sPending = pending.filter { !it.video }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Download", style = MaterialTheme.typography.titleLarge)
        }

        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Video ${videos.size}") }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Song ${songs.size}") }
            )
        }

        val active = if (tab == 0) vPending else sPending
        if (active.isNotEmpty()) {
            Text(
                "Downloading",
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            active.forEach { p ->
                PendingRow(
                    p = p,
                    onPause = { onPause(p) },
                    onResume = { onResume(p) },
                    onCancel = { onCancel(p) }
                )
            }
        }

        if (tab == 0) {
            if (videos.isEmpty()) {
                if (active.isEmpty()) {
                    Text(
                        "Abhi koi video download nahi.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(videos) { v ->
                        val i = videos.indexOf(v)
                        Column(Modifier.clickable { onPlayVideo(videos, i) }) {
                            AsyncImage(
                                model = v.thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    v.title,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { onDeleteVideo(v) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (songs.isEmpty()) {
                if (active.isEmpty()) {
                    Text(
                        "Abhi koi song download nahi. Gaane ke 3-dot menu se Download dabao.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(songs) { index, s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPlaySong(songs, index) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = s.thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    s.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    s.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onDeleteSong(s) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingRow(
    p: Pending,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val pct = (p.progress * 100).toInt()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.song.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = if (p.paused) onResume else onPause) {
                Text(if (p.paused) "Continue" else "Pause")
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "Cancel")
            }
        }
        LinearProgressIndicator(
            progress = { p.progress },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (p.paused) "Paused $pct%" else "Downloading $pct%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
