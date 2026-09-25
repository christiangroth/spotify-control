package de.chrgroth.spotify.control.domain.port.out.readmodel

import de.chrgroth.spotify.control.domain.model.playlist.SingularitySuggestionsView

interface SingularitySuggestionsViewRepositoryPort {
  fun save(view: SingularitySuggestionsView)
  fun find(): SingularitySuggestionsView?
}
