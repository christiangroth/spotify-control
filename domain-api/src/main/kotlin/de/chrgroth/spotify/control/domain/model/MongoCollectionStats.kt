package de.chrgroth.spotify.control.domain.model

data class MongoCollectionStats(
    val name: String,
    val documentCount: Long,
    val size: Long,
) {
    val sizeKb: Long get() = size / BYTES_PER_KILOBYTE

    companion object {
        private const val BYTES_PER_KILOBYTE = 1024
    }
}
