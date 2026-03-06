package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.IndexOptions
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import mu.KLogging
import org.bson.Document

@ApplicationScoped
@Suppress("UnusedParameter")
class MongoIndexInitializer {

    @Inject
    lateinit var recentlyPlayedDocumentRepository: RecentlyPlayedDocumentRepository

    @Inject
    lateinit var currentlyPlayingDocumentRepository: CurrentlyPlayingDocumentRepository

    @Inject
    lateinit var recentlyPartialPlayedDocumentRepository: RecentlyPartialPlayedDocumentRepository

    fun onStartup(@Observes event: StartupEvent) {
        logger.info { "Ensuring MongoDB indexes..." }

        recentlyPlayedDocumentRepository.mongoCollection().createIndex(
            Document("spotifyUserId", 1).append("playedAt", 1),
            IndexOptions().name("spotifyUserId_1_playedAt_1"),
        )

        currentlyPlayingDocumentRepository.mongoCollection().createIndex(
            Document("spotifyUserId", 1).append("trackId", 1).append("observedAt", 1),
            IndexOptions().name("spotifyUserId_1_trackId_1_observedAt_1"),
        )

        recentlyPartialPlayedDocumentRepository.mongoCollection().createIndex(
            Document("spotifyUserId", 1).append("playedAt", 1),
            IndexOptions().name("rpp_spotifyUserId_1_playedAt_1"),
        )

        logger.info { "MongoDB indexes ready." }
    }

    companion object : KLogging()
}
