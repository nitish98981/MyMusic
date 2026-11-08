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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object Downloads {

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<Song>>(emptyList())
    val downloads: StateFlow<List<Song>> = _downloads

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        _downloads.value = load()
    }

    fun toggle(song: Song) {
        if (_downloads.value.any { it.url == song.url }) remove(song) else download(song)
    }

    fun download(song: Song) {
        if (_progress.value.containsKey(song.url)) return
        if (_downloads.value.any { it.url == song.url }) return

        _error.value = null
        _progress.update { it + (song.url to 0f) }
        startService()

        scope.launch {
            try {
                val spec = DataSpec.Builder()
                                        .setUri(Uri.parse("yt://resolve?m=audio&u=" + Uri.encode(song.url)))
                    .build()

                CacheWriter(
                    AudioCache.downloadSource(),
                    spec,
                    null,
                    CacheWriter.ProgressListener { requestLength, bytesCached, _ ->
                        if (requestLength > 0) {
                            val p = (bytesCached.toFloat() / requestLength).coerceIn(0f, 1f)
                            _progress.update { it + (song.url to p) }
                        }
                    }
                ).cache()

                _downloads.value = _downloads.value + song
                save()
            } catch (e: Throwable) {
                _error.value = "Download error: ${e.javaClass.simpleName}: ${e.message}"
            } finally {
                _progress.update { it - song.url }
            }
        }
    }

    fun remove(song: Song) {
        scope.launch {
            try {
                AudioCache.downloadCache.removeResource(song.url)
            } catch (e: Throwable) {
                // ignore
            }
            _downloads.value = _downloads.value.filter { it.url != song.url }
            save()
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

    private fun save() {
        val arr = JSONArray()
        _downloads.value.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("artist", it.artist)
                    .put("url", it.url)
                    .put("thumb", it.thumb ?: "")
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun load(): List<Song> {
        return try {
            val arr = JSONArray(prefs.getString("list", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Song(
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    url = o.getString("url"),
                    thumb = o.optString("thumb").ifEmpty { null }
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }
}
