package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.DashboardStats
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.model.infra.OutboxTask
import de.chrgroth.spotify.control.domain.model.playback.DayCount
import de.chrgroth.spotify.control.domain.model.playback.ListeningStats
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.playback.TopEntry
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistCheckStats
import io.quarkus.qute.TemplateData

// Qute resolves properties like `stats.xyz` in templates via reflection (no @CheckedTemplate). Classes bound this
// way aren't reliably reachable by GraalVM's native-image analysis, which surfaces as "Property not found" only in
// native mode - this can hit plain constructor properties too, not just getter-only ones. @TemplateData generates a
// build-time ValueResolver instead of relying on runtime reflection, avoiding the issue entirely (in both JVM and
// native mode). Every class below is bound untyped to a template (directly or as part of another bound class's
// object graph, e.g. DashboardStats' nested types) and needs to be listed here explicitly.
@TemplateData(target = HealthStats::class)
@TemplateData(target = MongoCollectionStats::class)
@TemplateData(target = ArtistBrowseItem::class)
@TemplateData(target = OutboxTask::class)
@TemplateData(target = PlaylistChecksResource.PlaylistCheckRow::class)
@TemplateData(target = LogUiGroup::class)
@TemplateData(target = PlaylistsResource.PlaylistRow::class)
@TemplateData(target = DashboardStats::class)
@TemplateData(target = DayCount::class)
@TemplateData(target = RecentlyPlayedItem::class)
@TemplateData(target = ListeningStats::class)
@TemplateData(target = TopEntry::class)
@TemplateData(target = CatalogStats::class)
@TemplateData(target = PlaylistCheckStats::class)
class QuteTemplateDataConfig
