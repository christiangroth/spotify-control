package de.chrgroth.spotify.control.domain.model

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private const val PLAYED_AT_DATE_PATTERN = "yyyy-MM-dd HH:mm"
private const val DURATION_FORMAT = "%d:%02d"
private val PLAYED_AT_FORMATTER = DateTimeFormatter.ofPattern(PLAYED_AT_DATE_PATTERN).withZone(ZoneOffset.UTC)
private const val SECONDS_PER_MINUTE = 60L

data class RecentlyPlayedItem(
    val spotifyUserId: UserId,
    val trackId: TrackId,
    val trackName: String,
    val artistIds: List<ArtistId>,
    val artistNames: List<String>,
    val playedAt: Instant,
    val albumId: AlbumId? = null,
    val albumName: String? = null,
    val imageUrl: String? = null,
    val durationSeconds: Long? = null,
) {
    val playedAtFormatted: String get() = PLAYED_AT_FORMATTER.format(playedAt.toJavaInstant())
    val durationFormatted: String? get() = durationSeconds?.let {
        val minutes = it / SECONDS_PER_MINUTE
        val seconds = it % SECONDS_PER_MINUTE
        DURATION_FORMAT.format(minutes, seconds)
    }
}
