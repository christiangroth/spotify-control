package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the currently-followed-artists page (`templates/following.html`).
 * Shared/reusable app-shell strings live in [AppMessages] instead.
 */
@MessageBundle("following")
interface FollowingMessages {

  @Message
  fun title(): String

  @Message
  fun suggestionsLinkLabel(): String

  @Message("Followed artist data last synced {date}")
  fun lastSyncLabel(date: String): String

  @Message
  fun lastSyncNeverLabel(): String

  @Message
  fun emptyState(): String

  @Message
  fun tableArtistHeader(): String

  @Message
  fun tableFollowedSinceHeader(): String

  @Message
  fun artistImagePlaceholderAriaLabel(): String
}
