package de.chrgroth.spotify.control.domain.port.out.infra

interface DatabaseMigrationPort {
  fun renameCollectionIfExists(from: String, to: String)
  fun dropCollectionIfExists(name: String)
}
