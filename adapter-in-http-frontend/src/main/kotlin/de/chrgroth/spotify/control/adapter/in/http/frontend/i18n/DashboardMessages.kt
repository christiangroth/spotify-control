package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the dashboard page (`templates/dashboard.html`). Shared/reusable app-shell strings live in
 * [AppMessages] instead.
 */
@MessageBundle("dashboard")
interface DashboardMessages {

  @Message
  fun dashboardTitle(): String

  @Message("Hej {displayName}!")
  fun dashboardWelcomeMessage(displayName: String): String

  @Message
  fun dashboardRefreshProfileLabel(): String

  // playback histogram
  @Message
  fun dashboardPlaybackHistogramTitle(): String

  @Message("{count} events on {date}")
  fun dashboardHistogramBarTooltip(count: String, date: String): String

  // recently played
  @Message
  fun dashboardRecentlyPlayedTitle(): String

  @Message
  fun dashboardRecentlyPlayedEmpty(): String

  // listening stats
  @Message
  fun dashboardListeningStatsTitle(): String

  @Message
  fun dashboardListenedLabel(): String

  @Message
  fun dashboardTopTracksTitle(): String

  @Message
  fun dashboardTopArtistsTitle(): String

  @Message
  fun dashboardTopAlbumsTitle(): String

  @Message
  fun dashboardStatsEmpty(): String

  // profile refresh feedback
  @Message
  fun dashboardProfileRefreshSuccess(): String

  @Message
  fun dashboardProfileRefreshErrorPrefix(): String
}
