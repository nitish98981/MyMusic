package com.you.music.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Channel(val name: String, val url: String, val avatar: String?)

object Follows {

    private var prefs: SharedPreferences? = null

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("follows", Context.MODE_PRIVATE)
        prefs = p
        _channels.value = try {
            val arr = JSONArray(p.getString("list", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Channel(
                    name = o.getString("name"),
                    url = o.getString("url"),
                    avatar = o.optString("avatar").ifEmpty { null }
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun isFollowing(url: String): Boolean = _channels.value.any { it.url == url }

    fun toggle(ch: Channel) {
        _channels.value =
            if (isFollowing(ch.url)) _channels.value.filter { it.url != ch.url }
            else _channels.value + ch
        save()
    }

    private fun save() {
        val arr = JSONArray()
        _channels.value.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("avatar", it.avatar ?: "")
            )
        }
        prefs?.edit()?.putString("list", arr.toString())?.apply()
    }
}
