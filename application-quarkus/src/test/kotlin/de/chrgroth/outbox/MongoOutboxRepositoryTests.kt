package de.chrgroth.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition
import de.chrgroth.quarkus.outbox.domain.OutboxPartitionStatus
import de.chrgroth.quarkus.outbox.domain.OutboxTaskPriority
import de.chrgroth.quarkus.outbox.domain.OutboxTaskStatus
import de.chrgroth.quarkus.outbox.domain.port.out.ArchivedTaskRepositoryPort
import de.chrgroth.quarkus.outbox.domain.port.out.PartitionRepositoryPort
import de.chrgroth.quarkus.outbox.domain.port.out.TaskRepositoryPort
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
    lateinit var taskRepository: TaskRepositoryPort

    @Inject
    lateinit var partitionRepository: PartitionRepositoryPort

    @Inject
    lateinit var archiveRepository: ArchivedTaskRepositoryPort

    private fun uniquePartition() = object : ApplicationOutboxPartition {
        override val key = "test-${UUID.randomUUID()}"
    }

    private fun event(dedupKey: String = "TestEvent:1") = object : ApplicationOutboxEvent {
        override val key = "TestEvent"
        override val partition = uniquePartition()
        override val priority = OutboxTaskPriority.NORMAL
        override val deduplicationKey = dedupKey
        override val serializePayload = ""
    }

    @Test
    fun `enqueue inserts a new task and claim retrieves it`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)

        val task = taskRepository.claim(partition)

        assertThat(task).isNotNull()
        assertThat(task!!.partition).isEqualTo(partition.key)
        assertThat(task.eventType).isEqualTo("TestEvent")
        assertThat(task.status).isEqualTo(OutboxTaskStatus.PROCESSING)
    }

    @Test
    fun `enqueue returns true for new task and false for duplicate PENDING`() {
        val partition = uniquePartition()

        val first = taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        val second = taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)

        assertThat(first).isTrue()
        assertThat(second).isFalse()
    }

    @Test
    fun `enqueue allows re-enqueue after FAILED task`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        val task = taskRepository.claim(partition)!!
        archiveRepository.appendFailed(task, "error")
        taskRepository.delete(task)

        val reEnqueued = taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)

        assertThat(reEnqueued).isTrue()
    }

    @Test
    fun `claim returns null when no tasks are available`() {
        val partition = uniquePartition()

        val task = taskRepository.claim(partition)

        assertThat(task).isNull()
    }

    @Test
    fun `claim respects nextRetryAt and skips tasks not yet due`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        val task = taskRepository.claim(partition)!!
        taskRepository.scheduleRetry(task, "temporary error", Instant.now().plus(Duration.ofMinutes(10)))

        val next = taskRepository.claim(partition)

        assertThat(next).isNull()
    }

    @Test
    fun `complete moves task to archive and no further claim is possible`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        val task = taskRepository.claim(partition)!!

        archiveRepository.append(task)
        taskRepository.delete(task)

        assertThat(taskRepository.claim(partition)).isNull()
    }

    @Test
    fun `fail with nextRetryAt makes task available again after delay`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        val task = taskRepository.claim(partition)!!

        taskRepository.scheduleRetry(task, "temporary error", Instant.now().minus(Duration.ofSeconds(1)))

        val retried = taskRepository.claim(partition)
        assertThat(retried).isNotNull()
        assertThat(retried!!.attempts).isEqualTo(1)
    }

    @Test
    fun `fail with null nextRetryAt makes task unavailable for claim`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        val task = taskRepository.claim(partition)!!

        archiveRepository.appendFailed(task, "permanent error")
        taskRepository.delete(task)

        assertThat(taskRepository.claim(partition)).isNull()
    }

    @Test
    fun `reschedule sets task back to PENDING without incrementing attempts`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        val task = taskRepository.claim(partition)!!

        taskRepository.reschedule(task, Instant.now().minus(Duration.ofSeconds(1)))

        val rescheduled = taskRepository.claim(partition)
        assertThat(rescheduled).isNotNull()
        assertThat(rescheduled!!.attempts).isEqualTo(0)
    }

    @Test
    fun `pausePartition and resume transitions partition status`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        partitionRepository.pause(partition, "rate limited", Instant.now().plus(Duration.ofMinutes(5)))

        val pausedInfo = partitionRepository.findOrCreate(partition)
        assertThat(pausedInfo.status).isEqualTo(OutboxPartitionStatus.PAUSED)

        partitionRepository.resume(partition)

        val activeInfo = partitionRepository.findOrCreate(partition)
        assertThat(activeInfo.status).isEqualTo(OutboxPartitionStatus.ACTIVE)
    }

    @Test
    fun `findOrCreate returns ACTIVE status when no partition document exists`() {
        val partition = uniquePartition()

        val info = partitionRepository.findOrCreate(partition)

        assertThat(info).isNotNull()
        assertThat(info.status).isEqualTo(OutboxPartitionStatus.ACTIVE)
    }

    @Test
    fun `resume creates ACTIVE partition document when none exists yet`() {
        val partition = uniquePartition()

        partitionRepository.resume(partition)

        val info = partitionRepository.findOrCreate(partition)
        assertThat(info).isNotNull()
        assertThat(info.status).isEqualTo(OutboxPartitionStatus.ACTIVE)
        assertThat(info.statusReason).isNull()
        assertThat(info.pausedUntil).isNull()
    }

    @Test
    fun `findOrCreate returns PAUSED partition after pause`() {
        val partition = uniquePartition()
        partitionRepository.pause(partition, "test reason", Instant.now().plus(Duration.ofMinutes(1)))

        val info = partitionRepository.findOrCreate(partition)

        assertThat(info).isNotNull()
        assertThat(info.status).isEqualTo(OutboxPartitionStatus.PAUSED)
        assertThat(info.statusReason).isEqualTo("test reason")
    }

    @Test
    fun `findOrCreate returns ACTIVE partition after resume`() {
        val partition = uniquePartition()
        partitionRepository.pause(partition, "test reason", Instant.now().plus(Duration.ofMinutes(1)))
        partitionRepository.resume(partition)

        val info = partitionRepository.findOrCreate(partition)

        assertThat(info).isNotNull()
        assertThat(info.status).isEqualTo(OutboxPartitionStatus.ACTIVE)
        assertThat(info.statusReason).isNull()
        assertThat(info.pausedUntil).isNull()
    }

    @Test
    fun `resetStaleProcessingTasks makes claimed tasks available again`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event(), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        taskRepository.claim(partition)!! // leaves task in PROCESSING

        taskRepository.resetStaleProcessing()

        val recovered = taskRepository.claim(partition)
        assertThat(recovered).isNotNull()
    }

    @Test
    fun `HIGH priority task is claimed before NORMAL priority task`() {
        val partition = uniquePartition()
        taskRepository.enqueue(partition, event("TestEvent:normal"), """{"id":"normal"}""", OutboxTaskPriority.NORMAL)
        taskRepository.enqueue(partition, event("TestEvent:high"), """{"id":"high"}""", OutboxTaskPriority.HIGH)

        val first = taskRepository.claim(partition)!!

        assertThat(first.priority).isEqualTo(OutboxTaskPriority.HIGH)
    }

    @Test
    fun `deleteArchiveEntriesOlderThan removes entries older than cutoff and keeps newer ones`() {
        val partition = uniquePartition()

        // Enqueue and complete two tasks
        taskRepository.enqueue(partition, event("TestEvent:1"), """{"id":"1"}""", OutboxTaskPriority.NORMAL)
        val oldTask = taskRepository.claim(partition)!!
        archiveRepository.append(oldTask)
        taskRepository.delete(oldTask)

        taskRepository.enqueue(partition, event("TestEvent:2"), """{"id":"2"}""", OutboxTaskPriority.NORMAL)
        val recentTask = taskRepository.claim(partition)!!
        archiveRepository.append(recentTask)
        taskRepository.delete(recentTask)

        // Delete entries older than 1 day in the future (i.e. delete everything)
        val deletedAll = archiveRepository.deleteOlderThan(Instant.now().plus(Duration.ofDays(1)))
        assertThat(deletedAll).isGreaterThanOrEqualTo(2)

        // Delete entries older than 1 day in the past (should not delete recently completed ones)
        taskRepository.enqueue(partition, event("TestEvent:3"), """{"id":"3"}""", OutboxTaskPriority.NORMAL)
        val newestTask = taskRepository.claim(partition)!!
        archiveRepository.append(newestTask)
        taskRepository.delete(newestTask)

        val deletedNone = archiveRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(1)))
        assertThat(deletedNone).isEqualTo(0)
    }
}
