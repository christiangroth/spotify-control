package de.chrgroth.spotify.control.domain.port.out.readmodel

import de.chrgroth.spotify.control.domain.model.catalog.FollowSuggestionsView

interface FollowSuggestionsViewRepositoryPort {
  fun save(view: FollowSuggestionsView)
  fun find(): FollowSuggestionsView?
}
