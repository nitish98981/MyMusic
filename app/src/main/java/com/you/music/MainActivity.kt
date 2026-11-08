package com.you.music

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.you.music.data.History
import com.you.music.data.PlaybackMode
import com.you.music.data.LinkResolver
import com.you.music.data.Prefs
import com.you.music.data.toMediaItem
import com.you.music.playback.PlaybackService
import com.you.music.ui.MusicApp
import com.you.music.ui.LanguageGate
import com.you.music.ui.rememberPlayerState
import com.you.music.ui.theme.AppTheme
import com.you.music.ui.theme.Presets
import com.you.music.ui.theme.ThemeManager
import com.you.music.ui.theme.ThemeMode
import com.you.music.ui.theme.ThemeSettings
import com.you.music.ui.theme.cachedSeed
import com.you.music.ui.theme.schemeFromSeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)
    private val pendingUrl = mutableStateOf<String?>(null)
    private val themeManager by lazy { ThemeManager(applicationContext) }

    private var lastVolumeUp = 0L
    private var swallowVolumeUp = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Saved theme pehle hi padh lo, taaki shuru me white/light flash na aaye
        val initial = runBlocking { themeManager.settings.first() }
        applyWindowColors(initial)

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val crashFile = File(filesDir, "crash.txt")
        val lastCrash: String? = if (crashFile.exists()) {
            val text = crashFile.readText()
            crashFile.delete()
            text
        } else null

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { controllerState.value = future.get() },
            ContextCompat.getMainExecutor(this)
        )

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by themeManager.settings.collectAsState(initial = initial)
            val controller = controllerState.value
            val state = rememberPlayerState(controller)

            // YouTube se share kiya gaana
            LaunchedEffect(pendingUrl.value, controller) {
                val url = pendingUrl.value
                if (url != null && controller != null) {
                    try {
                        val song = LinkResolver.song(url)
                        controller.setMediaItem(song.toMediaItem())
                        controller.prepare()
                        controller.play()
                    } catch (e: Throwable) {
                        Toast.makeText(
                            this@MainActivity,
                            "Ye link nahi chala: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    pendingUrl.value = null
                }
            }

            AppTheme(settings, state.artwork) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                        LanguageGate {
                        MusicApp(controller, state, lastCrash, settings, themeManager)
                                        }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = LinkResolver.extractUrl(text)
        if (url == null) {
            Toast.makeText(this, "Ye YouTube ka link nahi hai", Toast.LENGTH_SHORT).show()
            return
        }
        pendingUrl.value = url
    }

    // Volume Up do baar dabao: gaana chalu ya band (sirf jab app khuli ho)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && Prefs.volumeShortcut(this)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val now = SystemClock.uptimeMillis()
                val delta = now - lastVolumeUp
                if (delta in 1L..600L) {
                    lastVolumeUp = 0L
                    swallowVolumeUp = true
                    toggleFromShortcut()
                    return true
                }
                lastVolumeUp = now
            } else if (event.action == KeyEvent.ACTION_UP && swallowVolumeUp) {
                swallowVolumeUp = false
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun toggleFromShortcut() {
        val c = controllerState.value ?: return

        if (c.isPlaying) {
            c.pause()
            return
        }
        if (c.mediaItemCount > 0) {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
            return
        }

        val recent = History.recent.value
        if (recent.isEmpty()) {
            Toast.makeText(this, "Abhi koi recent gaana nahi", Toast.LENGTH_SHORT).show()
            return
        }
        c.setMediaItems(recent.map { it.toMediaItem() }, 0, 0L)
        c.prepare()
        c.play()
    }

    private fun applyWindowColors(s: ThemeSettings) {
        val systemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val dark = when (s.mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            else -> true
        }
        val seed = if (s.artColors) {
            cachedSeed(this).takeIf { it != 0 } ?: Presets[s.preset].color
        } else {
            Presets[s.preset].color
        }
        val bg = schemeFromSeed(seed, dark, s.mode == ThemeMode.AMOLED).background.toArgb()
        window.setBackgroundDrawable(ColorDrawable(bg))
        window.statusBarColor = bg
        window.navigationBarColor = bg
    }

        override fun onStop() {
        // App background me jaye ya screen lock ho to video band, sirf audio (data bachta hai)
        if (PlaybackMode.video.value && !isChangingConfigurations) {
            PlaybackMode.set(controllerState.value, false)
        }
        super.onStop()
        }
    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
