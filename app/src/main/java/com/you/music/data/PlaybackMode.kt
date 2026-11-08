package com.you.music.data

import android.net.Uri
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Song (sirf audio) ya Video mode. App khulte hi hamesha Song mode se shuru hota hai. */
object PlaybackMode {

    private val _video = MutableStateFlow(false)
    val video: StateFlow<Boolean> = _video

    /** Mode badalta hai aur chalta hua gaana usi jagah se dobara load karta hai */
    fun set(controller: MediaController?, video: Boolean) {
        if (_video.value == video) return
        _video.value = video
        reload(controller)
    }

    /** Queue ko naye mode/quality ke saath dobara banata hai, usi gaane aur usi jagah se */
    fun reload(controller: MediaController?) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return

        val items = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            item.buildUpon()
                .setUri(Uri.parse("yt://resolve?u=" + Uri.encode(item.mediaId)))
                .build()
        }
        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        c.setMediaItems(items, index, position)
        c.prepare()
    }
}
