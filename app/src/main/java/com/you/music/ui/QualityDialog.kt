package com.you.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.you.music.data.DataSaver
import com.you.music.data.MusicRepository
import com.you.music.data.Song

@Composable
fun VideoQualityDialog(
    song: Song,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    var heights by remember { mutableStateOf<List<Int>?>(null) }

    LaunchedEffect(song.url) {
        heights = try {
            MusicRepository.videoHeights(song.url).filter { it in 240..1080 }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download quality") },
        text = {
            Column {
                if (DataSaver.enabled) {
                    Text(
                        "Data saver on hai, isliye quality 360p tak hi jayegi.",
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Auto (360p, kam space)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(0) }
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                val list = heights
                if (list == null) {
                    Text(
                        "HD quality dhundh raha hoon...",
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    list.forEach { h ->
                        Text(
                            "${h}p (HD, zyada space)",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(h) }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
