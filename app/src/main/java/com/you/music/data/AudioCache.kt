package com.you.music.data

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.you.music.data.remote.NewPipeDownloader
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

object AudioCache {

    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    private val db by lazy { StandaloneDatabaseProvider(app) }

    /** Jo gaane chale hain (audio + video): 512 MB, purane apne aap hat jate hain */
    val streamCache by lazy {
        SimpleCache(
            File(app.cacheDir, "stream"),
            LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
            db
        )
    }

    /** Jo gaane download kiye hain: kabhi apne aap delete nahi hote */
    val downloadCache by lazy {
        SimpleCache(File(app.filesDir, "downloads"), NoOpCacheEvictor(), db)
    }

    /** URI me m=audio ya m=video ho to wahi, warna abhi ka PlaybackMode */
    private fun wantsVideo(uri: Uri): Boolean {
        val m = uri.getQueryParameter("m")
        return if (m != null) m == "video" else PlaybackMode.video.value
    }

    // Stream URL expire hota hai, isliye cache key gaane ka apna YouTube URL rakhte hain.
    // Video ka key alag ("video:" ke saath), taaki audio wale se na mile.
    private val keyFactory = CacheKeyFactory { spec ->
        spec.key ?: run {
            val u = spec.uri.getQueryParameter("u")
            when {
                u == null -> spec.uri.toString()
                wantsVideo(spec.uri) -> "video:$u"
                else -> u
            }
        }
    }

    private fun resolvingHttp(): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        return ResolvingDataSource.Factory(
            http,
            ResolvingDataSource.Resolver { spec ->
                val videoUrl = spec.uri.getQueryParameter("u")
                if (spec.uri.scheme == "yt" && videoUrl != null) {
                    try {
                        val wantVideo = wantsVideo(spec.uri)
                        val real = runBlocking {
                            if (wantVideo) MusicRepository.videoUrl(videoUrl)
                            else MusicRepository.audioUrl(videoUrl)
                        }
                        spec.withUri(Uri.parse(real))
                    } catch (e: Exception) {
                        throw IOException(e.message ?: "resolve failed", e)
                    }
                } else {
                    spec
                }
            }
        )
    }

    /** Player ke liye: pehle downloads dekho, phir stream cache, phir internet */
    fun playbackFactory(): DataSource.Factory {
        val streaming = CacheDataSource.Factory()
            .setCache(streamCache)
            .setCacheKeyFactory(keyFactory)
            .setUpstreamDataSourceFactory(resolvingHttp())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheKeyFactory(keyFactory)
            .setCacheWriteDataSinkFactory(null) // yahan sirf padhna, likhna nahi
            .setUpstreamDataSourceFactory(streaming)
    }

    /** Download ke liye: seedha downloads cache me likhta hai */
    fun downloadSource(): CacheDataSource =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheKeyFactory(keyFactory)
            .setUpstreamDataSourceFactory(resolvingHttp())
            .createDataSource()
}
