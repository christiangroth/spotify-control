package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.infra.HealthStats
import io.quarkus.qute.CheckedTemplate
import io.quarkus.qute.TemplateInstance

// Type-safe template/fragment bindings, validated at build time by Qute. Method names must match the template file's
// base name (for a page) or "templateBaseName$fragmentId" (for a `{#fragment id="..."}` section), and are kept in a
// single top-level object (rather than nested per resource) so the default "flat" template lookup applies, matching
// the flat layout of src/main/resources/templates.
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
}
