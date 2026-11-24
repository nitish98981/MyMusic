package com.you.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.you.music.data.Downloads
import com.you.music.data.History
import com.you.music.data.Likes
import com.you.music.data.MusicRepository
import com.you.music.data.PlaybackMode
import com.you.music.data.Playlists
import com.you.music.data.SearchMode
import com.you.music.data.Song
import com.you.music.data.toMediaItem
import com.you.music.ui.theme.ThemeManager
import com.you.music.ui.theme.ThemeSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UiPlayerState {
    var mediaId by mutableStateOf("")
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artwork by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var hasMedia by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)
}

@Composable
fun rememberPlayerState(controller: MediaController?): UiPlayerState {
    val state = remember { UiPlayerState() }

    DisposableEffect(controller) {
        if (controller == null) {
            return@DisposableEffect onDispose { }
        }
        val sync = {
            state.hasMedia = controller.mediaItemCount > 0
            state.mediaId = controller.currentMediaItem?.mediaId ?: ""
            state.title = controller.mediaMetadata.title?.toString() ?: ""
            state.artist = controller.mediaMetadata.artist?.toString() ?: ""
            state.artwork = controller.mediaMetadata.artworkUri?.toString()
                        com.you.music.data.Privacy.realOf(state.mediaId)?.let {
                state.title = it.title
                state.artist = it.artist
                state.artwork = it.thumb
                        }
            state.isPlaying = controller.isPlaying
            state.buffering = controller.playbackState == Player.STATE_BUFFERING
            state.shuffle = controller.shuffleModeEnabled
            state.repeat = controller.repeatMode
            if (controller.isPlaying) state.error = null
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                sync()
            }

            override fun onPlayerError(error: PlaybackException) {
                state.error = error.errorCodeName
            }
        }
        controller.addListener(listener)
        sync()
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                state.position = it.currentPosition.coerceAtLeast(0L)
                state.duration = it.duration.coerceAtLeast(0L)
            }
            delay(400)
        }
    }
    return state
}

@Composable
fun MusicApp(
    controller: MediaController?,
    state: UiPlayerState,
    lastCrash: String?,
    settings: ThemeSettings,
    themeManager: ThemeManager
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
        RestoreVideoOnReturn(controller)

    // 0 = Video, 1 = Music, 2 = Profile
    var mainTab by remember { mutableIntStateOf(1) }
    var profileSection by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(SearchMode.ALL) }
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showWatch by remember { mutableStateOf(false) }
    var qualityTarget by remember { mutableStateOf<Song?>(null) }
        var miniVideoOff by remember { mutableStateOf(false) }
            var showPrivacy by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    var browserQuery by remember { mutableStateOf("") }
    BackHandler(enabled = showPrivacy) { showPrivacy = false }
    var crash by remember { mutableStateOf(lastCrash) }
    var tab by remember { mutableStateOf(0) }

    var collection by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }

    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var addTarget by remember { mutableStateOf<Song?>(null) }
    var createThenAdd by remember { mutableStateOf<Song?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    val downloaded by Downloads.downloads.collectAsState()
    val progressMap by Downloads.progress.collectAsState()
    val dlError by Downloads.error.collectAsState()
    val liked by Likes.liked.collectAsState()
    val playlists by Playlists.all.collectAsState()
    val recent by History.recent.collectAsState()
    val videoDl by Downloads.videos.collectAsState()
        val videoMode by PlaybackMode.video.collectAsState()
            var inPip by remember { mutableStateOf(false) }
    PipEffect(
        enabled = videoMode && state.hasMedia && state.isPlaying,
        controller = controller,
        onChanged = { inPip = it }
    )
    val pendingDl by Downloads.pending.collectAsState()

    val openPlaylist = playlists.firstOrNull { it.id == openPlaylistId }
    val col = if (tab == 0 && !searchActive) collection else null

    val shown = when {
        col != null -> col.second
        tab == 0 -> results
        tab == 1 -> downloaded
        tab == 2 -> liked
        tab == 3 -> openPlaylist?.songs ?: emptyList()
        else -> recent
    }

    // 5 second se zyada suna to "Recent" me jodo
    LaunchedEffect(state.mediaId) {
        if (state.mediaId.isNotEmpty()) {
            delay(5000)
            if (state.mediaId.isNotEmpty() && state.title.isNotEmpty()) {
                                val played = Song(state.title, state.artist, state.mediaId, state.artwork)
                if (com.you.music.data.Privacy.isActive()) {
                    com.you.music.data.Privacy.logPlay(played, videoMode)
                } else {
                    History.add(played)
                }
            }
        }
    }

    val clearSearch: () -> Unit = {
        query = ""
        results = emptyList()
        status = ""
        searchActive = false
        collection = null
        focus.clearFocus()
    }

    BackHandler(enabled = col != null && mainTab == 1) { collection = null }
    BackHandler(enabled = tab == 3 && openPlaylist != null && mainTab == 1) { openPlaylistId = null }
    BackHandler(enabled = searchActive && tab == 0 && mainTab == 1) { clearSearch() }
    BackHandler(enabled = mainTab == 2 && profileSection != null) { profileSection = null }
    BackHandler(enabled = showWatch) { showWatch = false }
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    val doSearch: () -> Unit = {
        if (query.isNotBlank()) {
            focus.clearFocus()
            tab = 0
            searchActive = true
            val m = mode
            scope.launch {
                status = "Searching..."
                try {
                    results = MusicRepository.search(query, m)
                    status = if (results.isEmpty()) "Kuch nahi mila" else ""
                } catch (e: Throwable) {
                    status = "Search error: ${e.javaClass.simpleName}: ${e.message}"
                }
            }
        }
    }

        val playRaw: (List<Song>, Int) -> Unit = { list, index ->
        controller?.let { c ->
            c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
            c.prepare()
            c.play()
        }
    }

    val playList: (List<Song>, Int) -> Unit = { list, index ->
        PlaybackMode.set(controller, false)
        playRaw(list, index)
    }

    val playNext: (Song) -> Unit = { song ->
        controller?.let { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItem(song.toMediaItem())
                c.prepare()
            } else {
                c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
            }
        }
    }

    val addToQueue: (Song) -> Unit = { song ->
        controller?.let { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItem(song.toMediaItem())
                c.prepare()
            } else {
                c.addMediaItem(song.toMediaItem())
            }
        }
    }

    val toggle: () -> Unit = {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

        val playVideos: (List<Song>, Int) -> Unit = { list, index ->
        PlaybackMode.set(controller, true)
        playRaw(list, index)
        miniVideoOff = false
        showWatch = true
        }

    val isHome = tab == 0 && !searchActive && col == null
    val infoLine = state.error?.let { "Player error: $it" } ?: dlError ?: status
    val currentPlaylistId = if (tab == 3) openPlaylist?.id else null

        if (inPip) {
        VideoSurface(controller, Modifier.fillMaxSize())
    } else Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {

                if (mainTab == 0) {
                    VideoScreen(
                        downloadedVideos = videoDl,
                        pending = pendingDl,
                        onPlay = { list, index -> playVideos(list, index) },
                                                onBrowser = { q ->
                            browserQuery = q
                            showBrowser = true
                        },
                        onDownload = { qualityTarget = it },
                        onSettings = { showSettings = true },
                        modifier = Modifier.weight(1f)
                    )
                } else if (mainTab == 2) {
                    val sec = profileSection
                    if (sec == null) {
                        ProfileScreen(
                            recent = recent,
                            downloaded = downloaded,
                            liked = liked,
                            playlists = playlists.map {
                                PlaylistCard(
                                    it.id,
                                    it.name,
                                    it.songs.firstOrNull()?.thumb,
                                    it.songs.size
                                )
                            },
                            onPlay = { list, index -> playList(list, index) },
                            onSeeAll = { kind ->
                                if (kind == "playlist") {
                                    mainTab = 1
                                    tab = 3
                                    openPlaylistId = null
                                    searchActive = false
                                    collection = null
                                } else {
                                    profileSection = kind
                                }
                            },
                                                        onPrivacy = { showPrivacy = true },
                            onOpenPlaylist = { id ->
                                mainTab = 1
                                tab = 3
                                openPlaylistId = id
                                searchActive = false
                                collection = null
                            },
                            onSettings = { showSettings = true }
                        )
                    } else if (sec == "download") {
                        DownloadScreen(
                            songs = downloaded,
                            videos = videoDl,
                            pending = pendingDl,
                            onBack = { profileSection = null },
                            onPlaySong = { list, index -> playList(list, index) },
                            onPlayVideo = { list, index -> playVideos(list, index) },
                            onDeleteSong = { Downloads.remove(it) },
                            onDeleteVideo = { Downloads.removeVideo(it) },
                            onPause = { Downloads.pause(it) },
                            onResume = { Downloads.resume(it) },
                            onCancel = { Downloads.cancel(it) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val list = when (sec) {
                            "recent" -> recent
                            "liked" -> liked
                            else -> downloaded
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { profileSection = null }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                            Text(
                                when (sec) {
                                    "recent" -> "Recent"
                                    "liked" -> "Liked"
                                    else -> "Download"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        LazyColumn(Modifier.weight(1f)) {
                            itemsIndexed(list) { index, song ->
                                TrackItem(
                                    song = song,
                                    isDownloaded = downloaded.any { it.url == song.url },
                                    progress = progressMap[song.url],
                                    onClick = { playList(list, index) },
                                    onPlayNext = { playNext(song) },
                                    onAddToQueue = { addToQueue(song) },
                                    onDownloadToggle = { Downloads.toggle(song) },
                                    isLiked = liked.any { it.url == song.url },
                                    onLikeToggle = { Likes.toggle(song) },
                                    onAddToPlaylist = { addTarget = song },
                                    onRemove = null
                                )
                            }
                        }
                    }
                } else {

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            placeholder = { Text("Search song") },
                                                        leadingIcon = {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    modifier = Modifier.pointerInput(Unit) {
                                        detectTapGestures(onDoubleTap = {
                                            browserQuery = query
                                            showBrowser = true
                                        })
                                    }
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty() || searchActive) {
                                    IconButton(onClick = clearSearch) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch() })
                        )
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }

                    val c = crash
                    if (c != null) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "Last crash (screenshot bhejo):",
                                style = MaterialTheme.typography.labelMedium
                            )
                            SelectionContainer {
                                Text(
                                    c.take(2500),
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(rememberScrollState()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(onClick = { crash = null }) { Text("Close") }
                        }
                    }

                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = tab == 0,
                            onClick = {
                                tab = 0
                                searchActive = false
                                collection = null
                            },
                            label = { Text("Home") }
                        )
                        FilterChip(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            label = { Text("Offline ${downloaded.size}") }
                        )
                        FilterChip(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            label = { Text("Liked ${liked.size}") }
                        )
                        FilterChip(
                            selected = tab == 3,
                            onClick = {
                                tab = 3
                                openPlaylistId = null
                            },
                            label = { Text("Playlists ${playlists.size}") }
                        )
                        FilterChip(
                            selected = tab == 4,
                            onClick = { tab = 4 },
                            label = { Text("Recent") }
                        )
                    }

                    if (isHome) {
                        HomeScreen(
                            onPlay = { list, index -> playList(list, index) },
                            onOpenCollection = { title, songs -> collection = title to songs },
                            modifier = Modifier.weight(1f)
                        )
                    } else if (tab == 3 && openPlaylist == null) {
                        Column(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showImport = true },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) { Text("Import from YouTube link") }
                            PlaylistList(
                                playlists = playlists,
                                onOpen = { openPlaylistId = it },
                                onNew = {
                                    createThenAdd = null
                                    showCreate = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        if (searchActive && tab == 0) {
                            Row(
                                Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    SearchMode.ALL to "All",
                                    SearchMode.SONGS to "Songs",
                                    SearchMode.VIDEOS to "Videos"
                                ).forEach { (m, label) ->
                                    FilterChip(
                                        selected = mode == m,
                                        onClick = {
                                            mode = m
                                            doSearch()
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        if (col != null) {
                            CollectionHeader(
                                name = col.first,
                                onBack = { collection = null },
                                onPlayAll = {
                                    if (col.second.isNotEmpty()) playList(col.second, 0)
                                }
                            )
                        }

                        if (tab == 3 && openPlaylist != null) {
                            PlaylistHeader(
                                name = openPlaylist.name,
                                onBack = { openPlaylistId = null },
                                onPlayAll = {
                                    if (openPlaylist.songs.isNotEmpty()) playList(openPlaylist.songs, 0)
                                },
                                onDelete = {
                                    Playlists.delete(openPlaylist.id)
                                    openPlaylistId = null
                                }
                            )
                        }

                        if (infoLine.isNotBlank()) {
                            Text(
                                infoLine,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val emptyText = when {
                            tab == 1 && downloaded.isEmpty() ->
                                "Abhi koi download nahi. Gaane ke 3-dot menu se Download dabao."
                            tab == 2 && liked.isEmpty() ->
                                "Abhi koi liked gaana nahi. Player me ♥ dabao."
                            tab == 3 && openPlaylist != null && openPlaylist.songs.isEmpty() ->
                                "Ye playlist khali hai. Gaane ke 3-dot menu se Add to playlist karo."
                            tab == 4 && recent.isEmpty() ->
                                "Abhi kuch nahi suna. Jo gaana 5 second se zyada sunoge, yahan aayega."
                            else -> null
                        }
                        if (emptyText != null) {
                            Text(
                                emptyText,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyColumn(Modifier.weight(1f)) {
                            itemsIndexed(shown) { index, song ->
                                TrackItem(
                                    song = song,
                                    isDownloaded = downloaded.any { it.url == song.url },
                                    progress = progressMap[song.url],
                                    onClick = { playList(shown, index) },
                                    onPlayNext = { playNext(song) },
                                    onAddToQueue = { addToQueue(song) },
                                    onDownloadToggle = { Downloads.toggle(song) },
                                    isLiked = liked.any { it.url == song.url },
                                    onLikeToggle = { Likes.toggle(song) },
                                    onAddToPlaylist = { addTarget = song },
                                    onRemove = currentPlaylistId?.let { pid ->
                                        { Playlists.removeSong(pid, song.url) }
                                    }
                                )
                            }
                        }
                    }

                }
            }

                        if (state.hasMedia) {
                if (videoMode && !showWatch && !showPlayer && !miniVideoOff) {
                    MiniVideoBar(
                        state = state,
                        controller = controller,
                        onOpen = { showWatch = true },
                        onClose = {
                            controller?.pause()
                            miniVideoOff = true
                        }
                    )
                } else {
                    MiniPlayer(
                        state = state,
                        onOpen = { showPlayer = true },
                        onToggle = toggle,
                        onNext = { controller?.seekToNextMediaItem() }
                    )
                }
                        }

            NavigationBar {
                NavigationBarItem(
                    selected = mainTab == 0,
                    onClick = { mainTab = 0 },
                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = "Video") },
                    label = { Text("Video") }
                )
                NavigationBarItem(
                    selected = mainTab == 1,
                    onClick = { mainTab = 1 },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Music") },
                    label = { Text("Music") }
                )
                NavigationBarItem(
                    selected = mainTab == 2,
                    onClick = { mainTab = 2 },
                    icon = { Icon(Icons.Rounded.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }

        // Overlay screens: pointerInput touch ko neeche wali list tak jaane se rokta hai
        AnimatedVisibility(
            visible = showWatch,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                WatchScreen(
                    state = state,
                    controller = controller,
                    downloadedVideos = videoDl,
                    pending = pendingDl,
                    onClose = { showWatch = false },
                    onDownload = { qualityTarget = it }
                )
            }
        }

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                NowPlayingScreen(state, controller, onClose = { showPlayer = false })
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                SettingsScreen(settings, themeManager, onClose = { showSettings = false })
            }
        }

                if (showPrivacy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                PrivacyScreen(
                    controller = controller,
                    onClose = { showPrivacy = false },
                    onBrowser = {
                        browserQuery = ""
                        showBrowser = true
                    },
                    onPlay = { s, v ->
                        if (v) {
                            showPrivacy = false
                            playVideos(listOf(s), 0)
                        } else {
                            playList(listOf(s), 0)
                        }
                    }
                )
            }
        }

        if (showBrowser) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                BrowserScreen(
                    initialQuery = browserQuery,
                    onClose = { showBrowser = false }
                )
            }
        }
        val qt = qualityTarget
        if (qt != null) {
            VideoQualityDialog(
                song = qt,
                onDismiss = { qualityTarget = null },
                onPick = { q ->
                    Downloads.downloadVideo(qt, q)
                    qualityTarget = null
                }
            )
        }

        val target = addTarget
        if (target != null) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { addTarget = null },
                onPick = { id ->
                    Playlists.addSong(id, target)
                    addTarget = null
                },
                onNew = {
                    createThenAdd = target
                    addTarget = null
                    showCreate = true
                }
            )
        }

        if (showCreate) {
            CreatePlaylistDialog(
                onDismiss = {
                    showCreate = false
                    createThenAdd = null
                },
                onCreate = { name ->
                    val id = Playlists.create(name)
                    createThenAdd?.let { Playlists.addSong(id, it) }
                    createThenAdd = null
                    showCreate = false
                }
            )
        }

        if (showImport) {
            ImportPlaylistDialog(
                onDismiss = { showImport = false },
                onDone = { showImport = false }
            )
        }
    }
}

@Composable
private fun CollectionHeader(
    name: String,
    onBack: () -> Unit,
    onPlayAll: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Button(onClick = onPlayAll) { Text("Play all") }
    }
}
