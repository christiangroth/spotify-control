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
    lateinit var recentlyPlayedDocumentRepository: SpotifyRecentlyPlayedDocumentRepository

    @Inject
    lateinit var currentlyPlayingDocumentRepository: SpotifyCurrentlyPlayingDocumentRepository

    @Inject
    lateinit var recentlyPartialPlayedDocumentRepository: SpotifyRecentlyPartialPlayedDocumentRepository

    @Inject
    lateinit var appPlaybackDocumentRepository: AppPlaybackDocumentRepository

    @Inject
    lateinit var appArtistDocumentRepository: AppArtistDocumentRepository

    @Inject
    lateinit var appTrackDocumentRepository: AppTrackDocumentRepository

    @Inject
    lateinit var playlistMetadataDocumentRepository: PlaylistMetadataDocumentRepository

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

        appPlaybackDocumentRepository.mongoCollection().createIndex(
            Document("spotifyUserId", 1).append("playedAt", 1),
            IndexOptions().name("app_playback_spotifyUserId_1_playedAt_1"),
        )

        appPlaybackDocumentRepository.mongoCollection().createIndex(
            Document("trackId", 1),
            IndexOptions().name("app_playback_trackId_1"),
        )

        appArtistDocumentRepository.mongoCollection().createIndex(
            Document("playbackProcessingStatus", 1),
            IndexOptions().name("app_artist_playbackProcessingStatus_1"),
        )

        appTrackDocumentRepository.mongoCollection().createIndex(
            Document("artistId", 1),
            IndexOptions().name("app_track_artistId_1"),
        )

        playlistMetadataDocumentRepository.mongoCollection().createIndex(
            Document("spotifyUserId", 1),
            IndexOptions().name("spotify_playlist_metadata_spotifyUserId_1"),
        )

        logger.info { "MongoDB indexes ready." }
    }

    companion object : KLogging()
}
