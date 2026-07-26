package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the playlists domain (`templates/settings/playlist.html`, `templates/playlist-checks.html`) and
 * the playlist settings/checks endpoints they call. Shared/reusable app-shell strings live in [AppMessages] instead.
 */
@MessageBundle("playlists")
interface PlaylistsMessages {

  // settings/playlist.html
  @Message
  fun settingsTitle(): String

  @Message
  fun settingsSyncNowButton(): String

  @Message
  fun settingsSyncNowSuccess(): String

  @Message
  fun settingsSyncNowErrorPrefix(): String

  @Message
  fun settingsEmptyState(): String

  @Message
  fun settingsTableIndexHeader(): String

  @Message
  fun settingsTableSyncHeader(): String

  @Message
  fun settingsTableNameHeader(): String

  @Message
  fun settingsTableTracksHeader(): String

  @Message
  fun settingsTableArtistsHeader(): String

  @Message
  fun settingsTableLastSyncHeader(): String

  @Message
  fun settingsEmptyValuePlaceholder(): String

  @Message
  fun settingsToggleSyncTooltip(): String

  @Message("Toggle sync status for {name}")
  fun settingsToggleSyncAriaLabel(name: String): String

  @Message
  fun settingsSyncPlaylistTooltip(): String

  @Message("Sync playlist {name}")
  fun settingsSyncPlaylistAriaLabel(name: String): String

  @Message
  fun settingsSyncStatusUpdatedSuccess(): String

  @Message
  fun settingsSyncStatusUpdateErrorPrefix(): String

  @Message
  fun settingsSyncPlaylistSuccess(): String

  @Message
  fun settingsSyncPlaylistErrorPrefix(): String

  // playlist settings errors (surfaced via /settings/playlist/* JSON responses)
  @Message("Invalid sync status: {syncStatus}")
  fun settingsErrorInvalidSyncStatus(syncStatus: String): String

  @Message
  fun settingsErrorPlaylistNotFound(): String

  @Message("Invalid playlist type: {type}")
  fun settingsErrorInvalidPlaylistType(type: String): String

  @Message
  fun settingsErrorPlaylistTypeConflict(): String

  @Message
  fun settingsErrorPlaylistTypeInactive(): String

  @Message("Sync enqueue failed: {errorCode}")
  fun settingsErrorSyncEnqueueFailed(errorCode: String): String

  // playlist-checks.html
  @Message
  fun checksTitle(): String

  @Message
  fun checksTriggerAllLabel(): String

  @Message
  fun checksTriggerAllSuccess(): String

  @Message
  fun checksTriggerErrorPrefix(): String

  @Message
  fun checksEmptyState(): String

  @Message("Run {checkName} check")
  fun checksTriggerGroupLabel(checkName: String): String

  @Message
  fun checksTriggerGroupSuccess(): String

  @Message
  fun checksTablePlaylistHeader(): String

  @Message
  fun checksTableCheckedHeader(): String

  @Message
  fun checksTableViolationsHeader(): String

  @Message
  fun checksNoViolationsPlaceholder(): String

  @Message("Show violations for {checkName} on {playlistName}")
  fun checksShowViolationsAriaLabel(checkName: String, playlistName: String): String

  @Message
  fun checksSelectAllButton(): String

  @Message
  fun checksSelectNoneButton(): String

  @Message("Toggle fix for {message}")
  fun checksToggleFixAriaLabel(message: String): String

  @Message
  fun checksCloseButton(): String

  @Message
  fun checksFixButton(): String

  @Message
  fun checksFixSuccess(): String

  @Message
  fun checksFixErrorPrefix(): String
}
