package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import io.quarkus.qute.TemplateData

// Qute resolves `stats.xyz` in health.html via reflection (no @CheckedTemplate). Getter-only Kotlin properties
// (declared in the class body, without a backing constructor field) aren't reachable by GraalVM's native-image
// analysis, which surfaced as "Property not found" only in native mode. @TemplateData generates a build-time
// ValueResolver instead of relying on runtime reflection, avoiding the issue entirely (in both JVM and native mode).
@TemplateData(target = HealthStats::class)
@TemplateData(target = MongoCollectionStats::class)
class QuteTemplateDataConfig
