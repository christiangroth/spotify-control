package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Shared UI strings used across the app shell (navbar, page nav, login, error page) and reusable template fragments
 * (`templates/tags`). Domain/page-specific strings live in their own bundle (see [DashboardMessages] and siblings).
 */
@MessageBundle
interface AppMessages {

  @Message
  fun commonAppName(): String

  @Message
  fun commonNotAvailable(): String

  @Message
  fun commonSpotifyLogoAriaLabel(): String

  @Message
  fun commonLogout(): String

  @Message
  fun commonUnknownError(): String

  @Message
  fun commonRequestFailed(): String

  // layout / navbar
  @Message
  fun layoutLogoAlt(): String

  @Message
  fun layoutOutboxStatusLabel(): String

  @Message
  fun layoutPlaybackStatusLabel(): String

  @Message
  fun layoutTechnicalMenuLabel(): String

  @Message
  fun layoutNavHealthLabel(): String

  @Message
  fun layoutNavCatalogSyncLabel(): String

  @Message
  fun layoutNavOutboxLabel(): String

  @Message
  fun layoutNavConfigLabel(): String

  @Message
  fun layoutNavLogsUiLabel(): String

  @Message
  fun layoutNavGrafanaOverviewLabel(): String

  @Message
  fun layoutNavGrafanaLogsLabel(): String

  @Message
  fun layoutNavGrafanaTechnicalLabel(): String

  @Message
  fun layoutNavGrafanaDomainLabel(): String

  @Message
  fun layoutNavMongodbViewerLabel(): String

  @Message
  fun layoutNavMongodbAtlasLabel(): String

  @Message
  fun layoutNavDocsLabel(): String

  @Message
  fun layoutNavGithubLabel(): String

  @Message
  fun layoutNavSpotifyDebugLabel(): String

  @Message
  fun layoutNavSpotifyApiLabel(): String

  // page nav tiles
  @Message
  fun layoutNavTileDashboardLabel(): String

  @Message
  fun layoutNavTileStatsLabel(): String

  @Message
  fun layoutNavTilePlaylistsLabel(): String

  @Message
  fun layoutNavTileChecksLabel(): String

  @Message
  fun layoutNavTileCatalogLabel(): String

  @Message
  fun layoutNavTilePlaybackLabel(): String

  @Message("{count} artist(s) awaiting sync decision")
  fun layoutCatalogBadgeTooltip(count: String): String

  @Message
  fun layoutOutboxCountdownNow(): String

  @Message
  fun layoutLanguageToggleLabel(): String

  // login page
  @Message
  fun loginButton(): String

  @Message
  fun loginErrorAlreadyRegistered(): String

  @Message
  fun loginErrorTokenExchangeFailed(): String

  @Message
  fun loginErrorProfileFetchFailed(): String

  @Message
  fun loginErrorTokenRefreshFailed(): String

  @Message
  fun loginErrorEncryptionFailed(): String

  @Message
  fun loginErrorSessionInvalidDecryption(): String

  @Message
  fun loginErrorSessionInvalidFormat(): String

  @Message
  fun loginErrorSpotifyDenied(): String

  @Message
  fun loginErrorInvalidRequest(): String

  @Message
  fun loginErrorStateMismatch(): String

  @Message
  fun loginErrorUnexpected(): String

  // error page
  @Message
  fun errorHeading(): String

  @Message
  fun errorTypeLabel(): String

  @Message
  fun errorMessageLabel(): String

  @Message
  fun errorStackTraceHeading(): String

  @Message
  fun errorNoMessage(): String

  // shared rank-entry / playback-event tags
  @Message
  fun tagUnknownAlbum(): String

  @Message
  fun tagUnknownArtist(): String

  @Message
  fun tagAlbumCoverPlaceholderAriaLabel(): String

  @Message
  fun tagPlaybackEventTrackLabel(): String

  @Message
  fun tagPlaybackEventArtistLabel(): String

  @Message
  fun tagPlaybackEventAlbumLabel(): String

  @Message
  fun tagPlaybackEventStartLabel(): String

  @Message
  fun tagPlaybackEventDurationLabel(): String
}
