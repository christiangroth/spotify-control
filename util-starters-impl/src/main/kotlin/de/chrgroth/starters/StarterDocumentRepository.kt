package de.chrgroth.starters

import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class StarterDocumentRepository : PanacheMongoRepositoryBase<StarterDocument, String>
