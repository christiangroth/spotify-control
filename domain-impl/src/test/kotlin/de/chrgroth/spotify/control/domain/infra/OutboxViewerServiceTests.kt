package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.model.infra.OutboxViewerPartition
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxAdminPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OutboxViewerServiceTests {

  private val tasksCache: OutboxViewerTasksCache = mockk()
  private val outboxAdmin: OutboxAdminPort = mockk()

  private val service = OutboxViewerService(tasksCache, outboxAdmin)

  @Test
  fun `getPartitions returns the cached partitions`() {
    val partitions = listOf(OutboxViewerPartition(key = "partition", tasks = emptyList()))
    every { tasksCache.current() } returns partitions

    assertThat(service.getPartitions()).isEqualTo(partitions)
  }

  @Test
  fun `wipeAll delegates to OutboxAdminPort and refreshes the tasks cache afterwards`() {
    every { outboxAdmin.wipeAll() } just runs
    every { tasksCache.refresh() } just runs

    service.wipeAll()

    verifyOrder {
      outboxAdmin.wipeAll()
      tasksCache.refresh()
    }
  }

  @Test
  fun `requeueStuckTasks delegates to OutboxAdminPort, refreshes the tasks cache and returns the cleared count`() {
    every { outboxAdmin.requeueStuckTasks("to-spotify-playback") } returns 2
    every { tasksCache.refresh() } just runs

    val clearedCount = service.requeueStuckTasks("to-spotify-playback")

    assertThat(clearedCount).isEqualTo(2)
    verifyOrder {
      outboxAdmin.requeueStuckTasks("to-spotify-playback")
      tasksCache.refresh()
    }
  }
}
