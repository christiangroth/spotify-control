package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.catalog.FollowSuggestionsPort
import jakarta.enterprise.context.ApplicationScoped

// Builds the app_follow_suggestions_view read model once for existing deployments (see #901, ADR-0014) so
// FollowSuggestionsResource can read a single precomputed document instead of recomputing the candidates on
// every request. Later updates happen via the deduplicated RebuildFollowSuggestions outbox event.
@ApplicationScoped
@Suppress("Unused")
class BackfillFollowSuggestionsViewStarter(
  private val followSuggestionsPort: FollowSuggestionsPort,
) : Starter {

  override val id = "BackfillFollowSuggestionsViewStarter-v1"

  override fun execute() {
    followSuggestionsPort.rebuildFollowSuggestionsView()
  }
}
