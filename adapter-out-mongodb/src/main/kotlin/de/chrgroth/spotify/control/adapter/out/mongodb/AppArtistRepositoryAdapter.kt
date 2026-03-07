package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.AppArtist
import de.chrgroth.spotify.control.domain.port.out.AppArtistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KLogging

@ApplicationScoped
class AppArtistRepositoryAdapter : AppArtistRepositoryPort {

    @Inject
    lateinit var appArtistDocumentRepository: AppArtistDocumentRepository

    @Inject
    lateinit var mongoQueryMetrics: MongoQueryMetrics

    override fun upsertAll(items: List<AppArtist>) {
        if (items.isEmpty()) return
        val collection = appArtistDocumentRepository.mongoCollection()
        val upsertOptions = UpdateOptions().upsert(true)
        mongoQueryMetrics.timed("app_artist.upsertAll") {
            items.forEach { item ->
                collection.updateOne(
                    Filters.eq("_id", item.artistId),
                    Updates.combine(
                        Updates.set("artistName", item.artistName),
                        Updates.setOnInsert("genres", item.genres),
                        Updates.setOnInsert("imageLink", item.imageLink),
                    ),
                    upsertOptions,
                )
            }
        }
    }

    override fun findByArtistIds(artistIds: Set<String>): List<AppArtist> {
        if (artistIds.isEmpty()) return emptyList()
        return mongoQueryMetrics.timed("app_artist.findByArtistIds") {
            artistIds.mapNotNull { artistId ->
                appArtistDocumentRepository.findById(artistId)?.toDomain()
            }
        }
    }

    override fun updateEnrichmentData(artistId: String, genres: List<String>, imageLink: String?) {
        mongoQueryMetrics.timed("app_artist.updateEnrichmentData") {
            appArtistDocumentRepository.mongoCollection().updateOne(
                Filters.eq("_id", artistId),
                Updates.combine(
                    Updates.set("genres", genres),
                    Updates.set("imageLink", imageLink),
                ),
            )
        }
    }

    private fun AppArtistDocument.toDomain() = AppArtist(
        artistId = id,
        artistName = artistName,
        genres = genres,
        imageLink = imageLink,
    )

    companion object : KLogging()
}
