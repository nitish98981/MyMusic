package com.you.music.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException

data class Pending(
    val song: Song,
    val video: Boolean,
    val paused: Boolean,
    val progress: Float
)

object Downloads {

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<Song>>(emptyList())
    val downloads: StateFlow<List<Song>> = _downloads

    private val _videos = MutableStateFlow<List<Song>>(emptyList())
    val videos: StateFlow<List<Song>> = _videos

    private val _pending = MutableStateFlow<List<Pending>>(emptyList())
    val pending: StateFlow<List<Pending>> = _pending

    // sirf chalte hue downloads (service isse notification dikhati hai)
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val writers = HashMap<String, CacheWriter>()
    private val running = HashSet<String>()

    private fun keyOf(song: Song, video: Boolean) = if (video) "v:" + song.url else song.url
    private fun keyOf(p: Pending) = keyOf(p.song, p.video)
    private fun findPending(key: String) = _pending.value.firstOrNull { keyOf(it) == key }

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        _downloads.value = loadSongs("list")
        _videos.value = loadSongs("videos")
        _pending.value = loadPending()
    }

    fun toggle(song: Song) {
        if (_downloads.value.any { it.url == song.url }) remove(song) else download(song)
    }

    fun download(song: Song) {
        if (_downloads.value.any { it.url == song.url }) return
        _error.value = null
        launchDownload(song, false)
    }

    fun downloadVideo(song: Song) {
        if (_videos.value.any { it.url == song.url }) return
        _error.value = null
        launchDownload(song, true)
    }

    fun pause(p: Pending) {
        val key = keyOf(p)
        setPending(p.song, p.video, paused = true, progress = null, save = true)
        synchronized(writers) { writers[key]?.cancel() }
    }

    fun resume(p: Pending) {
        _error.value = null
        launchDownload(p.song, p.video)
    }

    fun cancel(p: Pending) {
        val key = keyOf(p)
        _pending.update { l -> l.filter { keyOf(it) != key } }
        savePending()
        synchronized(writers) { writers[key]?.cancel() }
        scope.launch {
            var n = 0
            while (n < 15 && synchronized(running) { running.contains(key) }) {
                delay(200)
                n++
            }
            removeCache(key)
        }
    }

    fun remove(song: Song) {
        scope.launch {
            removeCache(song.url)
            _downloads.value = _downloads.value.filter { it.url != song.url }
            save()
        }
    }

    fun removeVideo(song: Song) {
        scope.launch {
            removeCache("v:" + song.url)
            _videos.value = _videos.value.filter { it.url != song.url }
            save()
        }
    }

    private fun removeCache(key: String) {
        try {
            AudioCache.downloadCache.removeResource(key)
        } catch (e: Throwable) {
            // ignore
        }
    }

    private fun launchDownload(song: Song, video: Boolean) {
        val key = keyOf(song, video)
        synchronized(running) { if (!running.add(key)) return }

        val startP = findPending(key)?.progress ?: 0f
        setPending(song, video, paused = false, progress = startP, save = true)
        _progress.update { it + (key to startP) }
        startService()

        scope.launch {
            var finished = false
            try {
                val mode = if (video) "video" else "audio"
                val b = DataSpec.Builder()
                    .setUri(Uri.parse("yt://resolve?m=" + mode + "&u=" + Uri.encode(song.url)))
                if (video) b.setKey(key)

                val writer = CacheWriter(
                    AudioCache.downloadSource(),
                    b.build(),
                    null,
                    CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                        if (requestLength > 0) {
                            val p = (bytesCached.toFloat() / requestLength).coerceIn(0f, 1f)
                            _progress.update { it + (key to p) }
                            updateProgress(key, p)
                        }
                    }
                )
                synchronized(writers) { writers[key] = writer }

                val st = findPending(key)
                if (st == null || st.paused) throw InterruptedIOException()

                writer.cache()
                finished = true
            } catch (e: Throwable) {
                val st = findPending(key)
                val quiet = st == null || st.paused
                if (!quiet) {
                    _error.value = "Download error: ${e.javaClass.simpleName}: ${e.message}"
                    // retry ke liye Continue dikhega
                    setPending(song, video, paused = true, progress = null, save = true)
                }
            } finally {
                synchronized(writers) { writers.remove(key) }
                synchronized(running) { running.remove(key) }
                _progress.update { it - key }
            }

            if (finished) {
                if (video) {
                    _videos.value = _videos.value.filter { it.url != song.url } + song
                } else {
                    _downloads.value = _downloads.value.filter { it.url != song.url } + song
                }
                _pending.update { l -> l.filter { keyOf(it) != key } }
                save()
                savePending()
            }
        }
    }

    private fun setPending(
        song: Song,
        video: Boolean,
        paused: Boolean,
        progress: Float?,
        save: Boolean
    ) {
        val key = keyOf(song, video)
        _pending.update { list ->
            val old = list.firstOrNull { keyOf(it) == key }
            val item = Pending(song, video, paused, progress ?: old?.progress ?: 0f)
            if (old == null) list + item
            else list.map { if (keyOf(it) == key) item else it }
        }
        if (save) savePending()
    }

    private fun updateProgress(key: String, p: Float) {
        _pending.update { list ->
            list.map { if (keyOf(it) == key) it.copy(progress = p) else it }
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, DownloadService::class.java)
            )
        } catch (e: Throwable) {
            // service na chale to bhi download app khuli rehne par chalega
        }
    }

    private fun songJson(s: Song): JSONObject =
        JSONObject()
            .put("title", s.title)
            .put("artist", s.artist)
            .put("url", s.url)
            .put("thumb", s.thumb ?: "")

    private fun songFrom(o: JSONObject): Song =
        Song(
            title = o.getString("title"),
            artist = o.getString("artist"),
            url = o.getString("url"),
            thumb = o.optString("thumb").ifEmpty { null }
        )

    private fun save() {
        val a = JSONArray()
        _downloads.value.forEach { a.put(songJson(it)) }
        val v = JSONArray()
        _videos.value.forEach { v.put(songJson(it)) }
        prefs.edit().putString("list", a.toString()).putString("videos", v.toString()).apply()
    }

    private fun savePending() {
        val arr = JSONArray()
        _pending.value.forEach { arr.put(songJson(it.song).put("video", it.video)) }
        prefs.edit().putString("pending", arr.toString()).apply()
    }

    private fun loadSongs(name: String): List<Song> {
        return try {
            val arr = JSONArray(prefs.getString(name, "[]"))
            (0 until arr.length()).map { songFrom(arr.getJSONObject(it)) }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun loadPending(): List<Pending> {
        return try {
            val arr = JSONArray(prefs.getString("pending", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Pending(songFrom(o), o.optBoolean("video", false), true, 0f)
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }
}
