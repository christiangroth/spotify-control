package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MigratePartialPlayedStartTimeStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "MigratePartialPlayedStartTimeStarter-v1"

  override fun execute() {
    val result = mongoClient.getDatabase(databaseName)
      .getCollection(COLLECTION)
      .updateMany(
        Filters.not(Filters.exists(START_TIME_FIELD)),
        listOf(
          Document(
            "\$set",
            Document(
              START_TIME_FIELD,
              Document(
                "\$subtract",
                listOf(
                  "\$$PLAYED_AT_FIELD",
                  Document("\$multiply", listOf("\$$PLAYED_SECONDS_FIELD", 1000L)),
                ),
              ),
            ),
          ),
        ),
      )
    logger.info { "Migrated startTime for ${result.modifiedCount} recently partial played documents" }
  }

  companion object : KLogging() {
    private const val COLLECTION = "spotify_recently_partial_played"
    private const val START_TIME_FIELD = "startTime"
    private const val PLAYED_AT_FIELD = "playedAt"
    private const val PLAYED_SECONDS_FIELD = "playedSeconds"
  }
}
