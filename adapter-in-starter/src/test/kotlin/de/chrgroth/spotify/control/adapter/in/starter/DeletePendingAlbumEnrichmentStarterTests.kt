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
import org.junit.jupiter.api.Test

class DeletePendingAlbumEnrichmentStarterTests {

    private val mongoCollection: MongoCollection<Document> = mockk()
    private val mongoDatabase: MongoDatabase = mockk()
    private val mongoClient: MongoClient = mockk()

    private val starter = DeletePendingAlbumEnrichmentStarter(mongoClient, "testdb")

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("DeletePendingAlbumEnrichmentStarter-v1")
    }

    @Test
    fun `execute deletes pending EnrichAlbumDetails tasks and logs result`() {
        every { mongoClient.getDatabase("testdb") } returns mongoDatabase
        every { mongoDatabase.getCollection("outbox") } returns mongoCollection
        every { mongoCollection.deleteMany(any()) } returns DeleteResult.acknowledged(5)

        starter.execute()

        verify(exactly = 1) { mongoCollection.deleteMany(any()) }
    }

    @Test
    fun `execute handles zero deleted tasks without throwing`() {
        every { mongoClient.getDatabase("testdb") } returns mongoDatabase
        every { mongoDatabase.getCollection("outbox") } returns mongoCollection
        every { mongoCollection.deleteMany(any()) } returns DeleteResult.acknowledged(0)

        starter.execute()

        verify(exactly = 1) { mongoCollection.deleteMany(any()) }
    }
}
