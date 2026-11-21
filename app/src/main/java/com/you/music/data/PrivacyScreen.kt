package com.you.music.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.you.music.data.Privacy
import com.you.music.data.PrivateEntry
import com.you.music.data.Song
import kotlinx.coroutines.delay

@Composable
fun PrivacyScreen(
    controller: MediaController?,
    onClose: () -> Unit,
    onBrowser: () -> Unit,
    onPlay: (Song, Boolean) -> Unit
) {
    val ctx = LocalContext.current
    Privacy.init(ctx)
    val method by Privacy.method.collectAsState()
    val active by Privacy.active.collectAsState()
    val log by Privacy.log.collectAsState()
    var unlocked by remember { mutableStateOf(false) }

    // App background me jaye ya screen lock ho: dobara lock
    DisposableEffect(ctx) {
        val owner = ctx.findLifecycleOwner()
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) unlocked = false
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Privacy mode", style = MaterialTheme.typography.titleLarge)
        }

        when {
            method == "none" -> SetupPart(onDone = {
                unlocked = true
                Privacy.enter()
            })
            !unlocked -> UnlockPart(
                method = method,
                controller = controller,
                onUnlocked = {
                    unlocked = true
                    Privacy.enter()
                }
            )
            else -> HomePart(
                active = active,
                log = log,
                controller = controller,
                onBrowser = onBrowser,
                onPlay = onPlay
            )
        }
    }
}

@Composable
private fun SetupPart(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val km = remember { ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    val secure = km.isDeviceSecure
    var creating by remember { mutableStateOf(false) }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            Privacy.setDevice()
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Privacy mode ka lock chuno", style = MaterialTheme.typography.titleMedium)
        Text(
            "Har baar privacy mode kholne par yahi lock poocha jayega.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!creating) {
            Button(
                onClick = {
                    val i = km.createConfirmDeviceCredentialIntent(
                        "Privacy mode",
                        "Phone ka lock confirm karo"
                    )
                    if (i != null) launcher.launch(i)
                },
                enabled = secure,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Phone ka lock screen password use karo") }

            if (!secure) {
                Text(
                    "Phone me lock screen (PIN / pattern / password) nahi hai, isliye ye option band hai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apna naya password banao") }
        } else {
            OutlinedTextField(
                value = p1,
                onValueChange = { p1 = it },
                label = { Text("Naya password (kam se kam 4 akshar)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = p2,
                onValueChange = { p2 = it },
                label = { Text("Dobara likho") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (err.isNotEmpty()) {
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    if (p1.length < 4) {
                        err = "Kam se kam 4 akshar chahiye"
                    } else if (p1 != p2) {
                        err = "Dono password alag hain"
                    } else {
                        Privacy.setPassword(p1)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Password set karo") }
            TextButton(onClick = {
                creating = false
                p1 = ""
                p2 = ""
                err = ""
            }) { Text("Wapas") }
        }
    }
}

@Composable
private fun UnlockPart(
    method: String,
    controller: MediaController?,
    onUnlocked: () -> Unit
) {
    val ctx = LocalContext.current
    val km = remember { ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var fails by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmReset by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) onUnlocked()
    }

    val launchDevice: () -> Unit = {
        val i = km.createConfirmDeviceCredentialIntent(
            "Privacy mode",
            "Privacy mode kholne ke liye confirm karo"
        )
        if (i != null) launcher.launch(i)
        else error = "Phone me lock screen nahi mila. Neeche Reset karo."
    }

    LaunchedEffect(method) {
        if (method == "device") launchDevice()
    }

    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }

    val waitSec = ((lockedUntil - now) / 1000).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Privacy mode locked hai", style = MaterialTheme.typography.titleMedium)

        if (method == "device") {
            Button(onClick = launchDevice, modifier = Modifier.fillMaxWidth()) {
                Text("Phone ke lock se kholo")
            }
        } else {
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (Privacy.check(pw)) {
                        fails = 0
                        error = ""
                        onUnlocked()
                    } else {
                        fails++
                        pw = ""
                        error = "Galat password"
                        if (fails >= 5) {
                            lockedUntil = System.currentTimeMillis() + 30_000L
                            fails = 0
                        }
                    }
                },
                enabled = pw.isNotEmpty() && waitSec == 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (waitSec > 0) "Ruko: $waitSec second" else "Kholo")
            }
        }

        if (error.isNotEmpty()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        TextButton(onClick = { confirmReset = true }) {
            Text("Password bhool gaye? Reset")
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset karein?") },
            text = {
                Text("Lock hat jayega aur private history delete ho jayegi. Ye wapas nahi aayegi.")
            },
            confirmButton = {
                TextButton(onClick = {
                    controller?.let {
                        it.stop()
                        it.clearMediaItems()
                    }
                    Privacy.resetAll()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HomePart(
    active: Boolean,
    log: List<PrivateEntry>,
    controller: MediaController?,
    onBrowser: () -> Unit,
    onPlay: (Song, Boolean) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (active) "Privacy mode ON" else "Privacy mode OFF",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "ON me jo dekhoge ya sunoge wo Recent me nahi aayega aur recommendation par asar nahi dalega. " +
                        "Notification me naam \"Privacy mode\" dikhega, asli naam yahin niche milega.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Browser me phone ki location block rehti hai. Privacy mode me download abhi band hai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (active) {
                    Button(
                        onClick = {
                            controller?.let {
                                it.stop()
                                it.clearMediaItems()
                            }
                            Privacy.exit()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Privacy mode band karo") }
                } else {
                    Button(
                        onClick = { Privacy.enter() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Privacy mode chalu karo") }
                }
                OutlinedButton(onClick = onBrowser, modifier = Modifier.fillMaxWidth()) {
                    Text("DuckDuckGo private search")
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Private history (${log.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                if (log.isNotEmpty()) {
                    TextButton(onClick = { Privacy.clearLog() }) { Text("Sab saaf karo") }
                }
            }
        }

        items(log) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(e.song, e.video) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = e.song.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        e.song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        e.song.artist + if (e.video) "  •  video" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
