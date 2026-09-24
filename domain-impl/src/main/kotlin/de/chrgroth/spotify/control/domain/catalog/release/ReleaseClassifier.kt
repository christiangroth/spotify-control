package de.chrgroth.spotify.control.domain.catalog.release

import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * Shared "what counts as a new release" heuristic, currently just reissue detection (see #824 for planned
 * improvements). Used both by
 * [de.chrgroth.spotify.control.domain.playlist.check.TrackFromLatestReleaseCheckRunner] (to keep a
 * later reissue/live re-release from outranking the original studio album) and by the automatic "End of
 * the Road" challenger detection in [de.chrgroth.spotify.control.domain.playlist.SingularityService] (to
 * avoid staging a reissue as if it were a new song). Any future change to this heuristic applies to both.
 */
object ReleaseClassifier {

  private val REISSUE_MARKERS = listOf("edition", "live", "deluxe", "remaster", "anniversary", "expanded", "bonus")

  private const val YEAR_ONLY_DATE_LENGTH = 4
  private const val YEAR_MONTH_DATE_LENGTH = 7

  /**
   * Detects reissues (e.g. "Album (Deluxe Edition)", "Album (Live)") among an artist's albums: an album counts
   * as a reissue of another album in [otherAlbums] if its title contains that other album's title plus a
   * reissue marker word.
   */
  fun isReissue(album: AppAlbum, otherAlbums: List<AppAlbum>): Boolean {
    val title = album.title?.lowercase() ?: return false
    return otherAlbums.any { other ->
      other.id != album.id &&
        other.title?.lowercase()?.let { otherTitle -> otherTitle.isNotBlank() && title.contains(otherTitle) } == true &&
        REISSUE_MARKERS.any { marker -> title.contains(marker) }
    }
  }

  /**
   * Parses a Spotify release date ("YYYY", "YYYY-MM" or "YYYY-MM-DD") into the [Instant] at the start of that
   * day (UTC), or `null` if [date] is missing or unparseable.
   */
  fun parseReleaseDate(date: String?): Instant? {
    if (date == null) return null
    val padded = when (date.length) {
      YEAR_ONLY_DATE_LENGTH -> "$date-01-01"
      YEAR_MONTH_DATE_LENGTH -> "$date-01"
      else -> date
    }
    return try {
      LocalDate.parse(padded).atStartOfDayIn(TimeZone.UTC)
    } catch (e: IllegalArgumentException) {
      null
    }
  }
}
