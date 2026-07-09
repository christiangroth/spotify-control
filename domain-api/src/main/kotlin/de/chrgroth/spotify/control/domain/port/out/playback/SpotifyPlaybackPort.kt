package de.chrgroth.spotify.control.domain.port.out.playback

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import kotlin.time.Instant

interface SpotifyPlaybackPort {
  fun getCurrentlyPlaying(accessToken: AccessToken): Either<DomainError, CurrentlyPlayingItem?>
  fun getRecentlyPlayed(accessToken: AccessToken, after: Instant? = null): Either<DomainError, List<RecentlyPlayedItem>>
}
