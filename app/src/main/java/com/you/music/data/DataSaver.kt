package com.you.music.data

import android.content.Context

object DataSaver {
    @Volatile
    var enabled: Boolean = false
        private set

    fun init(c: Context) {
        enabled = Prefs.dataSaver(c)
    }

    fun set(c: Context, value: Boolean) {
        Prefs.setDataSaver(c, value)
        enabled = value
    }
}
