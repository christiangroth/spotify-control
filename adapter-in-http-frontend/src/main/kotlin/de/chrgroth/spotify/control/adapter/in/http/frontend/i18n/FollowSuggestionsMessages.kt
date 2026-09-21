package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the follow suggestions settings page (`templates/settings/follow-suggestions.html`).
 * Shared/reusable app-shell strings live in [AppMessages] instead.
 */
@MessageBundle("followsuggestions")
interface FollowSuggestionsMessages {

  @Message
  fun title(): String

  @Message
  fun backToFollowingButton(): String

  @Message
  fun followSectionTitle(): String

  @Message
  fun followSectionEmptyState(): String

  @Message("{weeks} week(s) among top artists")
  fun followTopRankWeeks(weeks: String): String

  @Message
  fun unfollowSectionTitle(): String

  @Message
  fun unfollowSectionEmptyState(): String

  @Message("Followed since {date}")
  fun unfollowFollowedSince(date: String): String
}
