package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.MongoException
import com.mongodb.client.ListCollectionNamesIterable
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCursor
import com.mongodb.client.MongoDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.bson.conversions.Bson
import org.junit.jupiter.api.Test

class MongoStatsAdapterTests {

  private val mongoClient: MongoClient = mockk()
  private val database: MongoDatabase = mockk()
  private val adapter = MongoStatsAdapter(mongoClient = mongoClient, databaseName = "test-db")

  init {
    every { mongoClient.getDatabase("test-db") } returns database
    every { database.runCommand(any<Bson>()) } returns Document("count", 1L).append("size", 1024L)
  }

  @Test
  fun `current lazily fetches on first read so callers never see a startup gap`() {
    every { database.listCollectionNames() } returns collectionsOf("users")

    val stats = adapter.current()

    assertThat(stats).extracting("name").containsExactly("users")
    verify(exactly = 1) { database.listCollectionNames() }
  }

  @Test
  fun `current is shared across readers instead of re-querying MongoDB per call`() {
    every { database.listCollectionNames() } returns collectionsOf("users", "playlists")

    val first = adapter.current()
    val second = adapter.current()

    assertThat(second).isEqualTo(first)
    verify(exactly = 1) { database.listCollectionNames() }
  }

  @Test
  fun `refresh replaces the cached stats so a later current sees updated values without re-querying`() {
    every { database.listCollectionNames() } returns collectionsOf("users")
    adapter.current()

    every { database.listCollectionNames() } returns collectionsOf("users", "playlists")
    val refreshed = adapter.refresh()

    assertThat(refreshed).extracting("name").containsExactlyInAnyOrder("users", "playlists")
    assertThat(adapter.current()).isEqualTo(refreshed)
    verify(exactly = 2) { database.listCollectionNames() }
  }

  @Test
  fun `a failed refresh keeps the previously cached values instead of propagating`() {
    every { database.listCollectionNames() } returns collectionsOf("users")
    val initial = adapter.current()

    every { database.listCollectionNames() } throws MongoException("mongo unreachable")
    val result = adapter.refresh()

    assertThat(result).isEqualTo(initial)
    assertThat(adapter.current()).isEqualTo(initial)
  }

  private fun collectionsOf(vararg names: String): ListCollectionNamesIterable {
    val iterable: ListCollectionNamesIterable = mockk()
    val cursor: MongoCursor<String> = mockk(relaxed = true)
    val remaining = names.toMutableList()
    every { cursor.hasNext() } answers { remaining.isNotEmpty() }
    every { cursor.next() } answers { remaining.removeAt(0) }
    every { iterable.iterator() } returns cursor
    return iterable
  }
}
