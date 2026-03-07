package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import de.chrgroth.spotify.control.domain.model.AppPlaybackItem
import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.AppPlaybackRepositoryPort
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import mu.KLogging
import org.bson.Document

@ApplicationScoped
class AppPlaybackRepositoryAdapter : AppPlaybackRepositoryPort {

    @Inject
    lateinit var appPlaybackDocumentRepository: AppPlaybackDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun saveAll(items: List<AppPlaybackItem>) {
        if (items.isEmpty()) return
        val documents = items.map { item ->
            AppPlaybackDocument().apply {
                id = "${item.userId.value}:${item.playedAt.toEpochMilliseconds()}"
                spotifyUserId = item.userId.value
                playedAt = item.playedAt.toJavaInstant()
                trackId = item.trackId
                secondsPlayed = item.secondsPlayed
            }
        }
        logger.info { "Saving ${documents.size} app_playback documents" }
        mongoQueryMetrics.timed("app_playback.saveAll") {
            appPlaybackDocumentRepository.persistOrUpdate(documents)
        }
    }

    override fun deleteAllByUserId(userId: UserId) {
        logger.info { "Deleting all app_playback documents for user: ${userId.value}" }
        mongoQueryMetrics.timed("app_playback.deleteAllByUserId") {
            appPlaybackDocumentRepository.delete("spotifyUserId = ?1", userId.value)
        }
    }

    override fun deleteAllByTrackIds(trackIds: Set<String>) {
        if (trackIds.isEmpty()) return
        logger.info { "Deleting all app_playback documents for ${trackIds.size} track(s)" }
        mongoQueryMetrics.timed("app_playback.deleteAllByTrackIds") {
            appPlaybackDocumentRepository.delete("trackId in ?1", trackIds.toList())
        }
    }

    override fun findMostRecentPlayedAt(userId: UserId): Instant? =
        mongoQueryMetrics.timed("app_playback.findMostRecentPlayedAt") {
            appPlaybackDocumentRepository
                .find("spotifyUserId = ?1", Sort.by("playedAt").descending(), userId.value)
                .firstResult()
                ?.playedAt?.toKotlinInstant()
        }

    override fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant> {
        if (playedAts.isEmpty()) return emptySet()
        val javaPlayedAts = playedAts.map { it.toJavaInstant() }
        return mongoQueryMetrics.timed("app_playback.findExistingPlayedAts") {
            appPlaybackDocumentRepository
                .list("spotifyUserId = ?1 and playedAt in ?2", userId.value, javaPlayedAts)
                .map { it.playedAt.toKotlinInstant() }
                .toSet()
        }
    }

    override fun countAll(userId: UserId): Long =
        mongoQueryMetrics.timed("app_playback.countAll") {
            appPlaybackDocumentRepository.count("spotifyUserId = ?1", userId.value)
        }

    override fun countSince(userId: UserId, since: Instant): Long =
        mongoQueryMetrics.timed("app_playback.countSince") {
            appPlaybackDocumentRepository.count(
                "spotifyUserId = ?1 and playedAt >= ?2",
                userId.value,
                since.toJavaInstant(),
            )
        }

    override fun countPerDaySince(userId: UserId, since: Instant): List<Pair<LocalDate, Long>> {
        val pipeline = listOf(
            Aggregates.match(
                Filters.and(
                    Filters.eq("spotifyUserId", userId.value),
                    Filters.gte("playedAt", since.toJavaInstant()),
                ),
            ),
            Aggregates.group(
                Document("\$dateToString", Document("format", "%Y-%m-%d").append("date", "\$playedAt")),
                Accumulators.sum("count", 1),
            ),
            Aggregates.sort(Sorts.ascending("_id")),
        )
        return mongoQueryMetrics.timed("app_playback.countPerDaySince") {
            appPlaybackDocumentRepository.mongoCollection()
                .aggregate(pipeline, Document::class.java)
                .map { doc ->
                    val dateStr = doc.getString("_id")
                    val count = (doc["count"] as? Int)?.toLong() ?: doc.getLong("count") ?: 0L
                    LocalDate.parse(dateStr) to count
                }
                .toList()
        }
    }

    override fun findRecentlyPlayed(userId: UserId, limit: Int): List<AppPlaybackItem> =
        mongoQueryMetrics.timed("app_playback.findRecentlyPlayed") {
            appPlaybackDocumentRepository
                .find("spotifyUserId = ?1", Sort.by("playedAt").descending(), userId.value)
                .page(0, limit)
                .list()
                .map { doc ->
                    AppPlaybackItem(
                        userId = UserId(doc.spotifyUserId),
                        playedAt = doc.playedAt.toKotlinInstant(),
                        trackId = doc.trackId,
                        secondsPlayed = doc.secondsPlayed,
                    )
                }
        }

    override fun findAllSince(userId: UserId, since: Instant): List<AppPlaybackItem> =
        mongoQueryMetrics.timed("app_playback.findAllSince") {
            appPlaybackDocumentRepository
                .list("spotifyUserId = ?1 and playedAt >= ?2", userId.value, since.toJavaInstant())
                .map { doc ->
                    AppPlaybackItem(
                        userId = UserId(doc.spotifyUserId),
                        playedAt = doc.playedAt.toKotlinInstant(),
                        trackId = doc.trackId,
                        secondsPlayed = doc.secondsPlayed,
                    )
                }
        }

    companion object : KLogging()
}
