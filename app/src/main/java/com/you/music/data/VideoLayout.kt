package com.you.music.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object VideoLayout {
    private var inited = false

    // true = bade cards (default), false = 2 column grid
    private val _big = MutableStateFlow(true)
    val bigList: StateFlow<Boolean> = _big

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (inited) return
        inited = true
        _big.value = prefs(c).getBoolean("video_big_list", true)
    }

    fun set(c: Context, big: Boolean) {
        _big.value = big
        prefs(c).edit().putBoolean("video_big_list", big).apply()
    }
}
