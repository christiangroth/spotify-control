package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class RecentlyPlayedRepositoryAdapter : RecentlyPlayedRepositoryPort {

    override fun findExistingPlayedAts(spotifyUserId: UserId, playedAts: Set<Instant>): Set<Instant> {
        if (playedAts.isEmpty()) return emptySet()
        val javaPlayedAts = playedAts.map { it.toJavaInstant() }
        return RecentlyPlayedDocument
            .list("spotifyUserId = ?1 and playedAt in ?2", spotifyUserId.value, javaPlayedAts)
            .map { it.playedAt.toKotlinInstant() }
            .toSet()
    }

    override fun saveAll(items: List<RecentlyPlayedItem>) {
        val documents = items.map { item ->
            RecentlyPlayedDocument().apply {
                spotifyUserId = item.spotifyUserId.value
                trackId = item.trackId
                trackName = item.trackName
                artistNames = item.artistNames
                playedAt = item.playedAt.toJavaInstant()
            }
        }
        logger.info { "Saving ${documents.size} recently played documents" }
        RecentlyPlayedDocument.persist(documents)
    }

    companion object : KLogging()
}
