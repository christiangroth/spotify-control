package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import de.chrgroth.spotify.control.domain.model.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.RecentlyPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import mu.KLogging
import org.bson.Document

@ApplicationScoped
class RecentlyPlayedRepositoryAdapter : RecentlyPlayedRepositoryPort {

    @Inject
    lateinit var recentlyPlayedDocumentRepository: RecentlyPlayedDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun findExistingPlayedAts(spotifyUserId: UserId, playedAts: Set<Instant>): Set<Instant> {
        if (playedAts.isEmpty()) return emptySet()
        val javaPlayedAts = playedAts.map { it.toJavaInstant() }
        return mongoQueryMetrics.timed("recently_played.findExistingPlayedAts") {
            recentlyPlayedDocumentRepository
                .list("spotifyUserId = ?1 and playedAt in ?2", spotifyUserId.value, javaPlayedAts)
                .map { it.playedAt.toKotlinInstant() }
                .toSet()
        }
    }

    override fun saveAll(items: List<RecentlyPlayedItem>) {
        if (items.isEmpty()) return
        val documents = items.map { item ->
            RecentlyPlayedDocument().apply {
                spotifyUserId = item.spotifyUserId.value
                trackId = item.trackId
                trackName = item.trackName
                artistIds = item.artistIds
                artistNames = item.artistNames
                playedAt = item.playedAt.toJavaInstant()
            }
        }
        logger.info { "Saving ${documents.size} recently played documents" }
        mongoQueryMetrics.timed("recently_played.saveAll") {
            recentlyPlayedDocumentRepository.persist(documents)
        }
    }

    override fun countAll(spotifyUserId: UserId): Long =
        mongoQueryMetrics.timed("recently_played.countAll") {
            recentlyPlayedDocumentRepository.count("spotifyUserId = ?1", spotifyUserId.value)
        }

    override fun countSince(spotifyUserId: UserId, since: Instant): Long =
        mongoQueryMetrics.timed("recently_played.countSince") {
            recentlyPlayedDocumentRepository.count(
                "spotifyUserId = ?1 and playedAt >= ?2",
                spotifyUserId.value,
                since.toJavaInstant(),
            )
        }

    override fun countPerDaySince(spotifyUserId: UserId, since: Instant): List<Pair<LocalDate, Long>> {
        val pipeline = listOf(
            Aggregates.match(
                Filters.and(
                    Filters.eq("spotifyUserId", spotifyUserId.value),
                    Filters.gte("playedAt", since.toJavaInstant()),
                ),
            ),
            Aggregates.group(
                Document("\$dateToString", Document("format", "%Y-%m-%d").append("date", "\$playedAt")),
                Accumulators.sum("count", 1),
            ),
            Aggregates.sort(Sorts.ascending("_id")),
        )
        return mongoQueryMetrics.timed("recently_played.countPerDaySince") {
            recentlyPlayedDocumentRepository.mongoCollection()
                .aggregate(pipeline, Document::class.java)
                .map { doc ->
                    val dateStr = doc.getString("_id")
                    val count = (doc["count"] as? Int)?.toLong() ?: doc.getLong("count") ?: 0L
                    LocalDate.parse(dateStr) to count
                }
                .toList()
        }
    }

    companion object : KLogging()
}
