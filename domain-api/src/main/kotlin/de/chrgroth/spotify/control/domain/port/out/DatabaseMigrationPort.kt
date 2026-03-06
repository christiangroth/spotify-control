package de.chrgroth.spotify.control.domain.port.out

interface DatabaseMigrationPort {
    fun renameCollectionIfExists(from: String, to: String)
}
