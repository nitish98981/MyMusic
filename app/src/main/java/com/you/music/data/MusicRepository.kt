package com.you.music.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

data class Song(
    val title: String,
    val artist: String,
    val url: String,
    val thumb: String? = null
)

enum class SearchMode { ALL, SONGS, VIDEOS }

/** Queue me jaane wala item. Asli stream URL playback ke time resolve hota hai. */
fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(url)
    .setUri(Uri.parse("yt://resolve?u=" + Uri.encode(url)))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(thumb?.let { Uri.parse(it) })
            .build()
    )
    .build()

object MusicRepository {

    suspend fun search(
        query: String,
        mode: SearchMode = SearchMode.SONGS
    ): List<Song> = withContext(Dispatchers.IO) {
        when (mode) {
            SearchMode.SONGS ->
                searchWith(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)

            SearchMode.VIDEOS ->
                searchWith(query, YoutubeSearchQueryHandlerFactory.VIDEOS)

            SearchMode.ALL -> {
                var error: Throwable? = null
                val videos = try {
                    searchWith(query, YoutubeSearchQueryHandlerFactory.VIDEOS)
                } catch (e: Throwable) {
                    error = e
                    emptyList()
                }
                val songs = try {
                    searchWith(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
                } catch (e: Throwable) {
                    error = e
                    emptyList()
                }
                val err = error
                if (videos.isEmpty() && songs.isEmpty() && err != null) throw err

                // Video aur song baari baari se milaate hain, duplicate hata ke
                val merged = ArrayList<Song>()
                val seen = HashSet<String>()
                var i = 0
                while (i < videos.size || i < songs.size) {
                    if (i < videos.size && seen.add(videos[i].url)) merged.add(videos[i])
                    if (i < songs.size && seen.add(songs[i].url)) merged.add(songs[i])
                    i++
                }
                merged
            }
        }
    }

    private fun searchWith(query: String, filter: String): List<Song> {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter {
                it.streamType != StreamType.LIVE_STREAM &&
                    it.streamType != StreamType.AUDIO_LIVE_STREAM
            }
            .map {
                Song(
                    title = it.name,
                    artist = it.uploaderName ?: "Unknown",
                    url = it.url,
                    thumb = it.thumbnails.firstOrNull()?.url
                )
            }
    }

    suspend fun audioUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        val info = StreamCache.info(videoUrl)
        info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }
            ?.content
            ?: error("Audio stream nahi mila")
    }

    /** Video + audio wali ek file (zyada se zyada 720p), warna sabse chhoti available. "Auto" quality. */
    suspend fun videoUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        val info = StreamCache.info(videoUrl)
        val candidates = info.videoStreams.filter {
            !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val pick = candidates.filter { it.height in 1..720 }.maxByOrNull { it.height }
            ?: candidates.minByOrNull { it.height }
        pick?.content ?: error("Video stream nahi mila")
    }

    /** Sirf H.264 (avc1) mp4 video-only files: ye har phone par chalti hain */
    private fun h264VideoOnly(streams: List<VideoStream>): List<VideoStream> =
        streams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                it.format == MediaFormat.MPEG_4 &&
                it.codec?.startsWith("avc1") == true
        }

    /** HD ke liye: bina awaaz ki video file, height <= maxHeight (audio alag se judti hai) */
    suspend fun videoOnlyUrl(videoUrl: String, maxHeight: Int): String =
        withContext(Dispatchers.IO) {
            val info = StreamCache.info(videoUrl)
            val pool = h264VideoOnly(info.videoOnlyStreams)
            val pick = pool
                .filter { it.height in 1..maxHeight }
                .maxWithOrNull(compareBy<VideoStream>({ it.height }, { it.bitrate }))
                ?: pool.minByOrNull { it.height }
            pick?.content ?: error("HD video stream nahi mila")
        }

    /** Is video ke liye available HD heights, bade se chhote */
    suspend fun videoHeights(videoUrl: String): List<Int> = withContext(Dispatchers.IO) {
        val info = StreamCache.info(videoUrl)
        h264VideoOnly(info.videoOnlyStreams)
            .map { it.height }
            .filter { it >= 144 }
            .distinct()
            .sortedDescending()
    }
}
