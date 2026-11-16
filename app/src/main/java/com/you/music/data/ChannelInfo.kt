package com.you.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Video ke channel ka naam, link aur logo */
suspend fun MusicRepository.channelOf(videoUrl: String): Channel? =
    withContext(Dispatchers.IO) {
        try {
            val info = StreamCache.info(videoUrl)
            val name = info.uploaderName
            val url = info.uploaderUrl
            if (name.isNullOrEmpty() || url.isNullOrEmpty()) {
                null
            } else {
                Channel(name, url, info.uploaderAvatars.firstOrNull()?.url)
            }
        } catch (e: Throwable) {
            null
        }
    }
