package de.chrgroth.spotify.control.util.outbox

import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class OutboxArchiveDocumentRepository : PanacheMongoRepositoryBase<OutboxArchiveDocument, String>
