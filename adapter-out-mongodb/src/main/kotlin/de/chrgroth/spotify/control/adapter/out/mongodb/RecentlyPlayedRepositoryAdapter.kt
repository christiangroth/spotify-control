package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class RecentlyPlayedRepositoryAdapter : RecentlyPlayedRepositoryPort {

    @Inject
    lateinit var recentlyPlayedDocumentRepository: SpotifyRecentlyPlayedDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun findExistingPlayedAts(spotifyUserId: UserId, playedAts: Set<Instant>): Set<Instant> {
        if (playedAts.isEmpty()) return emptySet()
        val javaPlayedAts = playedAts.map { it.toJavaInstant() }
        return mongoQueryMetrics.timedWithFallback("spotify_recently_played.findExistingPlayedAts", emptySet()) {
            recentlyPlayedDocumentRepository
                .list("spotifyUserId = ?1 and playedAt in ?2", spotifyUserId.value, javaPlayedAts)
                .map { it.playedAt.toKotlinInstant() }
                .toSet()
        }
    }

    override fun findMostRecentPlayedAt(spotifyUserId: UserId): Instant? =
        mongoQueryMetrics.timedWithFallback("spotify_recently_played.findMostRecentPlayedAt", null) {
            recentlyPlayedDocumentRepository
                .find("spotifyUserId = ?1", Sort.by("playedAt").descending(), spotifyUserId.value)
                .firstResult()
                ?.playedAt?.toKotlinInstant()
        }

    override fun findSince(spotifyUserId: UserId, since: Instant?): List<RecentlyPlayedItem> =
        mongoQueryMetrics.timedWithFallback("spotify_recently_played.findSince", emptyList()) {
            val query = if (since != null) {
                recentlyPlayedDocumentRepository.list(
                    "spotifyUserId = ?1 and playedAt > ?2",
                    spotifyUserId.value,
                    since.toJavaInstant(),
                )
            } else {
                recentlyPlayedDocumentRepository.list("spotifyUserId = ?1", spotifyUserId.value)
            }
            query.map { doc ->
                RecentlyPlayedItem(
                    spotifyUserId = UserId(doc.spotifyUserId),
                    trackId = doc.trackId,
                    trackName = doc.trackName,
                    artistIds = doc.artistIds,
                    artistNames = doc.artistNames,
                    playedAt = doc.playedAt.toKotlinInstant(),
                    durationSeconds = doc.durationSeconds,
                )
            }
        }

    override fun saveAll(items: List<RecentlyPlayedItem>) {
        if (items.isEmpty()) return
        val documents = items.map { item ->
            SpotifyRecentlyPlayedDocument().apply {
                spotifyUserId = item.spotifyUserId.value
                trackId = item.trackId
                trackName = item.trackName
                artistIds = item.artistIds
                artistNames = item.artistNames
                playedAt = item.playedAt.toJavaInstant()
                durationSeconds = item.durationSeconds ?: 0L
            }
        }
        logger.info { "Saving ${documents.size} recently played documents" }
        mongoQueryMetrics.timedWithFallback("spotify_recently_played.saveAll", Unit) {
            recentlyPlayedDocumentRepository.persist(documents)
        }
    }

    override fun deleteNonTracks(): Long =
        mongoQueryMetrics.timedWithFallback("spotify_recently_played.deleteNonTracks", 0L) {
            recentlyPlayedDocumentRepository.delete("artistIds = ?1 and artistNames = ?2", emptyList<String>(), emptyList<String>())
        }

    companion object : KLogging()
}

