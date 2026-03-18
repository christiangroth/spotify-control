package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.MongoViewerFilter
import de.chrgroth.spotify.control.domain.model.MongoViewerResult

interface MongoViewerPort {
    fun getViewer(
        collection: String?,
        filters: List<MongoViewerFilter>,
        sortField: String?,
        sortDesc: Boolean,
        page: Int,
        pageSize: Int,
    ): MongoViewerResult
}
