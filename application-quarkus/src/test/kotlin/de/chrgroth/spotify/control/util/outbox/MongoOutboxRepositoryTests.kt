package de.chrgroth.spotify.control.util.outbox

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

@QuarkusTest
class MongoOutboxRepositoryTests {

    @Inject
    lateinit var repository: MongoOutboxRepository

    private fun uniquePartition() = object : OutboxPartition {
        override val key = "test-${UUID.randomUUID()}"
    }

    private fun event(dedupKey: String = "TestEvent:1") = object : OutboxEvent {
        override val key = "TestEvent"
        override fun deduplicationKey() = dedupKey
    }

    @Test
    fun `enqueue inserts a new task and claim retrieves it`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")

        val task = repository.claim(partition)

        assertThat(task).isNotNull()
        assertThat(task!!.partition).isEqualTo(partition.key)
        assertThat(task.eventType).isEqualTo("TestEvent")
        assertThat(task.status).isEqualTo(OutboxTaskStatus.PROCESSING)
    }

    @Test
    fun `enqueue returns true for new task and false for duplicate PENDING`() {
        val partition = uniquePartition()

        val first = repository.enqueue(partition, event(), """{"id":"1"}""")
        val second = repository.enqueue(partition, event(), """{"id":"1"}""")

        assertThat(first).isTrue()
        assertThat(second).isFalse()
    }

    @Test
    fun `enqueue allows re-enqueue after FAILED task`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")
        val task = repository.claim(partition)!!
        repository.fail(task, "error", null)

        val reEnqueued = repository.enqueue(partition, event(), """{"id":"1"}""")

        assertThat(reEnqueued).isTrue()
    }

    @Test
    fun `claim returns null when no tasks are available`() {
        val partition = uniquePartition()

        val task = repository.claim(partition)

        assertThat(task).isNull()
    }

    @Test
    fun `claim respects nextRetryAt and skips tasks not yet due`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")
        val task = repository.claim(partition)!!
        repository.fail(task, "temporary error", Instant.now().plus(Duration.ofMinutes(10)))

        val next = repository.claim(partition)

        assertThat(next).isNull()
    }

    @Test
    fun `complete moves task to archive and no further claim is possible`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")
        val task = repository.claim(partition)!!

        repository.complete(task)

        assertThat(repository.claim(partition)).isNull()
    }

    @Test
    fun `fail with nextRetryAt makes task available again after delay`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")
        val task = repository.claim(partition)!!

        repository.fail(task, "temporary error", Instant.now().minus(Duration.ofSeconds(1)))

        val retried = repository.claim(partition)
        assertThat(retried).isNotNull()
        assertThat(retried!!.attempts).isEqualTo(1)
    }

    @Test
    fun `fail with null nextRetryAt makes task unavailable for claim`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")
        val task = repository.claim(partition)!!

        repository.fail(task, "permanent error", null)

        assertThat(repository.claim(partition)).isNull()
    }

    @Test
    fun `reschedule sets task back to PENDING without incrementing attempts`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")
        val task = repository.claim(partition)!!

        repository.reschedule(task, Instant.now().minus(Duration.ofSeconds(1)))

        val rescheduled = repository.claim(partition)
        assertThat(rescheduled).isNotNull()
        assertThat(rescheduled!!.attempts).isEqualTo(0)
    }

    @Test
    fun `pausePartition blocks claim and activatePartition restores it`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")
        repository.pausePartition(partition, "rate limited", Instant.now().plus(Duration.ofMinutes(5)))

        assertThat(repository.claim(partition)).isNull()

        repository.activatePartition(partition)

        assertThat(repository.claim(partition)).isNotNull()
    }

    @Test
    fun `findPartition returns null when no partition document exists`() {
        val partition = uniquePartition()

        assertThat(repository.findPartition(partition)).isNull()
    }

    @Test
    fun `findPartition returns PAUSED partition after pause`() {
        val partition = uniquePartition()
        repository.pausePartition(partition, "test reason", Instant.now().plus(Duration.ofMinutes(1)))

        val doc = repository.findPartition(partition)

        assertThat(doc).isNotNull()
        assertThat(doc!!.status).isEqualTo(OutboxPartitionStatus.PAUSED.name)
        assertThat(doc.statusReason).isEqualTo("test reason")
    }

    @Test
    fun `findPartition returns ACTIVE partition after activate`() {
        val partition = uniquePartition()
        repository.pausePartition(partition, "test reason", Instant.now().plus(Duration.ofMinutes(1)))
        repository.activatePartition(partition)

        val doc = repository.findPartition(partition)

        assertThat(doc).isNotNull()
        assertThat(doc!!.status).isEqualTo(OutboxPartitionStatus.ACTIVE.name)
        assertThat(doc.statusReason).isNull()
        assertThat(doc.pausedUntil).isNull()
    }

    @Test
    fun `resetStaleProcessingTasks makes claimed tasks available again`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event(), """{"id":"1"}""")
        repository.claim(partition)!! // leaves task in PROCESSING

        repository.resetStaleProcessingTasks()

        val recovered = repository.claim(partition)
        assertThat(recovered).isNotNull()
    }

    @Test
    fun `HIGH priority task is claimed before NORMAL priority task`() {
        val partition = uniquePartition()
        repository.enqueue(partition, event("TestEvent:normal"), """{"id":"normal"}""", OutboxTaskPriority.NORMAL)
        repository.enqueue(partition, event("TestEvent:high"), """{"id":"high"}""", OutboxTaskPriority.HIGH)

        val first = repository.claim(partition)!!

        assertThat(first.priority).isEqualTo(OutboxTaskPriority.HIGH)
    }

    @Test
    fun `deleteArchiveEntriesOlderThan removes entries older than cutoff and keeps newer ones`() {
        val partition = uniquePartition()

        // Enqueue and complete two tasks
        repository.enqueue(partition, event("TestEvent:1"), """{"id":"1"}""")
        val oldTask = repository.claim(partition)!!
        repository.complete(oldTask)

        repository.enqueue(partition, event("TestEvent:2"), """{"id":"2"}""")
        val recentTask = repository.claim(partition)!!
        repository.complete(recentTask)

        // Delete entries older than 1 day in the future (i.e. delete everything)
        val deletedAll = repository.deleteArchiveEntriesOlderThan(Instant.now().plus(Duration.ofDays(1)))
        assertThat(deletedAll).isGreaterThanOrEqualTo(2)

        // Delete entries older than 1 day in the past (should not delete recently completed ones)
        repository.enqueue(partition, event("TestEvent:3"), """{"id":"3"}""")
        val newestTask = repository.claim(partition)!!
        repository.complete(newestTask)

        val deletedNone = repository.deleteArchiveEntriesOlderThan(Instant.now().minus(Duration.ofDays(1)))
        assertThat(deletedNone).isEqualTo(0)
    }
}
