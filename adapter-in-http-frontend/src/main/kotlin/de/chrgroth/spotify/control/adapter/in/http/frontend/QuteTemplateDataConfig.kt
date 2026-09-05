package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncTimelineEntry
import de.chrgroth.spotify.control.domain.model.catalog.CatalogSyncTimelinePage
import de.chrgroth.spotify.control.domain.model.catalog.TrackBrowseItem
import de.chrgroth.spotify.control.domain.model.infra.ConfigEntry
import de.chrgroth.spotify.control.domain.model.infra.ConfigurationStats
import de.chrgroth.spotify.control.domain.model.infra.CronjobStats
import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.model.infra.MongoQueryStats
import de.chrgroth.spotify.control.domain.model.infra.OutboxEventTypeCount
import de.chrgroth.spotify.control.domain.model.infra.OutboxPartitionStats
import de.chrgroth.spotify.control.domain.model.infra.OutboxTask
import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.model.infra.OutgoingRequestStats
import de.chrgroth.spotify.control.domain.model.infra.PredicateStats
import de.chrgroth.spotify.control.domain.model.playback.DayCount
import de.chrgroth.spotify.control.domain.model.playback.ListeningStats
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventEntry
import de.chrgroth.spotify.control.domain.model.playback.PlaybackEventViewerResult
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.TopEntry
import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckStats
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckViolation
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.user.RuntimeConfig
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerField
import de.chrgroth.spotify.control.domain.model.viewer.MongoViewerResult
import io.quarkus.qute.TemplateData
import kotlinx.datetime.LocalDate

// Qute resolves properties like `stats.xyz` in templates via reflection (no @CheckedTemplate). Classes bound this
// way aren't reliably reachable by GraalVM's native-image analysis, which surfaces as "Property not found" only in
// native mode - this can hit plain constructor properties too, not just getter-only ones. @TemplateData generates a
// build-time ValueResolver instead of relying on runtime reflection, avoiding the issue entirely (in both JVM and
// native mode). Every class below is bound untyped to a template (directly or as part of another bound class's
// object graph, e.g. DashboardStats' nested types) and needs to be listed here explicitly.
@TemplateData(target = HealthStats::class)
@TemplateData(target = MongoCollectionStats::class)
@TemplateData(target = ArtistBrowseItem::class)
@TemplateData(target = AlbumBrowseItem::class)
@TemplateData(target = TrackBrowseItem::class)
@TemplateData(target = OutboxTask::class)
@TemplateData(target = OutboxViewerPartition::class)
@TemplateData(target = PlaylistChecksResource.PlaylistCheckGroup::class)
@TemplateData(target = PlaylistChecksResource.PlaylistCheckRow::class)
@TemplateData(target = AppPlaylistCheck::class)
@TemplateData(target = PlaylistCheckViolation::class)
@TemplateData(target = LogUiGroup::class)
@TemplateData(target = PlaylistsResource.PlaylistRow::class)
@TemplateData(target = ReleaseNotesGroupView::class)
@TemplateData(target = DashboardStats::class)
@TemplateData(target = DayCount::class)
@TemplateData(target = RecentlyPlayedItem::class)
@TemplateData(target = ListeningStats::class)
@TemplateData(target = TopEntry::class)
@TemplateData(target = CatalogStats::class)
@TemplateData(target = PlaylistCheckStats::class)
@TemplateData(target = RuntimeConfig::class)
@TemplateData(target = ConfigurationStats::class)
@TemplateData(target = ConfigEntry::class)
@TemplateData(target = PlaybackAggregation::class)
@TemplateData(target = StatsResource.AggregationTab::class)
@TemplateData(target = StatsResource.AggregationView::class)
@TemplateData(target = StatsResource.RankEntryView::class)
@TemplateData(target = StatsResource.ActivityBarEntryView::class)
@TemplateData(target = MongoViewerResult::class)
@TemplateData(target = MongoViewerField::class)
@TemplateData(target = PlaybackEventViewerResult::class)
@TemplateData(target = PlaybackEventEntry::class)
@TemplateData(target = CatalogSyncTimelinePage::class)
@TemplateData(target = CatalogSyncTimelineEntry::class)
@TemplateData(target = CronjobStats::class)
@TemplateData(target = OutboxPartitionStats::class)
@TemplateData(target = OutboxEventTypeCount::class)
@TemplateData(target = OutgoingRequestStats::class)
@TemplateData(target = MongoQueryStats::class)
@TemplateData(target = PredicateStats::class)
@TemplateData(target = LogUiEntry::class)
@TemplateData(target = PlaylistInfo::class)
// external type, reached via `.toString` path expressions like `result.date.toString` (e.g. playback-event-viewer.html,
// dashboard.html) - same reflection gap as above, just on a class we don't own
@TemplateData(target = LocalDate::class)
class QuteTemplateDataConfig
