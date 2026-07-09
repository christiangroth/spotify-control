package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class RecentlyPartialPlayedRepositoryAdapter(
  private val recentlyPartialPlayedDocumentRepository: RecentlyPartialPlayedDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : RecentlyPartialPlayedRepositoryPort {

  override fun findExistingPlayedAts(playedAts: Set<Instant>): Set<Instant> {
    if (playedAts.isEmpty()) return emptySet()
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    return mongoQueryMetrics.timed("recently_partial_played.findExistingPlayedAts") {
      recentlyPartialPlayedDocumentRepository
        .list("playedAt in ?1", javaPlayedAts)
        .map { it.playedAt.toKotlinInstant() }
        .toSet()
    }
  }

  override fun findSince(since: Instant?): List<RecentlyPartialPlayedItem> =
    mongoQueryMetrics.timed("recently_partial_played.findSince") {
      val query = if (since != null) {
        recentlyPartialPlayedDocumentRepository.list("playedAt > ?1", since.toJavaInstant())
      } else {
        recentlyPartialPlayedDocumentRepository.listAll()
      }
      query.map { doc -> doc.toItem() }
    }

  override fun findByTrackIds(trackIds: Set<TrackId>): List<RecentlyPartialPlayedItem> {
    if (trackIds.isEmpty()) return emptyList()
    val trackIdValues = trackIds.map { it.value }
    return mongoQueryMetrics.timed("recently_partial_played.findByTrackIds") {
      recentlyPartialPlayedDocumentRepository
        .list("trackId in ?1", trackIdValues)
        .map { doc -> doc.toItem() }
    }
  }

  override fun saveAll(items: List<RecentlyPartialPlayedItem>) {
    if (items.isEmpty()) return
    val documents = items.map { item ->
      RecentlyPartialPlayedDocument().apply {
        trackId = item.trackId.value
        trackName = item.trackName
        artistIds = item.artistIds.map { it.value }
        artistNames = item.artistNames
        playedAt = item.playedAt.toJavaInstant()
        startTime = item.startTime.toJavaInstant()
        playedSeconds = item.playedSeconds
        albumId = item.albumId?.value
      }
    }
    mongoQueryMetrics.timed("recently_partial_played.saveAll") {
      recentlyPartialPlayedDocumentRepository.persist(documents)
    }
  }

  override fun deleteByPlayedAts(playedAts: Set<Instant>) {
    if (playedAts.isEmpty()) return
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    mongoQueryMetrics.timed("recently_partial_played.deleteByPlayedAts") {
      recentlyPartialPlayedDocumentRepository.delete("playedAt in ?1", javaPlayedAts)
    }
  }

  private fun RecentlyPartialPlayedDocument.toItem() = RecentlyPartialPlayedItem(
    trackId = TrackId(trackId),
    trackName = trackName,
    artistIds = artistIds.map { ArtistId(it) },
    artistNames = artistNames,
    playedAt = playedAt.toKotlinInstant(),
    startTime = startTime.toKotlinInstant(),
    playedSeconds = playedSeconds,
    albumId = albumId?.let { AlbumId(it) },
  )

}
