package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.model.infra.OutboxTask
import io.quarkus.qute.TemplateData

// Qute resolves properties like `stats.xyz` in templates via reflection (no @CheckedTemplate). Getter-only Kotlin
// properties (declared in the class body, without a backing constructor field) aren't reachable by GraalVM's
// native-image analysis, which surfaced as "Property not found" only in native mode. @TemplateData generates a
// build-time ValueResolver instead of relying on runtime reflection, avoiding the issue entirely (in both JVM and
// native mode). Every class below has at least one getter-only property that a template actually references.
@TemplateData(target = HealthStats::class)
@TemplateData(target = MongoCollectionStats::class)
@TemplateData(target = ArtistBrowseItem::class)
@TemplateData(target = OutboxTask::class)
@TemplateData(target = PlaylistChecksResource.PlaylistCheckRow::class)
@TemplateData(target = LogUiGroup::class)
@TemplateData(target = PlaylistsResource.PlaylistRow::class)
class QuteTemplateDataConfig
