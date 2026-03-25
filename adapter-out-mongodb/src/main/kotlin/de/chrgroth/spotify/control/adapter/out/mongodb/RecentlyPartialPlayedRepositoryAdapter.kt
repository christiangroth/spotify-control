package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.TrackId
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.RecentlyPartialPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class RecentlyPartialPlayedRepositoryAdapter : RecentlyPartialPlayedRepositoryPort {

    @Inject
    lateinit var recentlyPartialPlayedDocumentRepository: SpotifyRecentlyPartialPlayedDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant> {
        if (playedAts.isEmpty()) return emptySet()
        val javaPlayedAts = playedAts.map { it.toJavaInstant() }
        return mongoQueryMetrics.timed("recently_partial_played.findExistingPlayedAts") {
            recentlyPartialPlayedDocumentRepository
                .list("spotifyUserId = ?1 and playedAt in ?2", userId.value, javaPlayedAts)
                .map { it.playedAt.toKotlinInstant() }
                .toSet()
        }
    }

    override fun findSince(userId: UserId, since: Instant?): List<RecentlyPartialPlayedItem> =
        mongoQueryMetrics.timed("recently_partial_played.findSince") {
            val query = if (since != null) {
                recentlyPartialPlayedDocumentRepository.list(
                    "spotifyUserId = ?1 and playedAt > ?2",
                    userId.value,
                    since.toJavaInstant(),
                )
            } else {
                recentlyPartialPlayedDocumentRepository.list("spotifyUserId = ?1", userId.value)
            }
            query.map { doc ->
                RecentlyPartialPlayedItem(
                    spotifyUserId = UserId(doc.spotifyUserId),
                    trackId = TrackId(doc.trackId),
                    trackName = doc.trackName,
                    artistIds = doc.artistIds.map { ArtistId(it) },
                    artistNames = doc.artistNames,
                    playedAt = doc.playedAt.toKotlinInstant(),
                    playedSeconds = doc.playedSeconds,
                    albumId = doc.albumId?.let { AlbumId(it) },
                )
            }
        }

    override fun saveAll(items: List<RecentlyPartialPlayedItem>) {
        if (items.isEmpty()) return
        val documents = items.map { item ->
            SpotifyRecentlyPartialPlayedDocument().apply {
                spotifyUserId = item.spotifyUserId.value
                trackId = item.trackId.value
                trackName = item.trackName
                artistIds = item.artistIds.map { it.value }
                artistNames = item.artistNames
                playedAt = item.playedAt.toJavaInstant()
                playedSeconds = item.playedSeconds
                albumId = item.albumId?.value
            }
        }
        logger.info { "Saving ${documents.size} recently partial played documents" }
        mongoQueryMetrics.timed("recently_partial_played.saveAll") {
            recentlyPartialPlayedDocumentRepository.persist(documents)
        }
    }

    companion object : KLogging()
}
