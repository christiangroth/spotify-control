package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the playback pages (`templates/playback.html`, `templates/playback-event-viewer.html`) and the
 * playback/artist settings endpoints they call. Shared/reusable app-shell strings live in [AppMessages] instead.
 */
@MessageBundle("playback")
interface PlaybackMessages {

  @Message
  fun playbackTitle(): String

  // stats cards
  @Message
  fun playbackStatsEventsLast30DaysLabel(): String

  @Message
  fun playbackStatsTotalEventsLabel(): String

  // playback data rebuild
  @Message
  fun playbackDataHeading(): String

  @Message
  fun playbackDataDescription(): String

  @Message
  fun playbackDataRebuildButton(): String

  @Message
  fun playbackDataRebuildSuccess(): String

  @Message
  fun playbackDataRebuildErrorPrefix(): String

  // missing artists sync
  @Message
  fun playbackMissingArtistsHeading(): String

  @Message
  fun playbackMissingArtistsDescription(): String

  @Message
  fun playbackMissingArtistsSyncButton(): String

  @Message
  fun playbackMissingArtistsSyncSuccess(): String

  @Message
  fun playbackMissingArtistsSyncErrorPrefix(): String

  // playback polling
  @Message
  fun playbackPollingHeading(): String

  @Message
  fun playbackPollingDescription(): String

  @Message
  fun playbackPollingButton(): String

  @Message
  fun playbackPollingSuccess(): String

  @Message
  fun playbackPollingErrorPrefix(): String

  // playback event viewer
  @Message
  fun playbackEventsTitle(): String

  @Message
  fun playbackEventsPrevButton(): String

  @Message
  fun playbackEventsNextButton(): String

  @Message("No playback events found for {date}.")
  fun playbackEventsEmpty(date: String): String

  @Message("{count} event(s) on {date}")
  fun playbackEventsSummary(count: String, date: String): String

  @Message
  fun playbackEventTypeRecentlyPlayed(): String

  @Message
  fun playbackEventTypeRecentlyPartialPlayed(): String

  @Message
  fun playbackEventTypeCurrentlyPlaying(): String

  @Message
  fun playbackEventOrphanedBadge(): String

  @Message
  fun playbackEventActiveBadge(): String

  // artist settings errors (surfaced via /settings/playback/artists/* JSON responses)
  @Message("Artist not found: {artistId}")
  fun playbackErrorArtistNotFound(artistId: String): String

  @Message("Re-sync failed: {errorCode}")
  fun playbackErrorArtistResyncFailed(errorCode: String): String

  @Message("Set-sync failed: {errorCode}")
  fun playbackErrorArtistSetSyncFailed(errorCode: String): String

  @Message("Set-shallow failed: {errorCode}")
  fun playbackErrorArtistSetShallowFailed(errorCode: String): String
}
