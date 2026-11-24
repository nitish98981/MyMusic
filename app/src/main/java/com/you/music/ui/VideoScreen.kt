package com.you.music.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.you.music.data.AppLanguage
import com.you.music.data.Downloads
import com.you.music.data.History
import com.you.music.data.Lang
import com.you.music.data.Likes
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Tab badalne par bhi sab kuch yahin bacha rehta hai */
object VideoState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var query by mutableStateOf("")
    var searchActive by mutableStateOf(false)
    var searchResults by mutableStateOf<List<Song>>(emptyList())

    var tabKey by mutableStateOf("Home")
    var lists by mutableStateOf<Map<String, List<Song>>>(emptyMap())
    var loaded by mutableStateOf(setOf<String>())
    var loading by mutableStateOf(setOf<String>())
    var status by mutableStateOf("")
}

private class Topic(
    val label: String,
    val words: List<String>,
    val qHi: String,
    val qEn: String
)

private val allTopics = listOf(
    Topic("Free Fire", listOf("free fire", "freefire", "garena"), "free fire gameplay hindi", "free fire gameplay"),
    Topic("BGMI", listOf("bgmi", "pubg"), "bgmi gameplay hindi", "pubg mobile gameplay"),
    Topic("Minecraft", listOf("minecraft"), "minecraft hindi gameplay", "minecraft gameplay"),
    Topic("GTA", listOf("gta", "vice city"), "gta 6 gameplay hindi", "gta 6 gameplay"),
    Topic("Gaming", listOf("gameplay", "gaming", "gamer", "walkthrough"), "gaming videos hindi", "gaming videos"),
    Topic("Cricket", listOf("cricket", "ipl", "t20", "kohli", "rohit"), "cricket highlights hindi", "cricket highlights"),
    Topic("Fun", listOf("funny", "comedy", "prank", "roast", "meme"), "funny comedy videos hindi", "funny videos compilation"),
    Topic("Music", listOf("official video", "music video", "song", "lyrical", "remix"), "new hindi songs video", "new english songs official video"),
    Topic("News", listOf("news", "breaking", "live update"), "aaj ki taza khabar", "world news today"),
    Topic("Tech", listOf("tech", "review", "unboxing", "smartphone", "iphone"), "tech review hindi", "tech review"),
    Topic("Anime", listOf("anime", "naruto", "one piece"), "anime hindi dubbed", "anime episode"),
    Topic("Cooking", listOf("recipe", "cooking", "kitchen"), "recipe hindi", "easy recipes"),
    Topic("Movies", listOf("trailer", "movie", "web series"), "movie trailer hindi", "movie trailer")
)

private val fixedLabels = listOf("Gaming", "Fun", "Music", "News")

/** Tumhari history se tab list banata hai */
private fun computeTabs(): Pair<List<String>, List<Topic>> {
    val texts = (History.recent.value.take(60) +
        Likes.liked.value +
        Downloads.downloads.value +
        Downloads.videos.value)
        .map { (it.title + " " + it.artist).lowercase() }

    val counts = allTopics.associateWith { t ->
        texts.count { x -> t.words.any { w -> x.contains(w) } }
    }
    val dyn = allTopics
        .filter { (counts[it] ?: 0) >= 2 }
        .sortedByDescending { counts[it] ?: 0 }
        .take(4)
    val dynLabels = dyn.map { it.label }
    val tabs = listOf("Home", "NEW", "Rec", "Trending") +
        dynLabels +
        fixedLabels.filter { it !in dynLabels }
    return tabs to dyn
}

private suspend fun safeList(block: suspend () -> List<Song>): List<Song> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        emptyList()
    }

private suspend fun searchVideos(q: String): List<Song> =
    MusicRepository.search(q, SearchMode.VIDEOS)

private fun interleave(vararg lists: List<Song>): List<Song> {
    val out = ArrayList<Song>()
    val seen = HashSet<String>()
    var i = 0
    while (lists.any { i < it.size }) {
        for (l in lists) {
            if (i < l.size && seen.add(l[i].url)) out.add(l[i])
        }
        i++
    }
    return out
}

private suspend fun fetchHome(ctx: Context, hindi: Boolean, dyn: List<Topic>): List<Song> =
    coroutineScope {
        val a = async { safeList { Recommender.forYou(ctx, music = false, count = 20) } }
        val b = async {
            safeList {
                searchVideos(if (hindi) "trending hindi video today" else "trending music video today")
            }
        }
        val c = async {
            val t = dyn.firstOrNull()
            if (t == null) emptyList() else safeList { searchVideos(if (hindi) t.qHi else t.qEn) }
        }
        interleave(a.await(), c.await(), b.await())
    }

private suspend fun fetchByKey(ctx: Context, key: String, dyn: List<Topic>): List<Song> {
    val hindi = AppLanguage.current.value == Lang.HINDI
    return when (key) {
        "Home" -> fetchHome(ctx, hindi, dyn)
        "NEW" -> searchVideos(
            if (hindi) "new hindi songs video 2026" else "new english songs official video 2026"
        )
        "Rec" -> {
            val rec = safeList { Recommender.forYou(ctx, music = false) }
            if (rec.size >= 6) rec
            else searchVideos(
                if (hindi) "popular hindi songs video" else "popular english songs video"
            )
        }
        "Trending" -> searchVideos(
            if (hindi) "trending hindi video today" else "trending music video today"
        )
        else -> {
            val t = allTopics.firstOrNull { it.label == key }
            if (t == null) emptyList() else searchVideos(if (hindi) t.qHi else t.qEn)
        }
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
    onBrowser: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    VideoLayout.init(ctx)
    val big by VideoLayout.bigList.collectAsState()
    val focus = LocalFocusManager.current

    val computed = remember { computeTabs() }
    val tabs = computed.first
    val dyn = computed.second

    val loadTab: (String, Boolean) -> Unit = { key, force ->
        if ((force || key !in VideoState.loaded) && key !in VideoState.loading) {
            VideoState.loading = VideoState.loading + key
            VideoState.status = ""
            VideoState.scope.launch {
                try {
                    val res = fetchByKey(ctx, key, dyn)
                    VideoState.lists = VideoState.lists + (key to res)
                    VideoState.loaded = VideoState.loaded + key
                    if (res.isEmpty()) {
                        VideoState.status = "Kuch nahi mila. Tab dobara dabao."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    VideoState.status = "Error: ${e.javaClass.simpleName}: ${e.message}"
                } finally {
                    VideoState.loading = VideoState.loading - key
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
                    VideoState.searchResults = searchVideos(q)
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

    LaunchedEffect(VideoState.tabKey) {
        if (VideoState.tabKey !in tabs) VideoState.tabKey = "Home"
        loadTab(VideoState.tabKey, false)
    }

    val list = if (VideoState.searchActive) VideoState.searchResults
    else VideoState.lists[VideoState.tabKey] ?: emptyList()
    val isLoading = !VideoState.searchActive && VideoState.tabKey in VideoState.loading
    val tabIndex = tabs.indexOf(VideoState.tabKey).coerceAtLeast(0)

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
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { onBrowser(VideoState.query) })
                        }
                    )
                },
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
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 8.dp) {
                tabs.forEach { key ->
                    Tab(
                        selected = VideoState.tabKey == key,
                        onClick = {
                            if (VideoState.tabKey == key) loadTab(key, true)
                            else VideoState.tabKey = key
                        },
                        text = { Text(key) }
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
