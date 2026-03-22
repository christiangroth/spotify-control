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

class CapPartialPlayedDurationStarterTests {

    private val mongoDatabase: MongoDatabase = mockk()
    private val mongoClient: MongoClient = mockk()

    private val partialPlayedCollection: MongoCollection<Document> = mockk(relaxed = true)
    private val trackCollection: MongoCollection<Document> = mockk(relaxed = true)

    private val databaseName = "test-db"
    private val starter = CapPartialPlayedDurationStarter(mongoClient, databaseName)

    @BeforeEach
    fun setUp() {
        every { mongoClient.getDatabase(databaseName) } returns mongoDatabase
        every { mongoDatabase.getCollection("spotify_recently_partial_played") } returns partialPlayedCollection
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
        assertThat(starter.id).isEqualTo("CapPartialPlayedDurationStarter-v1")
    }

    @Test
    fun `execute does nothing when collection is empty`() {
        every { partialPlayedCollection.find() } returns mockFindIterable(emptyList())

        starter.execute()

        verify(exactly = 0) { partialPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute does not update when playedSeconds is within track duration`() {
        val docId = ObjectId()
        val partialPlayedDoc = Document(mapOf("_id" to docId, "trackId" to "track-1", "playedSeconds" to 100L))
        val trackDoc = Document(mapOf("_id" to "track-1", "durationMs" to 210_000L))

        every { partialPlayedCollection.find() } returns mockFindIterable(listOf(partialPlayedDoc))
        every { trackCollection.find(any<Bson>()) } returns mockFindIterable(listOf(trackDoc))

        starter.execute()

        verify(exactly = 0) { partialPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute caps playedSeconds to track duration when it exceeds track length`() {
        val docId = ObjectId()
        val partialPlayedDoc = Document(mapOf("_id" to docId, "trackId" to "track-1", "playedSeconds" to 42_039L))
        val trackDoc = Document(mapOf("_id" to "track-1", "durationMs" to 210_000L))

        every { partialPlayedCollection.find() } returns mockFindIterable(listOf(partialPlayedDoc))
        every { trackCollection.find(any<Bson>()) } returns mockFindIterable(listOf(trackDoc))

        starter.execute()

        verify(exactly = 1) { partialPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute logs error when no matching app_track document exists`() {
        val docId = ObjectId()
        val partialPlayedDoc = Document(mapOf("_id" to docId, "trackId" to "track-missing", "playedSeconds" to 42_039L))

        every { partialPlayedCollection.find() } returns mockFindIterable(listOf(partialPlayedDoc))
        every { trackCollection.find(any<Bson>()) } returns mockFindIterable(emptyList())

        starter.execute()

        verify(exactly = 0) { partialPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute logs error when app_track document has no durationMs`() {
        val docId = ObjectId()
        val partialPlayedDoc = Document(mapOf("_id" to docId, "trackId" to "track-noduration", "playedSeconds" to 42_039L))
        val trackDoc = Document(mapOf("_id" to "track-noduration"))

        every { partialPlayedCollection.find() } returns mockFindIterable(listOf(partialPlayedDoc))
        every { trackCollection.find(any<Bson>()) } returns mockFindIterable(listOf(trackDoc))

        starter.execute()

        verify(exactly = 0) { partialPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }

    @Test
    fun `execute caps only documents that exceed track duration`() {
        val docId1 = ObjectId()
        val docId2 = ObjectId()
        val okDoc = Document(mapOf("_id" to docId1, "trackId" to "track-1", "playedSeconds" to 100L))
        val badDoc = Document(mapOf("_id" to docId2, "trackId" to "track-1", "playedSeconds" to 42_039L))
        val trackDoc = Document(mapOf("_id" to "track-1", "durationMs" to 210_000L))

        every { partialPlayedCollection.find() } returns mockFindIterable(listOf(okDoc, badDoc))
        every { trackCollection.find(any<Bson>()) } answers { mockFindIterable(listOf(trackDoc)) }

        starter.execute()

        verify(exactly = 1) { partialPlayedCollection.updateOne(any<Bson>(), any<Bson>()) }
    }
}
