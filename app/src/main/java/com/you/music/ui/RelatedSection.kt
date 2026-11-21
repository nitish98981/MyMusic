package com.you.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.you.music.data.Recommender
import com.you.music.data.Song
import com.you.music.data.toMediaItem
import com.you.music.data.PlaybackMode

@Composable
fun RelatedSection(url: String, controller: MediaController?) {
    var list by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        list = emptyList()
        if (url.isNotEmpty()) {
            loading = true
            list = Recommender.related(url, 45L).take(15)
            loading = false
        }
    }

    if (list.isEmpty() && !loading) return

    Text(
        "Aage dekhein",
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium
    )
    if (loading) {
        Text(
            "Dhundh raha hoon...",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    list.forEachIndexed { i, s ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    controller?.let { c ->
                                                PlaybackMode.set(c, true)
                        c.setMediaItems(list.map { it.toMediaItem() }, i, 0L)
                        c.prepare()
                        c.play()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            AsyncImage(
                model = s.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    s.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    s.artist,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
