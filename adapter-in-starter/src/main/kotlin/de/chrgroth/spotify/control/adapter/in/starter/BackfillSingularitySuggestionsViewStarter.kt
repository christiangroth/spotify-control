package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.playlist.SingularitySuggestionsPort
import jakarta.enterprise.context.ApplicationScoped

// Builds the app_singularity_suggestions_view read model once for existing deployments (see #941, ADR-0014) so
// SingularitySuggestionsResource can read a single precomputed document instead of recomputing the candidates on
// every request. Later updates happen via the deduplicated RebuildSingularitySuggestions outbox event.
@ApplicationScoped
@Suppress("Unused")
class BackfillSingularitySuggestionsViewStarter(
  private val singularitySuggestionsPort: SingularitySuggestionsPort,
) : Starter {

  override val id = "BackfillSingularitySuggestionsViewStarter-v1"

  override fun execute() {
    singularitySuggestionsPort.rebuildSingularitySuggestionsView()
  }
}
