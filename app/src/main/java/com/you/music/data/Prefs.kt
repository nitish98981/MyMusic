package com.you.music.data

import android.content.Context

object Prefs {
    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun sponsorBlock(c: Context): Boolean = p(c).getBoolean("sponsorblock", true)

    fun setSponsorBlock(c: Context, value: Boolean) {
        p(c).edit().putBoolean("sponsorblock", value).apply()
    }

    fun volumeShortcut(c: Context): Boolean = p(c).getBoolean("volume_shortcut", true)

    fun setVolumeShortcut(c: Context, value: Boolean) {
        p(c).edit().putBoolean("volume_shortcut", value).apply()
    }

    fun dataSaver(c: Context): Boolean = p(c).getBoolean("data_saver", false)

    fun setDataSaver(c: Context, value: Boolean) {
        p(c).edit().putBoolean("data_saver", value).apply()
    }

    /** "hindi" ya "english" */
    fun language(c: Context): String = p(c).getString("language", "hindi") ?: "hindi"

    fun languageOrNull(c: Context): String? = p(c).getString("language", null)

    fun setLanguage(c: Context, value: String) {
        p(c).edit().putString("language", value).apply()
    }
}
