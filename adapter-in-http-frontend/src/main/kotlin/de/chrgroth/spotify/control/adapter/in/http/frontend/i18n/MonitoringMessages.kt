package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the operational/monitoring pages (`templates/mongodb-viewer.html`, `templates/outbox-viewer.html`,
 * `templates/spotify-debug.html`). Shared/reusable app-shell strings live in [AppMessages] instead.
 */
@MessageBundle("monitoring")
interface MonitoringMessages {

  // mongodb viewer
  @Message
  fun mongodbViewerTitle(): String

  @Message
  fun mongodbViewerHeading(): String

  @Message
  fun mongodbViewerCollectionLabel(): String

  @Message
  fun mongodbViewerCollectionPlaceholder(): String

  @Message
  fun mongodbViewerNoFieldsMessage(): String

  @Message
  fun mongodbViewerFiltersHeading(): String

  @Message
  fun mongodbViewerFilterContainsBadge(): String

  @Message
  fun mongodbViewerFilterIdBadge(): String

  @Message
  fun mongodbViewerFilterContainsPlaceholder(): String

  @Message
  fun mongodbViewerFilterEqualsPlaceholder(): String

  @Message
  fun mongodbViewerFilterInPlaceholder(): String

  @Message
  fun mongodbViewerFilterNotInPlaceholder(): String

  @Message
  fun mongodbViewerSortHeading(): String

  @Message
  fun mongodbViewerSortNoneOption(): String

  @Message
  fun mongodbViewerSortAscLabel(): String

  @Message
  fun mongodbViewerSortDescLabel(): String

  @Message
  fun mongodbViewerApplyFiltersButton(): String

  @Message
  fun mongodbViewerResultsHeading(): String

  @Message("{count} document(s) total")
  fun mongodbViewerTotalCount(count: String): String

  @Message
  fun mongodbViewerPerPageLabel(): String

  @Message
  fun mongodbViewerNoDocumentsMessage(): String

  @Message("Page {page} of {totalPages}")
  fun mongodbViewerPageOf(page: String, totalPages: String): String

  @Message
  fun mongodbViewerPrevButton(): String

  @Message
  fun mongodbViewerNextButton(): String

  // outbox viewer
  @Message
  fun outboxViewerTitle(): String

  @Message
  fun outboxViewerHeading(): String

  @Message
  fun outboxViewerWipeActionLabel(): String

  @Message
  fun outboxViewerWipeDescription(): String

  @Message
  fun outboxViewerWipeModalTitle(): String

  @Message
  fun outboxViewerModalCloseAriaLabel(): String

  @Message
  fun outboxViewerWipeConfirmInstructionsPrefix(): String

  @Message
  fun outboxViewerWipeConfirmInstructionsSuffix(): String

  @Message
  fun outboxViewerWipeConfirmInputPlaceholder(): String

  @Message
  fun outboxViewerCancelButton(): String

  @Message
  fun outboxViewerRequeueButtonTitle(): String

  @Message("Requeue stuck tasks in {partition}")
  fun outboxViewerRequeueButtonAriaLabel(partition: String): String

  @Message
  fun outboxViewerColumnStatus(): String

  @Message
  fun outboxViewerColumnCreated(): String

  @Message
  fun outboxViewerColumnType(): String

  @Message
  fun outboxViewerColumnDeduplicationKey(): String

  @Message
  fun outboxViewerColumnAttempts(): String

  @Message
  fun outboxViewerColumnNextRetry(): String

  @Message
  fun outboxViewerColumnLastError(): String

  @Message
  fun outboxViewerNoPendingTasksMessage(): String

  @Message
  fun outboxViewerPriorityHighBadge(): String

  @Message
  fun outboxViewerPriorityMediumBadge(): String

  @Message
  fun outboxViewerWipeSuccessMessage(): String

  @Message
  fun outboxViewerWipeErrorPrefix(): String

  @Message
  fun outboxViewerRequeueSuccessPrefix(): String

  @Message
  fun outboxViewerRequeueSuccessMiddle(): String

  @Message
  fun outboxViewerRequeueNoneMessagePrefix(): String

  @Message
  fun outboxViewerRequeueFailedPrefix(): String

  // spotify debug
  @Message
  fun spotifyDebugTitle(): String

  @Message
  fun spotifyDebugHeading(): String

  @Message
  fun spotifyDebugDescription(): String

  @Message
  fun spotifyDebugTabUser(): String

  @Message
  fun spotifyDebugTabPlayback(): String

  @Message
  fun spotifyDebugTabPlaylist(): String

  @Message
  fun spotifyDebugTabCatalog(): String

  @Message
  fun spotifyDebugPillCurrentUser(): String

  @Message
  fun spotifyDebugPillCurrentlyPlaying(): String

  @Message
  fun spotifyDebugPillRecentlyPlayed(): String

  @Message
  fun spotifyDebugPillMyPlaylists(): String

  @Message
  fun spotifyDebugPillPlaylistTracks(): String

  @Message
  fun spotifyDebugPillArtist(): String

  @Message
  fun spotifyDebugPillArtistAlbums(): String

  @Message
  fun spotifyDebugPillAlbum(): String

  @Message
  fun spotifyDebugPillAlbumTracks(): String

  @Message
  fun spotifyDebugDescCurrentUser(): String

  @Message
  fun spotifyDebugDescCurrentlyPlaying(): String

  @Message
  fun spotifyDebugDescRecentlyPlayed(): String

  @Message
  fun spotifyDebugDescUserPlaylists(): String

  @Message
  fun spotifyDebugDescPlaylistTracks(): String

  @Message
  fun spotifyDebugDescArtist(): String

  @Message
  fun spotifyDebugDescArtistAlbums(): String

  @Message
  fun spotifyDebugDescAlbum(): String

  @Message
  fun spotifyDebugDescAlbumTracks(): String

  @Message
  fun spotifyDebugLabelAfter(): String

  @Message
  fun spotifyDebugLabelPlaylist(): String

  @Message
  fun spotifyDebugLabelPageUrl(): String

  @Message
  fun spotifyDebugLabelArtist(): String

  @Message
  fun spotifyDebugLabelAlbum(): String

  @Message
  fun spotifyDebugPlaceholderSearchPlaylist(): String

  @Message
  fun spotifyDebugPlaceholderSearchArtist(): String

  @Message
  fun spotifyDebugPlaceholderSearchAlbum(): String

  @Message
  fun spotifyDebugExecuteButton(): String

  @Message
  fun spotifyDebugNoResultYetMessage(): String

  @Message
  fun spotifyDebugNoMatchesMessage(): String

  @Message
  fun spotifyDebugLoadingMessage(): String

  @Message
  fun spotifyDebugErrorPrefix(): String

  @Message("Failed to obtain a valid Spotify access token: {reason}")
  fun spotifyDebugAccessTokenError(reason: String): String

  @Message
  fun spotifyDebugPlaylistIdRequired(): String

  @Message
  fun spotifyDebugArtistIdRequired(): String

  @Message
  fun spotifyDebugAlbumIdRequired(): String

  @Message("Unknown operation: {operation}")
  fun spotifyDebugUnknownOperation(operation: String): String

  @Message("Unexpected error: {reason}")
  fun spotifyDebugUnexpectedError(reason: String): String

  @Message
  fun spotifyDebugUntitledAlbum(): String
}
