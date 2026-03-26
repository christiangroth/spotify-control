package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.MongoNamespace
import com.mongodb.client.MongoClient
import de.chrgroth.spotify.control.domain.port.out.infra.DatabaseMigrationPort
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

    override fun dropCollectionIfExists(name: String) {
        val database = mongoClient.getDatabase(databaseName)
        val existingCollections = database.listCollectionNames().toList()
        if (name !in existingCollections) {
            logger.info { "Collection '$name' does not exist, skipping drop" }
            return
        }
        logger.info { "Dropping collection '$name'" }
        database.getCollection(name).drop()
        logger.info { "Successfully dropped collection '$name'" }
    }

    companion object : KLogging()
}
