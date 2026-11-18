package com.you.music.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.you.music.data.AppLanguage
import com.you.music.data.DataSaver
import com.you.music.data.Lang
import com.you.music.data.Prefs
import com.you.music.playback.SleepTimer
import kotlinx.coroutines.delay

@Composable
fun ExtraSettings() {
    val ctx = LocalContext.current
    var volumeShortcut by remember { mutableStateOf(Prefs.volumeShortcut(ctx)) }
        var dataSaver by remember { mutableStateOf(DataSaver.enabled) }

    LanguageSetting()

    Spacer(Modifier.size(24.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Volume button shortcut", style = MaterialTheme.typography.titleMedium)
            Text(
                "App khuli ho tab Volume Up do baar jaldi dabao: gaana chalu ya band",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = volumeShortcut,
            onCheckedChange = {
                volumeShortcut = it
                Prefs.setVolumeShortcut(ctx, it)
            }
        )
    }
    Spacer(Modifier.size(24.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Data saver", style = MaterialTheme.typography.titleMedium)
            Text(
                "Kam data: gaana aur video chhoti quality me chalenge (video 360p tak)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = dataSaver,
            onCheckedChange = {
                dataSaver = it
                DataSaver.set(ctx, it)
            }
        )
    }
    Spacer(Modifier.size(24.dp))
    SleepTimerSection()
}

@Composable
private fun LanguageSetting() {
    val ctx = LocalContext.current
    val lang by AppLanguage.current.collectAsState()

    Text("Language / भाषा", style = MaterialTheme.typography.titleMedium)
    Text(
        "Home ke gaane aur artists is bhasha ke hisaab se aate hain",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.values().forEach { l ->
            FilterChip(
                selected = lang == l,
                onClick = { AppLanguage.set(ctx, l) },
                label = { Text(if (l == Lang.HINDI) "हिन्दी" else "English") }
            )
        }
    }
}

@Composable
private fun SleepTimerSection() {
    val end by SleepTimer.endAt.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var picked by remember { mutableIntStateOf(0) }

    LaunchedEffect(end) {
        while (end != 0L) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    val effective = if (end == 0L) 0 else picked
    val left = if (end == 0L) 0L else ((end - now) / 60_000L + 1).coerceAtLeast(1L)

    Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
    Text(
        if (end == 0L) "Band hai" else "Gaana $left minute me ruk jayega",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(0, 15, 30, 45, 60).forEach { m ->
            FilterChip(
                selected = effective == m,
                onClick = {
                    picked = m
                    if (m == 0) SleepTimer.cancel() else SleepTimer.start(m)
                },
                label = { Text(if (m == 0) "Off" else "$m min") }
            )
        }
    }
}
