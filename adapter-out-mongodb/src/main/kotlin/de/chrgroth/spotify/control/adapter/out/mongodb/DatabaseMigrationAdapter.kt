package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.MongoNamespace
import com.mongodb.client.MongoClient
import de.chrgroth.spotify.control.domain.port.out.DatabaseMigrationPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DatabaseMigrationAdapter(
    private val mongoClient: MongoClient,
    @param:ConfigProperty(name = "quarkus.mongodb.database")
    private val databaseName: String,
) : DatabaseMigrationPort {

    override fun renameCollectionIfExists(from: String, to: String) {
        val database = mongoClient.getDatabase(databaseName)
        val existingCollections = database.listCollectionNames().toList()
        if (from !in existingCollections) {
            logger.info { "Collection '$from' does not exist, skipping rename to '$to'" }
            return
        }
        if (to in existingCollections) {
            logger.warn { "Target collection '$to' already exists, skipping rename from '$from'" }
            return
        }
        logger.info { "Renaming collection '$from' to '$to'" }
        database.getCollection(from).renameCollection(MongoNamespace(databaseName, to))
        logger.info { "Successfully renamed collection '$from' to '$to'" }
    }

    companion object : KLogging()
}
