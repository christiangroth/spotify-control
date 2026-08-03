package de.chrgroth.spotify.control.adapter.out.mongodb

import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class DashboardViewDocumentRepository : PanacheMongoRepositoryBase<DashboardViewDocument, String>
