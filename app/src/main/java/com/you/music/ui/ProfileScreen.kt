package com.you.music.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.you.music.data.Follows
import com.you.music.data.Song
import java.io.File

data class PlaylistCard(val id: String, val name: String, val thumb: String?, val count: Int)

private val avatarEmoji = listOf("🎧", "🎵", "🎸", "🥁", "🎹", "🎤", "🔥", "⭐")
private val avatarColors = listOf(
    0xFFE57373L, 0xFF64B5F6L, 0xFF81C784L, 0xFFFFB74DL,
    0xFFBA68C8L, 0xFF4DB6ACL, 0xFFFF8A65L, 0xFFA1887FL
)

object ProfileStore {
    private var prefs: SharedPreferences? = null

    // 0..7 = default avatar, -1 = gallery photo
    var avatar by mutableStateOf(0)
    var photoVersion by mutableStateOf(0L)

    fun init(c: Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
        prefs = p
        avatar = p.getInt("avatar", 0)
        photoVersion = p.getLong("photoV", 0L)
    }

    fun photoFile(c: Context): File = File(c.filesDir, "avatar_$photoVersion.jpg")

    fun pickDefault(i: Int) {
        avatar = i
        prefs?.edit()?.putInt("avatar", i)?.apply()
    }

    fun savePhoto(c: Context, uri: Uri) {
        try {
            val v = System.currentTimeMillis()
            c.filesDir.listFiles()
                ?.filter { it.name.startsWith("avatar_") }
                ?.forEach { it.delete() }
            val out = File(c.filesDir, "avatar_$v.jpg")
            c.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            photoVersion = v
            avatar = -1
            prefs?.edit()?.putInt("avatar", -1)?.putLong("photoV", v)?.apply()
        } catch (e: Throwable) {
            // ignore
        }
    }
}

@Composable
private fun DefaultAvatar(index: Int, size: Dp, modifier: Modifier = Modifier) {
    val i = index.coerceIn(0, 7)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(avatarColors[i])),
        contentAlignment = Alignment.Center
    ) {
        Text(avatarEmoji[i], fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun ProfileScreen(
    recent: List<Song>,
    downloaded: List<Song>,
    liked: List<Song>,
    playlists: List<PlaylistCard>,
    onPlay: (List<Song>, Int) -> Unit,
    onSeeAll: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    ProfileStore.init(ctx)
    Follows.init(ctx)
    val follows by Follows.channels.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) ProfileStore.savePhoto(ctx, uri)
        showPicker = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val a = ProfileStore.avatar
            if (a == -1 && ProfileStore.photoVersion != 0L) {
                AsyncImage(
                    model = ProfileStore.photoFile(ctx),
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .clickable { showPicker = true }
                )
            } else {
                DefaultAvatar(a, 96.dp, Modifier.clickable { showPicker = true })
            }
            Text(
                "Photo badalne ke liye tap karo",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        SongRow("Recent", "recent", recent, "Abhi kuch nahi suna", onPlay, onSeeAll)
        SongRow("Download", "download", downloaded, "Abhi koi download nahi", onPlay, onSeeAll)
        SongRow("Liked", "liked", liked, "Abhi koi liked gaana nahi", onPlay, onSeeAll)

        // Follow
        SectionTitle("Follow")
        if (follows.isEmpty()) {
            EmptyText("Abhi kisi channel ko follow nahi kiya")
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(follows) { ch ->
                    Column(
                        Modifier.width(76.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (ch.avatar != null) {
                            AsyncImage(
                                model = ch.avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    ch.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                        Text(
                            ch.name,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Playlist
        SectionTitle("Playlist")
        if (playlists.isEmpty()) {
            EmptyText("Abhi koi playlist nahi")
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlists) { p ->
                    Column(
                        Modifier
                            .width(168.dp)
                            .clickable { onOpenPlaylist(p.id) }
                    ) {
                        AsyncImage(
                            model = p.thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Text(
                            p.name,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${p.count} songs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    SeeAllTile("See all\n${playlists.size}") { onSeeAll("playlist") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Profile photo") },
            text = {
                Column {
                    (0 until 2).forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            (0 until 4).forEach { c ->
                                val i = r * 4 + c
                                val sel = ProfileStore.avatar == i
                                DefaultAvatar(
                                    i,
                                    52.dp,
                                    Modifier
                                        .then(
                                            if (sel) Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            ) else Modifier
                                        )
                                        .clickable {
                                            ProfileStore.pickDefault(i)
                                            showPicker = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickPhoto.launch("image/*") }) {
                    Text("Gallery se photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SeeAllTile(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .width(168.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun SongRow(
    title: String,
    kind: String,
    songs: List<Song>,
    emptyText: String,
    onPlay: (List<Song>, Int) -> Unit,
    onSeeAll: (String) -> Unit
) {
    SectionTitle(title)

    if (songs.isEmpty()) {
        EmptyText(emptyText)
        return
    }

    val shown = songs.take(10)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shown.size) { i ->
            val s = shown[i]
            Column(
                Modifier
                    .width(168.dp)
                    .clickable { onPlay(songs, i) }
            ) {
                AsyncImage(
                    model = s.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Text(
                    s.title,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            SeeAllTile("See all\n${songs.size}") { onSeeAll(kind) }
        }
    }
}
