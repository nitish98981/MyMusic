package com.you.music.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.you.music.data.PlaybackMode
import com.you.music.data.VideoQuality

/**
 * Normal gaane / Auto video: seedhi ek file.
 * HD quality (720p wagairah): video-only file + audio file ko jodta hai.
 */
class YtMediaSourceFactory(
    private val context: Context,
    dataSourceFactory: DataSource.Factory
) : MediaSource.Factory {

    private val default = DefaultMediaSourceFactory(dataSourceFactory)
    private val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider
    ): MediaSource.Factory {
        default.setDrmSessionManagerProvider(drmSessionManagerProvider)
        progressive.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy
    ): MediaSource.Factory {
        default.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        progressive.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = default.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        val quality = VideoQuality.get(context)

        if (uri != null &&
            uri.scheme == "yt" &&
            uri.getQueryParameter("m") == null &&
            PlaybackMode.video.value &&
            quality > 0
        ) {
            val u = uri.getQueryParameter("u")
            if (u != null) {
                val encoded = Uri.encode(u)

                // Video wale item me gaane ka naam/mediaId waisa hi rakhte hain,
                // taaki notification aur likes sahi chalein
                val videoItem = mediaItem.buildUpon()
                    .setUri(Uri.parse("yt://resolve?m=vo&q=$quality&u=$encoded"))
                    .build()
                val audioItem = MediaItem.Builder()
                    .setUri(Uri.parse("yt://resolve?m=audio&u=$encoded"))
                    .build()

                return MergingMediaSource(
                    progressive.createMediaSource(videoItem),
                    progressive.createMediaSource(audioItem)
                )
            }
        }
        return default.createMediaSource(mediaItem)
    }
}
