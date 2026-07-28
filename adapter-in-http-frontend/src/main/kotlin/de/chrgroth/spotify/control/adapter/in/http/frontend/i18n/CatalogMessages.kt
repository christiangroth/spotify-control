package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings for the catalog domain pages: `catalog.html`, `catalog-sync.html`, `catalog-artists-settings.html` and `catalog-shallow-artists.html`.
 */
@MessageBundle("catalog")
interface CatalogMessages {

  // shared across catalog pages
  @Message
  fun catalogTableArtistHeader(): String

  @Message
  fun catalogTableAlbumHeader(): String

  @Message
  fun catalogTableAlbumsHeader(): String

  @Message
  fun catalogTableTracksHeader(): String

  @Message
  fun catalogTableActionsHeader(): String

  @Message
  fun catalogTableDurationHeader(): String

  @Message
  fun catalogTableReleasedHeader(): String

  @Message
  fun catalogArtistImagePlaceholderAriaLabel(): String

  @Message
  fun catalogBadgeShallow(): String

  @Message
  fun catalogUntitledAlbum(): String

  @Message
  fun catalogSetSyncButton(): String

  @Message
  fun catalogSetShallowButton(): String

  @Message
  fun catalogSetSyncErrorPrefix(): String

  @Message
  fun catalogSetShallowErrorPrefix(): String

  @Message
  fun catalogResyncErrorPrefix(): String

  // catalog.html
  @Message
  fun catalogTitle(): String

  @Message
  fun catalogStatsArtistsLabel(): String

  @Message("{count} undecided")
  fun catalogStatsUndecidedLabel(count: String): String

  @Message("{count} shallow")
  fun catalogStatsShallowLabel(count: String): String

  @Message
  fun catalogStatsAlbumsLabel(): String

  @Message
  fun catalogStatsTracksLabel(): String

  @Message
  fun catalogResyncHeading(): String

  @Message
  fun catalogResyncDescription(): String

  @Message
  fun catalogResyncButton(): String

  @Message
  fun catalogWipeCatalogLabel(): String

  @Message
  fun catalogWipeDescription(): String

  @Message
  fun catalogWipeModalTitle(): String

  @Message
  fun catalogWipeModalCloseAriaLabel(): String

  @Message
  fun catalogWipeModalBodyBeforeWord(): String

  @Message
  fun catalogWipeConfirmWord(): String

  @Message
  fun catalogWipeModalBodyAfterWord(): String

  @Message
  fun catalogWipeConfirmPlaceholder(): String

  @Message
  fun catalogWipeModalCancelButton(): String

  @Message
  fun catalogBrowserHeading(): String

  @Message
  fun catalogFilterAriaLabel(): String

  @Message
  fun catalogFilterPlaceholder(): String

  @Message
  fun catalogFilterEmptyPrompt(): String

  @Message
  fun catalogFilterNoResults(): String

  @Message
  fun catalogBadgeAssumption(): String

  @Message("Show sync trigger for {name}")
  fun catalogSyncTriggerForArtistAriaLabel(name: String): String

  @Message
  fun catalogSyncTriggerForPrefix(): String

  @Message
  fun catalogAlbumFallbackName(): String

  @Message("Re-sync {name}")
  fun catalogResyncArtistAriaLabel(name: String): String

  @Message
  fun catalogResyncArtistButton(): String

  @Message("Set {name} to sync")
  fun catalogSetSyncAriaLabel(name: String): String

  @Message("Set {name} to shallow")
  fun catalogSetShallowAriaLabel(name: String): String

  @Message
  fun catalogLoadingAlbums(): String

  @Message
  fun catalogAlbumImagePlaceholderAriaLabel(): String

  @Message
  fun catalogLoadingTracks(): String

  @Message
  fun catalogTableTrackNumberHeader(): String

  @Message
  fun catalogTableTitleHeader(): String

  @Message
  fun catalogResyncSuccessMessage(): String

  @Message
  fun catalogWipeSuccessMessage(): String

  @Message
  fun catalogWipeErrorPrefix(): String

  @Message
  fun catalogSyncTraceLoadingLabel(): String

  @Message
  fun catalogSyncTraceLoadFailed(): String

  @Message
  fun catalogSyncTraceNotAvailable(): String

  @Message
  fun catalogArtistResyncSuccessMessage(): String

  @Message
  fun catalogSetShallowSuccessMessage(): String

  @Message
  fun catalogSetSyncSuccessMessage(): String

  // catalog-sync.html
  @Message
  fun catalogSyncTitle(): String

  @Message
  fun catalogSyncDescription(): String

  @Message
  fun catalogSyncTableTypeHeader(): String

  @Message
  fun catalogSyncTableSyncedAtHeader(): String

  @Message
  fun catalogSyncBadgeArtistLabel(): String

  @Message
  fun catalogSyncBadgeAlbumLabel(): String

  @Message
  fun catalogEmptyValuePlaceholder(): String

  @Message
  fun catalogSyncEmptyState(): String

  @Message
  fun catalogSyncLoadMoreButton(): String

  // catalog-artists-settings.html
  @Message
  fun catalogArtistSettingsTitle(): String

  @Message
  fun catalogBackToCatalogButton(): String

  @Message
  fun catalogArtistSettingsDescription(): String

  @Message("Showing the first {count} artists. Resolve some of them to see more.")
  fun catalogArtistSettingsTruncatedNotice(count: Int): String

  @Message
  fun catalogArtistSettingsEmptyState(): String

  @Message
  fun catalogTableAssumptionHeader(): String

  @Message
  fun catalogBadgeSync(): String

  @Message("Confirm {name} as sync")
  fun catalogConfirmSyncAriaLabel(name: String): String

  @Message("Confirm {name} as shallow")
  fun catalogConfirmShallowAriaLabel(name: String): String

  @Message
  fun catalogArtistSettingsSetSyncSuccessMessage(): String

  @Message
  fun catalogArtistSettingsSetShallowSuccessMessage(): String

  // catalog-shallow-artists.html
  @Message
  fun catalogShallowArtistsTitle(): String

  @Message
  fun catalogShallowArtistsDescription(): String

  @Message("Showing the first {count} artists.")
  fun catalogShallowArtistsTruncatedNotice(count: Int): String

  @Message
  fun catalogShallowArtistsEmptyState(): String
}
