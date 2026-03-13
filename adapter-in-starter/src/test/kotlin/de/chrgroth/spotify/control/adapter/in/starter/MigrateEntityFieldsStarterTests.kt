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

class MigrateEntityFieldsStarterTests {

    private val mongoDatabase: MongoDatabase = mockk()
    private val mongoClient: MongoClient = mockk()

    private val trackCollection: MongoCollection<Document> = mockk(relaxed = true)
    private val albumCollection: MongoCollection<Document> = mockk(relaxed = true)
    private val artistCollection: MongoCollection<Document> = mockk(relaxed = true)

    private val databaseName = "test-db"
    private val starter = MigrateEntityFieldsStarter(mongoClient, databaseName)

    @BeforeEach
    fun setUp() {
        every { mongoClient.getDatabase(databaseName) } returns mongoDatabase
        every { mongoDatabase.getCollection("app_track") } returns trackCollection
        every { mongoDatabase.getCollection("app_album") } returns albumCollection
        every { mongoDatabase.getCollection("app_artist") } returns artistCollection
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("MigrateEntityFieldsStarter-v1")
    }

    @Test
    fun `execute migrates track title field`() {
        starter.execute()

        verify(exactly = 1) { trackCollection.updateMany(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute migrates album title field`() {
        starter.execute()

        verify(exactly = 1) { albumCollection.updateMany(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute migrates artist genres array`() {
        starter.execute()

        verify(exactly = 1) { artistCollection.updateMany(any<Bson>(), any<List<Bson>>()) }
    }

    @Test
    fun `execute handles zero modified documents without throwing`() {
        starter.execute()

        verify(exactly = 1) { trackCollection.updateMany(any<Bson>(), any<Bson>()) }
        verify(exactly = 1) { albumCollection.updateMany(any<Bson>(), any<Bson>()) }
        verify(exactly = 1) { artistCollection.updateMany(any<Bson>(), any<List<Bson>>()) }
    }
}
