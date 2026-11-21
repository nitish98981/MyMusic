package com.you.music.ui

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.you.music.data.AppLanguage
import com.you.music.data.Lang
import com.you.music.data.MusicRepository
import com.you.music.data.Pending
import com.you.music.data.Recommender
import com.you.music.data.SearchMode
import com.you.music.data.Song
import com.you.music.data.VideoLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Tab badalne par bhi sab kuch yahin bacha rehta hai */
object VideoState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var query by mutableStateOf("")
    var searchActive by mutableStateOf(false)
    var searchResults by mutableStateOf<List<Song>>(emptyList())

    // 0 = NEW, 1 = Rec, 2 = Trending
    var tab by mutableIntStateOf(1)
    var lists by mutableStateOf<List<List<Song>>>(listOf(emptyList(), emptyList(), emptyList()))
    var loaded by mutableStateOf(setOf<Int>())
    var loading by mutableStateOf(setOf<Int>())
    var status by mutableStateOf("")
}

private suspend fun fetchTab(ctx: Context, t: Int): List<Song> {
    val hindi = AppLanguage.current.value == Lang.HINDI
    return when (t) {
        0 -> MusicRepository.search(
            if (hindi) "new hindi songs video 2026" else "new english songs official video 2026",
            SearchMode.VIDEOS
        )
        1 -> {
            val rec = try {
                Recommender.forYou(ctx, music = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyList()
            }
            if (rec.size >= 6) rec
            else MusicRepository.search(
                if (hindi) "popular hindi songs video" else "popular english songs video",
                SearchMode.VIDEOS
            )
        }
        else -> MusicRepository.search(
            if (hindi) "trending hindi video today" else "trending music video today",
            SearchMode.VIDEOS
        )
    }
}

private fun dlLabel(saved: Boolean, pend: Pending?): String = when {
    saved -> "Downloaded"
    pend != null ->
        (if (pend.paused) "Paused " else "") + (pend.progress * 100).toInt() + "%"
    else -> "Download"
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
    val ctx = LocalContext.current
    VideoLayout.init(ctx)
    val big by VideoLayout.bigList.collectAsState()
    val focus = LocalFocusManager.current

    val loadTab: (Int, Boolean) -> Unit = { t, force ->
        if ((force || t !in VideoState.loaded) && t !in VideoState.loading) {
            VideoState.loading = VideoState.loading + t
            VideoState.status = ""
            VideoState.scope.launch {
                try {
                    val res = fetchTab(ctx, t)
                    val copy = VideoState.lists.toMutableList()
                    copy[t] = res
                    VideoState.lists = copy
                    VideoState.loaded = VideoState.loaded + t
                    if (res.isEmpty()) {
                        VideoState.status = "Kuch nahi mila. Tab dobara dabao."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    VideoState.status = "Error: ${e.javaClass.simpleName}: ${e.message}"
                } finally {
                    VideoState.loading = VideoState.loading - t
                }
            }
        }
    }

    val runSearch: (String) -> Unit = { q ->
        if (q.isNotBlank()) {
            focus.clearFocus()
            VideoState.searchActive = true
            VideoState.status = "Searching..."
            VideoState.scope.launch {
                try {
                    VideoState.searchResults = MusicRepository.search(q, SearchMode.VIDEOS)
                    VideoState.status =
                        if (VideoState.searchResults.isEmpty()) "Kuch nahi mila" else ""
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    VideoState.status = "Search error: ${e.javaClass.simpleName}: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(VideoState.tab) {
        loadTab(VideoState.tab, false)
    }

    val list = if (VideoState.searchActive) VideoState.searchResults
    else VideoState.lists[VideoState.tab]
    val isLoading = !VideoState.searchActive && VideoState.tab in VideoState.loading

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
                    if (VideoState.query.isNotEmpty() || VideoState.searchActive) {
                        IconButton(onClick = {
                            VideoState.query = ""
                            VideoState.searchActive = false
                            VideoState.status = ""
                            focus.clearFocus()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(VideoState.query) })
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }

        if (!VideoState.searchActive) {
            TabRow(selectedTabIndex = VideoState.tab) {
                listOf("NEW", "Rec", "Trending").forEachIndexed { i, label ->
                    Tab(
                        selected = VideoState.tab == i,
                        onClick = {
                            if (VideoState.tab == i) loadTab(i, true) else VideoState.tab = i
                        },
                        text = { Text(label) }
                    )
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (VideoState.status.isNotBlank()) {
            Text(
                VideoState.status,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (big) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                itemsIndexed(list) { index, v ->
                    val saved = downloadedVideos.any { it.url == v.url }
                    val pend = pending.firstOrNull { it.video && it.song.url == v.url }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        AsyncImage(
                            model = v.thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPlay(list, index) }
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    v.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    v.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { onDownload(v) },
                                enabled = !saved && pend == null
                            ) {
                                Text(dlLabel(saved, pend))
                            }
                        }
                    }
                }
            }
        } else {
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
                            Text(dlLabel(saved, pend))
                        }
                    }
                }
            }
        }
    }
}
