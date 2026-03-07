package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.UserId

interface PlaybackDataPort {
    fun enqueueRebuild(userId: UserId)
    fun rebuildPlaybackData(userId: UserId)
    fun appendPlaybackData(userId: UserId)
}
