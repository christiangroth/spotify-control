package de.chrgroth.spotify.control.domain.port.out.infra

/**
 * Operational maintenance operations on the outbox storage that the quarkus-outbox library does
 * not expose (e.g. no wipe-all API). Kept separate from [OutboxPort] so that MongoDB access stays
 * confined to adapter-out-mongodb instead of adapter-out-outbox.
 */
interface OutboxAdminPort {
  fun wipeAll()
}
