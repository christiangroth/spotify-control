package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.result.DeleteResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteCatalogDataStarterTests {

    private val mongoDatabase: MongoDatabase = mockk()
    private val mongoClient: MongoClient = mockk()

    private val trackCollection: MongoCollection<Document> = mockk()
    private val albumCollection: MongoCollection<Document> = mockk()
    private val artistCollection: MongoCollection<Document> = mockk()
    private val playlistCheckCollection: MongoCollection<Document> = mockk()
    private val syncPoolCollection: MongoCollection<Document> = mockk()
    private val outboxCollection: MongoCollection<Document> = mockk()

    private val databaseName = "test-db"
    private val starter = DeleteCatalogDataStarter(mongoClient, databaseName)

    @BeforeEach
    fun setUp() {
        every { mongoClient.getDatabase(databaseName) } returns mongoDatabase
        every { mongoDatabase.getCollection("app_track") } returns trackCollection
        every { mongoDatabase.getCollection("app_album") } returns albumCollection
        every { mongoDatabase.getCollection("app_artist") } returns artistCollection
        every { mongoDatabase.getCollection("app_playlist_check") } returns playlistCheckCollection
        every { mongoDatabase.getCollection("app_sync_pool") } returns syncPoolCollection
        every { mongoDatabase.getCollection("outbox") } returns outboxCollection
        every { trackCollection.deleteMany(any<Document>()) } returns DeleteResult.acknowledged(5)
        every { albumCollection.deleteMany(any<Document>()) } returns DeleteResult.acknowledged(3)
        every { artistCollection.deleteMany(any<Document>()) } returns DeleteResult.acknowledged(2)
        every { playlistCheckCollection.deleteMany(any<Document>()) } returns DeleteResult.acknowledged(1)
        every { syncPoolCollection.deleteMany(any<Document>()) } returns DeleteResult.acknowledged(4)
        every { outboxCollection.deleteMany(any<Document>()) } returns DeleteResult.acknowledged(7)
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("DeleteCatalogDataStarter-v1")
    }

    @Test
    fun `execute deletes all app_track documents`() {
        starter.execute()

        verify(exactly = 1) { trackCollection.deleteMany(any<Document>()) }
    }

    @Test
    fun `execute deletes all app_album documents`() {
        starter.execute()

        verify(exactly = 1) { albumCollection.deleteMany(any<Document>()) }
    }

    @Test
    fun `execute deletes all app_artist documents`() {
        starter.execute()

        verify(exactly = 1) { artistCollection.deleteMany(any<Document>()) }
    }

    @Test
    fun `execute deletes all app_playlist_check documents`() {
        starter.execute()

        verify(exactly = 1) { playlistCheckCollection.deleteMany(any<Document>()) }
    }

    @Test
    fun `execute deletes all app_sync_pool documents`() {
        starter.execute()

        verify(exactly = 1) { syncPoolCollection.deleteMany(any<Document>()) }
    }

    @Test
    fun `execute deletes all outbox documents`() {
        starter.execute()

        verify(exactly = 1) { outboxCollection.deleteMany(any<Document>()) }
    }

    @Test
    fun `execute does not interact with any other collection`() {
        starter.execute()

        verify(exactly = 6) { mongoDatabase.getCollection(any()) }
    }
}
