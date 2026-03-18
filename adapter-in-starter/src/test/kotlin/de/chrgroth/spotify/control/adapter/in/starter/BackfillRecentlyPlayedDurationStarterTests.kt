package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.FindIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.MongoCursor
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BackfillRecentlyPlayedDurationStarterTests {

    private val mongoDatabase: MongoDatabase = mockk()
    private val mongoClient: MongoClient = mockk()

    private val recentlyPlayedCollection: MongoCollection<Document> = mockk(relaxed = true)
    private val trackCollection: MongoCollection<Document> = mockk(relaxed = true)

    private val databaseName = "test-db"
    private val starter = BackfillRecentlyPlayedDurationStarter(mongoClient, databaseName)

    @BeforeEach
    fun setUp() {
        every { mongoClient.getDatabase(databaseName) } returns mongoDatabase
        every { mongoDatabase.getCollection("spotify_recently_played") } returns recentlyPlayedCollection
        every { mongoDatabase.getCollection("app_track") } returns trackCollection
    }

    private fun <T : Any> mockFindIterable(docs: List<T>): FindIterable<T> {
        val iter = docs.iterator()
        val cursor = mockk<MongoCursor<T>>()
        every { cursor.hasNext() } answers { iter.hasNext() }
        every { cursor.next() } answers { iter.next() }
        every { cursor.close() } just runs
        return mockk<FindIterable<T>>().also {
            every { it.iterator() } returns cursor
        }
    }

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("BackfillRecentlyPlayedDurationStarter-v1")
    }

    @Test
    fun `execute does nothing when no documents are missing durationSeconds`() {
        every { recentlyPlayedCollection.find(any<Bson>()) } returns mockFindIterable(emptyList())

        starter.execute()

        verify(exactly = 0) { recentlyPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute backfills durationSeconds from app_track document`() {
        val docId = ObjectId()
        val recentlyPlayedDoc = Document(mapOf("_id" to docId, "trackId" to "track-1"))
        val trackDoc = Document(mapOf("_id" to "track-1", "durationMs" to 210_000L))

        every { recentlyPlayedCollection.find(any<Bson>()) } returns mockFindIterable(listOf(recentlyPlayedDoc))
        every { trackCollection.find(any<Bson>()) } returns mockFindIterable(listOf(trackDoc))

        starter.execute()

        verify(exactly = 1) { recentlyPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute logs error when no matching app_track document exists`() {
        val docId = ObjectId()
        val recentlyPlayedDoc = Document(mapOf("_id" to docId, "trackId" to "track-missing"))

        every { recentlyPlayedCollection.find(any<Bson>()) } returns mockFindIterable(listOf(recentlyPlayedDoc))
        every { trackCollection.find(any<Bson>()) } returns mockFindIterable(emptyList())

        starter.execute()

        verify(exactly = 0) { recentlyPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute logs error when app_track document has no durationMs`() {
        val docId = ObjectId()
        val recentlyPlayedDoc = Document(mapOf("_id" to docId, "trackId" to "track-noduration"))
        val trackDoc = Document(mapOf("_id" to "track-noduration"))

        every { recentlyPlayedCollection.find(any<Bson>()) } returns mockFindIterable(listOf(recentlyPlayedDoc))
        every { trackCollection.find(any<Bson>()) } returns mockFindIterable(listOf(trackDoc))

        starter.execute()

        verify(exactly = 0) { recentlyPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }
}
