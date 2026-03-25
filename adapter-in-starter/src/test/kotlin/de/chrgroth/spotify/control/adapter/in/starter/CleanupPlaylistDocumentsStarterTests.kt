package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.bson.conversions.Bson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CleanupPlaylistDocumentsStarterTests {

    private val mongoDatabase: MongoDatabase = mockk()
    private val mongoClient: MongoClient = mockk()
    private val playlistCollection: MongoCollection<Document> = mockk(relaxed = true)

    private val databaseName = "test-db"
    private val starter = CleanupPlaylistDocumentsStarter(mongoClient, databaseName)

    @BeforeEach
    fun setUp() {
        every { mongoClient.getDatabase(databaseName) } returns mongoDatabase
        every { mongoDatabase.getCollection("spotify_playlist") } returns playlistCollection
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("CleanupPlaylistDocumentsStarter-v1")
    }

    @Test
    fun `execute updates all playlist documents`() {
        starter.execute()

        verify(exactly = 1) { playlistCollection.updateMany(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute handles zero modified documents without throwing`() {
        starter.execute()

        verify(exactly = 1) { playlistCollection.updateMany(any<Bson>(), any<Bson>()) }
    }
}
