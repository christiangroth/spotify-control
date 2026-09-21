package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncTimelineEntry
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncTimelinePage
import de.chrgroth.spotify.control.domain.model.catalog.TrackBrowseItem
import de.chrgroth.spotify.control.domain.model.infra.ConfigurationStats
import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventViewerResult
import de.chrgroth.spotify.control.domain.model.user.RuntimeConfig
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerResult
import io.quarkus.qute.CheckedTemplate
import io.quarkus.qute.TemplateInstance
import kotlinx.datetime.LocalDate

// Type-safe template/fragment bindings, validated at build time by Qute. Method names must match the template file's
// base name (for a page) or "templateBaseName$fragmentId" (for a `{#fragment id="..."}` section), and are kept in a
// single top-level object (rather than nested per resource) so the default "flat" template lookup applies, matching
// the flat layout of src/main/resources/templates. Templates nested in a subdirectory (settings/playlist.html) need
// their own @CheckedTemplate class with a matching basePath, since basePath applies to the whole class.
@CheckedTemplate
object Templates {

  @JvmStatic
  external fun health(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_predicates`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_cronjobs`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_outgoing_http_calls`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_outbox_partitions`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_mongodb_collections`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_mongodb_queries`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_navbar_outbox_status`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_navbar_playback_status`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun dashboard(displayName: String, stats: DashboardStats): TemplateInstance

  @JvmStatic
  external fun `dashboard$snippet_playback_histogram`(stats: DashboardStats): TemplateInstance

  @JvmStatic
  external fun `dashboard$snippet_recently_played`(stats: DashboardStats): TemplateInstance

  @JvmStatic
  external fun `dashboard$snippet_listening_stats`(stats: DashboardStats): TemplateInstance

  @JvmStatic
  external fun catalog(
    artists: List<ArtistBrowseItem>,
    filter: String,
    filterActive: Boolean,
    albums: List<AlbumBrowseItem>,
    tracks: List<TrackBrowseItem>,
    catalogStats: CatalogStats,
  ): TemplateInstance

  @JvmStatic
  external fun `catalog$snippet_artist_list`(artists: List<ArtistBrowseItem>, filter: String, filterActive: Boolean): TemplateInstance

  @JvmStatic
  external fun `catalog$snippet_album_list`(albums: List<AlbumBrowseItem>, artistId: String): TemplateInstance

  @JvmStatic
  external fun `catalog$snippet_track_list`(tracks: List<TrackBrowseItem>): TemplateInstance

  @JvmStatic
  external fun `catalog-artists-settings`(artists: List<ArtistBrowseItem>, truncated: Boolean): TemplateInstance

  @JvmStatic
  external fun `catalog-shallow-artists`(artists: List<ArtistBrowseItem>, truncated: Boolean): TemplateInstance

  @JvmStatic
  external fun `catalog-sync`(page: CatalogSyncTimelinePage, entries: List<CatalogSyncTimelineEntry>): TemplateInstance

  @JvmStatic
  external fun `catalog-sync$snippet_rows`(entries: List<CatalogSyncTimelineEntry>): TemplateInstance

  @JvmStatic
  external fun config(stats: ConfigurationStats, runtimeConfig: RuntimeConfig): TemplateInstance

  @JvmStatic
  external fun login(errorMessage: String?, appBuildVersion: String): TemplateInstance

  @JvmStatic
  external fun `logs-viewer`(logs: List<LogUiEntry>, groups: List<LogUiGroup>, viewMode: String): TemplateInstance

  @JvmStatic
  external fun `mongodb-viewer`(result: MongoViewerResult, pageSizes: List<Int>): TemplateInstance

  @JvmStatic
  external fun `outbox-viewer`(partitions: List<OutboxViewerPartition>): TemplateInstance

  @JvmStatic
  external fun `outbox-viewer$snippet_tasks`(partitions: List<OutboxViewerPartition>): TemplateInstance

  @JvmStatic
  external fun `playback-event-viewer`(result: PlaybackEventViewerResult, prevDate: LocalDate, nextDate: LocalDate, today: LocalDate): TemplateInstance

  @JvmStatic
  external fun playback(displayName: String, stats: DashboardStats, eventsDate: LocalDate?): TemplateInstance

  @JvmStatic
  external fun `playlist-checks`(displayName: String, groups: List<PlaylistChecksResource.PlaylistCheckGroup>): TemplateInstance

  @JvmStatic
  external fun `release-notes`(groups: List<ReleaseNotesGroupView>): TemplateInstance

  @JvmStatic
  external fun `spotify-debug`(): TemplateInstance

  @JvmStatic
  external fun stats(tabs: List<StatsResource.AggregationTab>): TemplateInstance

  @JvmStatic
  external fun docs(title: String, markdownContent: String): TemplateInstance

  @JvmStatic
  external fun error(statusCode: Int, errorType: String, message: String, stackTrace: String, appBuildVersion: String): TemplateInstance
}

@CheckedTemplate(basePath = "settings")
object SettingsTemplates {

  @JvmStatic
  external fun playlist(displayName: String, rows: List<PlaylistsResource.PlaylistRow>): TemplateInstance

  @JvmStatic
  external fun `follow-suggestions`(
    followCandidates: List<FollowSuggestionsResource.FollowCandidateRow>,
    unfollowCandidates: List<FollowSuggestionsResource.UnfollowCandidateRow>,
  ): TemplateInstance
}
