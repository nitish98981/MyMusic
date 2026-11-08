package com.you.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

/** Ek hi gaane ke liye video aur audio dono ko ek hi baar extract karta hai (5 minute yaad rakhta hai) */
object StreamCache {

    private class Entry(val info: StreamInfo, val at: Long)

    private const val TTL = 5 * 60 * 1000L
    private val map = ConcurrentHashMap<String, Entry>()
    private val lock = Any()

    suspend fun info(url: String): StreamInfo = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val hit = map[url]
            if (hit != null && now - hit.at < TTL) {
                hit.info
            } else {
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                map[url] = Entry(info, now)
                if (map.size > 30) {
                    map.entries.removeIf { now - it.value.at > TTL }
                }
                info
            }
        }
    }
}
