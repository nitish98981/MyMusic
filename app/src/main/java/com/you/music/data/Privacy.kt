package com.you.music.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PrivateEntry(val song: Song, val video: Boolean, val at: Long)

object Privacy {

    private var prefs: SharedPreferences? = null

    // none | device | app
    private val _method = MutableStateFlow("none")
    val method: StateFlow<String> = _method

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val _log = MutableStateFlow<List<PrivateEntry>>(emptyList())
    val log: StateFlow<List<PrivateEntry>> = _log

    // Notification me naam chhupa hota hai, app ke andar asli naam yahan se aata hai
    private val real = ConcurrentHashMap<String, Song>()

    fun isActive(): Boolean = _active.value

    fun init(c: Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        prefs = p
        _method.value = p.getString("method", "none") ?: "none"
        _log.value = try {
            val arr = JSONArray(p.getString("log", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                PrivateEntry(
                    Song(
                        o.getString("title"),
                        o.getString("artist"),
                        o.getString("url"),
                        o.optString("thumb").ifEmpty { null }
                    ),
                    o.optBoolean("video", false),
                    o.optLong("at", 0L)
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun enter() {
        _active.value = true
    }

    fun exit() {
        _active.value = false
        real.clear()
    }

    fun remember(song: Song) {
        real[song.url] = song
    }

    fun realOf(url: String): Song? = if (url.isEmpty()) null else real[url]

    // ---- Lock ----

    private fun hash(pw: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pw.toCharArray(), salt, 60_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun setDevice() {
        prefs?.edit()
            ?.putString("method", "device")
            ?.remove("salt")
            ?.remove("hash")
            ?.apply()
        _method.value = "device"
    }

    fun setPassword(pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs?.edit()
            ?.putString("method", "app")
            ?.putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            ?.putString("hash", hash(pw, salt))
            ?.apply()
        _method.value = "app"
    }

    fun check(pw: String): Boolean {
        val p = prefs ?: return false
        val salt = Base64.decode(p.getString("salt", "") ?: "", Base64.NO_WRAP)
        val want = p.getString("hash", "") ?: return false
        return MessageDigest.isEqual(hash(pw, salt).toByteArray(), want.toByteArray())
    }

    // ---- Private history ----

    fun logPlay(song: Song, video: Boolean) {
        val e = PrivateEntry(song, video, System.currentTimeMillis())
        _log.value = (listOf(e) + _log.value.filter { it.song.url != song.url }).take(200)
        saveLog()
    }

    fun clearLog() {
        _log.value = emptyList()
        saveLog()
    }

    /** Password bhool jane par: lock aur private history dono hat jate hain */
    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        _method.value = "none"
        _log.value = emptyList()
        _active.value = false
        real.clear()
    }

    private fun saveLog() {
        val arr = JSONArray()
        _log.value.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.song.title)
                    .put("artist", it.song.artist)
                    .put("url", it.song.url)
                    .put("thumb", it.song.thumb ?: "")
                    .put("video", it.video)
                    .put("at", it.at)
            )
        }
        prefs?.edit()?.putString("log", arr.toString())?.apply()
    }
}
