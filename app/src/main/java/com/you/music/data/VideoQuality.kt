package com.you.music.data

import android.content.Context

object VideoQuality {
    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** 0 = Auto (simple 360p file), warna maximum height jaise 480, 720, 1080 */
    fun get(c: Context): Int = p(c).getInt("video_quality", 0)

    fun set(c: Context, value: Int) {
        p(c).edit().putInt("video_quality", value).apply()
    }
}
