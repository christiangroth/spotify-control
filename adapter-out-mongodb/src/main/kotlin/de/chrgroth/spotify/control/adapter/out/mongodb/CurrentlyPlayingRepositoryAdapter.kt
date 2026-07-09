package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.port.out.playback.CurrentlyPlayingRepositoryPort
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@ApplicationScoped
class CurrentlyPlayingRepositoryAdapter(
  private val currentlyPlayingDocumentRepository: CurrentlyPlayingDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : CurrentlyPlayingRepositoryPort {

  override fun save(item: CurrentlyPlayingItem) {
    val document = CurrentlyPlayingDocument().apply {
      trackId = item.trackId.value
      trackName = item.trackName
      artistIds = item.artistIds.map { it.value }
      artistNames = item.artistNames
      progressMs = item.progressMs
      durationMs = item.durationMs
      isPlaying = item.isPlaying
      observedAt = item.observedAt.toJavaInstant()
      startTime = item.startTime.toJavaInstant()
      albumId = item.albumId?.value
    }
    mongoQueryMetrics.timed("spotify_currently_playing.save") {
      currentlyPlayingDocumentRepository.persist(document)
    }
  }

  override fun findMostRecentByTrack(trackId: TrackId): CurrentlyPlayingItem? =
    mongoQueryMetrics.timed("spotify_currently_playing.findMostRecentByTrack") {
      currentlyPlayingDocumentRepository
        .find("trackId = ?1", Sort.by(OBSERVED_AT_FIELD).descending(), trackId.value)
        .firstResult()
        ?.toItem()
    }

  override fun updateProgress(item: CurrentlyPlayingItem) {
    mongoQueryMetrics.timed("spotify_currently_playing.updateProgress") {
      currentlyPlayingDocumentRepository.mongoCollection().findOneAndUpdate(
        Filters.eq(TRACK_ID_FIELD, item.trackId.value),
        Updates.combine(
          Updates.set(PROGRESS_MS_FIELD, item.progressMs),
          Updates.set(IS_PLAYING_FIELD, item.isPlaying),
          Updates.set(OBSERVED_AT_FIELD, item.observedAt.toJavaInstant()),
          Updates.set(START_TIME_FIELD, item.startTime.toJavaInstant()),
        ),
        FindOneAndUpdateOptions().sort(Sorts.descending(OBSERVED_AT_FIELD)),
      )
    }
  }

  override fun findAll(): List<CurrentlyPlayingItem> =
    mongoQueryMetrics.timed("spotify_currently_playing.findAll") {
      currentlyPlayingDocumentRepository
        .listAll()
        .map { doc -> doc.toItem() }
    }

  override fun deleteByTrackIds(trackIds: Set<String>) {
    if (trackIds.isEmpty()) return
    mongoQueryMetrics.timed("spotify_currently_playing.deleteByTrackIds") {
      currentlyPlayingDocumentRepository.delete("trackId in ?1", trackIds.toList())
    }
  }

  private fun CurrentlyPlayingDocument.toItem() = CurrentlyPlayingItem(
    trackId = TrackId(trackId),
    trackName = trackName,
    artistIds = artistIds.map { ArtistId(it) },
    artistNames = artistNames,
    progressMs = progressMs,
    durationMs = durationMs,
    isPlaying = isPlaying,
    observedAt = observedAt.toKotlinInstant(),
    startTime = startTime.toKotlinInstant(),
    albumId = albumId?.let { AlbumId(it) },
  )

  companion object {
    internal const val TRACK_ID_FIELD = "trackId"
    internal const val OBSERVED_AT_FIELD = "observedAt"
    internal const val START_TIME_FIELD = "startTime"
    internal const val PROGRESS_MS_FIELD = "progressMs"
    internal const val IS_PLAYING_FIELD = "isPlaying"
  }
}
