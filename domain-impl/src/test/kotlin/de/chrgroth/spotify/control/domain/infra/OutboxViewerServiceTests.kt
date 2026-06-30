package de.chrgroth.spotify.control.domain.infra

import de.chrgroth.spotify.control.domain.port.out.infra.OutboxAdminPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class OutboxViewerServiceTests {

  private val outbox: OutboxPort = mockk()
  private val outboxAdmin: OutboxAdminPort = mockk()

  private val service = OutboxViewerService(outbox, outboxAdmin)

  @Test
  fun `wipeAll delegates to OutboxAdminPort`() {
    every { outboxAdmin.wipeAll() } just runs

    service.wipeAll()

    verify { outboxAdmin.wipeAll() }
  }
}
