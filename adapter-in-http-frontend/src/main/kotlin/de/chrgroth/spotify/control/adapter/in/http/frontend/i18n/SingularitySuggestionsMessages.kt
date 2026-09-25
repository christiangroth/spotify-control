package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the singularity suggestions settings page (`templates/singularity-suggestions.html`).
 * Shared/reusable app-shell strings live in [AppMessages] instead.
 */
@MessageBundle("singularitysuggestions")
interface SingularitySuggestionsMessages {

  @Message
  fun title(): String

  @Message
  fun backToArtistsButton(): String

  @Message
  fun includeSectionTitle(): String

  @Message
  fun includeSectionEmptyState(): String

  @Message("{weeks} week(s) among top artists")
  fun includeTopRankWeeks(weeks: String): String

  @Message
  fun excludeSectionTitle(): String

  @Message
  fun excludeSectionEmptyState(): String

  @Message("Current pick added {date}")
  fun excludeCurrentTrackAddedAt(date: String): String
}
