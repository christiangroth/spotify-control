package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class RecentlyPlayedRepositoryAdapter(
  private val recentlyPlayedDocumentRepository: RecentlyPlayedDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : RecentlyPlayedRepositoryPort {

  override fun findExistingPlayedAts(playedAts: Set<Instant>): Set<Instant> {
    if (playedAts.isEmpty()) return emptySet()
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    return mongoQueryMetrics.timed("spotify_recently_played.findExistingPlayedAts") {
      recentlyPlayedDocumentRepository
        .list("playedAt in ?1", javaPlayedAts)
        .map { it.playedAt.toKotlinInstant() }
        .toSet()
    }
  }

  override fun findMostRecentPlayedAt(): Instant? =
    mongoQueryMetrics.timed("spotify_recently_played.findMostRecentPlayedAt") {
      recentlyPlayedDocumentRepository
        .findAll(Sort.by("playedAt").descending())
        .firstResult()
        ?.playedAt?.toKotlinInstant()
    }

  override fun findSince(since: Instant?): List<RecentlyPlayedItem> =
    mongoQueryMetrics.timed("spotify_recently_played.findSince") {
      val query = if (since != null) {
        recentlyPlayedDocumentRepository.list("playedAt > ?1", since.toJavaInstant())
      } else {
        recentlyPlayedDocumentRepository.listAll()
      }
      query.map { doc ->
        RecentlyPlayedItem(
          trackId = TrackId(doc.trackId),
          trackName = doc.trackName,
          artistIds = doc.artistIds.map { ArtistId(it) },
          artistNames = doc.artistNames,
          playedAt = doc.playedAt.toKotlinInstant(),
          startTime = doc.startTime?.toKotlinInstant(),
          albumId = doc.albumId?.let { AlbumId(it) },
          durationSeconds = doc.durationSeconds,
        )
      }
    }

  override fun saveAll(items: List<RecentlyPlayedItem>) {
    if (items.isEmpty()) return
    val documents = items.map { item ->
      RecentlyPlayedDocument().apply {
        trackId = item.trackId.value
        trackName = item.trackName
        artistIds = item.artistIds.map { it.value }
        artistNames = item.artistNames
        playedAt = item.playedAt.toJavaInstant()
        startTime = item.startTime?.toJavaInstant()
        albumId = item.albumId?.value
        durationSeconds = item.durationSeconds ?: 0L
      }
    }
    mongoQueryMetrics.timed("spotify_recently_played.saveAll") {
      recentlyPlayedDocumentRepository.persist(documents)
    }
  }

  override fun deleteNonTracks(): Long =
    mongoQueryMetrics.timed("spotify_recently_played.deleteNonTracks") {
      recentlyPlayedDocumentRepository.delete("artistIds = ?1 and artistNames = ?2", emptyList<String>(), emptyList<String>())
    }

}
