package com.you.music.data

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

        val c = controller ?: return
        if (c.mediaItemCount == 0) return

        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        c.stop()
        c.seekTo(index, position)
        c.prepare()
    }
}
