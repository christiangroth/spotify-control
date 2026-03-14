package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.model.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.AlbumId
import de.chrgroth.spotify.control.domain.model.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.ArtistId
import de.chrgroth.spotify.control.domain.model.CatalogStats
import de.chrgroth.spotify.control.domain.model.TrackBrowseItem
import de.chrgroth.spotify.control.domain.port.`in`.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.out.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class CatalogBrowserAdapter(
    private val appArtistRepository: AppArtistRepositoryPort,
    private val appAlbumRepository: AppAlbumRepositoryPort,
    private val appTrackRepository: AppTrackRepositoryPort,
) : CatalogBrowserPort {

    override fun getCatalogStats(): CatalogStats {
        val artists = appArtistRepository.findAll()
        val albumCount = appAlbumRepository.findAll().size.toLong()
        val trackCount = appTrackRepository.findAll().size.toLong()
        val genreCount = artists
            .flatMap { listOfNotNull(it.genre) + (it.additionalGenres ?: emptyList()) }
            .toSet()
            .size
            .toLong()
        return CatalogStats(
            artistCount = artists.size.toLong(),
            albumCount = albumCount,
            trackCount = trackCount,
            genreCount = genreCount,
        )
    }

    override fun getArtists(filter: String?): List<ArtistBrowseItem> {
        val artists = appArtistRepository.findAll()
        val allAlbums = appAlbumRepository.findAll()
        val allTracks = appTrackRepository.findAll()

        val albumCountByArtistId = allAlbums.groupingBy { it.artistId?.value }.eachCount()
        val trackCountByArtistId = allTracks.groupingBy { it.artistId.value }.eachCount()

        val filterLower = filter?.trim()?.lowercase()
        return artists
            .filter { artist ->
                filterLower.isNullOrBlank() ||
                    artist.artistName.lowercase().contains(filterLower) ||
                    listOfNotNull(artist.genre).any { it.lowercase().contains(filterLower) } ||
                    (artist.additionalGenres ?: emptyList()).any { it.lowercase().contains(filterLower) }
            }
            .sortedBy { it.artistName.lowercase() }
            .map { artist ->
                val genres = listOfNotNull(artist.genre) + (artist.additionalGenres ?: emptyList())
                ArtistBrowseItem(
                    artistId = artist.artistId,
                    artistName = artist.artistName,
                    imageLink = artist.imageLink,
                    genres = genres,
                    genresDisplay = genres.joinToString(", "),
                    albumCount = albumCountByArtistId[artist.artistId] ?: 0,
                    trackCount = trackCountByArtistId[artist.artistId] ?: 0,
                )
            }
    }

    override fun getArtistAlbums(artistId: String): List<AlbumBrowseItem> {
        val albums = appAlbumRepository.findByArtistId(ArtistId(artistId))
        val tracks = appTrackRepository.findByArtistId(ArtistId(artistId))
        val tracksByAlbumId = tracks.groupBy { it.albumId?.value }

        return albums
            .sortedWith(compareByDescending { it.releaseDate })
            .map { album ->
                val albumTracks = tracksByAlbumId[album.id.value] ?: emptyList()
                val totalDurationMs = albumTracks.sumOf { it.durationMs ?: 0L }
                AlbumBrowseItem(
                    albumId = album.id.value,
                    title = album.title,
                    imageLink = album.imageLink,
                    releaseDate = album.releaseDate,
                    trackCount = albumTracks.size,
                    durationFormatted = formatDurationHourMin(totalDurationMs),
                )
            }
    }

    override fun getAlbumTracks(albumId: String): List<TrackBrowseItem> {
        val tracks = appTrackRepository.findByAlbumId(AlbumId(albumId))
        return tracks
            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
            .map { track ->
                TrackBrowseItem(
                    trackId = track.id.value,
                    trackNumber = track.trackNumber,
                    discNumber = track.discNumber,
                    title = track.title,
                    durationFormatted = formatDurationMinSec(track.durationMs ?: 0L),
                )
            }
    }

    companion object {
        private const val MS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3600L

        private fun formatDurationHourMin(durationMs: Long): String {
            val totalSeconds = durationMs / MS_PER_SECOND
            val hours = totalSeconds / SECONDS_PER_HOUR
            val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            return "%d:%02d".format(hours, minutes)
        }

        private fun formatDurationMinSec(durationMs: Long): String {
            val totalSeconds = durationMs / MS_PER_SECOND
            val minutes = totalSeconds / SECONDS_PER_MINUTE
            val seconds = totalSeconds % SECONDS_PER_MINUTE
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
