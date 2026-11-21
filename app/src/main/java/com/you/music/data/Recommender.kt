package com.you.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

object Recommender {

    private class Cand(val song: Song, val seconds: Long)

    private val spam = listOf("#shorts", "whatsapp status", "ringtone", "reaction")

    private fun isSpam(title: String): Boolean {
        val t = title.lowercase()
        return spam.any { t.contains(it) }
    }

    private fun okDuration(sec: Long, min: Long, max: Long): Boolean =
        sec < 0 || sec in min..max

    /** Ek video ke related videos (YouTube ki apni list), kachra hata ke */
    suspend fun related(
        url: String,
        minSec: Long = 45L,
        maxSec: Long = Long.MAX_VALUE
    ): List<Song> =
        fetch(url)
            .filter { okDuration(it.seconds, minSec, maxSec) }
            .map { it.song }
            .filter { it.url != url }

    private suspend fun fetch(url: String): List<Cand> = withContext(Dispatchers.IO) {
        try {
            val info = StreamCache.info(url)
            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter {
                    it.streamType != StreamType.LIVE_STREAM &&
                        it.streamType != StreamType.AUDIO_LIVE_STREAM
                }
                .filter { !isSpam(it.name) }
                .map {
                    Cand(
                        Song(
                            title = it.name,
                            artist = it.uploaderName ?: "Unknown",
                            url = it.url,
                            thumb = it.thumbnails.firstOrNull()?.url
                        ),
                        it.duration
                    )
                }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Tumhari pasand (Like, Download, Recent) se recommendations.
     * music = true: gaane (1 se 10 min), false: videos (45 sec se 1 ghanta).
     * Kuch bhi history na ho to khali list aati hai.
     */
    suspend fun forYou(ctx: Context, music: Boolean, count: Int = 30): List<Song> {
        Follows.init(ctx)

        val liked = Likes.liked.value
        val downloaded = Downloads.downloads.value + Downloads.videos.value
        val recent = History.recent.value

        // url -> (song, weight)
        val weight = HashMap<String, Pair<Song, Int>>()
        fun add(list: List<Song>, w: Int) {
            list.forEach { s ->
                val old = weight[s.url]
                if (old == null || old.second < w) weight[s.url] = s to w
            }
        }
        add(recent.take(30), 1)
        add(downloaded, 2)
        add(liked, 3)
        if (weight.isEmpty()) return emptyList()

        val seen = HashSet<String>(weight.keys)
        val loved = HashSet<String>()
        (liked + downloaded).forEach { loved.add(it.artist) }
        val followed = Follows.channels.value.map { it.name }.toSet()

        // Weight ke hisaab se random 6 seeds, taaki har baar naya mile
        val pool = weight.values.toMutableList()
        val seeds = ArrayList<Pair<Song, Int>>()
        while (seeds.size < 6 && pool.isNotEmpty()) {
            val total = pool.sumOf { it.second }
            var r = kotlin.random.Random.nextInt(total)
            var idx = 0
            for ((i, p) in pool.withIndex()) {
                r -= p.second
                if (r < 0) {
                    idx = i
                    break
                }
            }
            seeds.add(pool.removeAt(idx))
        }

        val results = coroutineScope {
            seeds.map { s -> async { fetch(s.first.url) } }.awaitAll()
        }

        val score = HashMap<String, Double>()
        val songOf = HashMap<String, Song>()
        results.forEachIndexed { i, list ->
            val w = seeds[i].second
            list.forEachIndexed { rank, c ->
                val ok = if (music) okDuration(c.seconds, 60, 600)
                else okDuration(c.seconds, 45, 3600)
                if (!ok || c.song.url in seen) return@forEachIndexed

                // list me upar wale zyada achhe hote hain
                var s = w.toDouble() * (1.0 - rank / 40.0).coerceAtLeast(0.2)
                if (c.song.artist in loved) s += 2.0
                if (c.song.artist in followed) s += 1.5
                score[c.song.url] = (score[c.song.url] ?: 0.0) + s
                songOf[c.song.url] = c.song
            }
        }

        // Ek artist ke zyada se zyada 3
        val perArtist = HashMap<String, Int>()
        val out = ArrayList<Song>()
        for (e in score.entries.sortedByDescending { it.value }) {
            val s = songOf[e.key] ?: continue
            val n = perArtist[s.artist] ?: 0
            if (n >= 3) continue
            perArtist[s.artist] = n + 1
            out.add(s)
            if (out.size >= count) break
        }
        return out
    }
}
