package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import io.quarkus.runtime.annotations.RegisterForReflection

// Qute resolves `stats.xyz` in health.html via reflection at runtime (no @CheckedTemplate). Getter-only Kotlin
// properties (declared in the class body, without a backing constructor field) aren't reachable by GraalVM's
// native-image analysis unless explicitly registered, which surfaced as "Property not found" only in native mode.
@RegisterForReflection(targets = [HealthStats::class, MongoCollectionStats::class])
class NativeReflectionConfig
