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

class RemoveGenreFieldsStarterTests {

    private val mongoDatabase: MongoDatabase = mockk()
    private val mongoClient: MongoClient = mockk()

    private val artistCollection: MongoCollection<Document> = mockk(relaxed = true)
    private val albumCollection: MongoCollection<Document> = mockk(relaxed = true)

    private val databaseName = "test-db"
    private val starter = RemoveGenreFieldsStarter(mongoClient, databaseName)

    @BeforeEach
    fun setUp() {
        every { mongoClient.getDatabase(databaseName) } returns mongoDatabase
        every { mongoDatabase.getCollection("app_artist") } returns artistCollection
        every { mongoDatabase.getCollection("app_album") } returns albumCollection
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("RemoveGenreFieldsStarter-v1")
    }

    @Test
    fun `execute removes genre fields from artist documents`() {
        starter.execute()

        verify(exactly = 1) { artistCollection.updateMany(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute removes genreOverrides field from album documents`() {
        starter.execute()

        verify(exactly = 1) { albumCollection.updateMany(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute handles zero modified documents without throwing`() {
        starter.execute()

        verify(exactly = 1) { artistCollection.updateMany(any<Bson>(), any<Bson>()) }
        verify(exactly = 1) { albumCollection.updateMany(any<Bson>(), any<Bson>()) }
    }
}
