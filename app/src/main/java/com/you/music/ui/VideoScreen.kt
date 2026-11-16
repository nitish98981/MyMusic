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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.you.music.data.MusicRepository
import com.you.music.data.Pending
import com.you.music.data.SearchMode
import com.you.music.data.Song
import kotlinx.coroutines.launch

/** Tab badalne par search ka result na jaye, isliye yahan rakha hai */
object VideoState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Song>>(emptyList())
    var status by mutableStateOf("")
    var loaded by mutableStateOf(false)
}

@Composable
fun VideoScreen(
    downloadedVideos: List<Song>,
    pending: List<Pending>,
    onPlay: (List<Song>, Int) -> Unit,
    onDownload: (Song) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val run: (String) -> Unit = { q ->
        if (q.isNotBlank()) {
            focus.clearFocus()
            scope.launch {
                VideoState.status = "Searching..."
                try {
                    VideoState.results = MusicRepository.search(q, SearchMode.VIDEOS)
                    VideoState.status =
                        if (VideoState.results.isEmpty()) "Kuch nahi mila" else ""
                } catch (e: Throwable) {
                    VideoState.status = "Search error: ${e.javaClass.simpleName}: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!VideoState.loaded) {
            VideoState.loaded = true
            run("trending music video")
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = VideoState.query,
                onValueChange = { VideoState.query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search video") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (VideoState.query.isNotEmpty()) {
                        IconButton(onClick = { VideoState.query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(VideoState.query) })
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }

        if (VideoState.status.isNotBlank()) {
            Text(
                VideoState.status,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val list = VideoState.results
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(list) { v ->
                val index = list.indexOf(v)
                val saved = downloadedVideos.any { it.url == v.url }
                val pend = pending.firstOrNull { it.video && it.song.url == v.url }
                Column {
                    AsyncImage(
                        model = v.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPlay(list, index) }
                    )
                    Text(
                        v.title,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { onDownload(v) },
                        enabled = !saved && pend == null
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
            }
        }
    }
}
