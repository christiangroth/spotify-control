package de.chrgroth.starters

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import jakarta.enterprise.inject.Instance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.stream.Stream

class StarterServiceTests {

    private val repository: StarterDocumentRepository = mockk()
    private val meterRegistry = SimpleMeterRegistry()
    private val starters: Instance<Starter> = mockk()

    private val service = StarterService(starters, repository, meterRegistry)

    @BeforeEach
    fun setup() {
        every { starters.stream() } returns Stream.empty()
    }

    @Test
    fun `runAll returns true when there are no starters`() {
        val result = service.runAll()

        assertThat(result).isTrue()
    }

    @Test
    fun `runAll skips starter that already succeeded`() {
        val starter = mockk<Starter> { every { id } returns "starter-1" }
        every { starters.stream() } returns Stream.of(starter)
        val existingDoc = StarterDocument().apply {
            starterId = "starter-1"
            lastStatus = StarterStatus.SUCCEEDED.name
            executions = emptyList()
        }
        every { repository.findById("starter-1") } returns existingDoc

        val result = service.runAll()

        assertThat(result).isTrue()
        verify(exactly = 0) { starter.execute() }
    }

    @Test
    fun `runAll executes and persists success for a pending starter`() {
        val starter = mockk<Starter> {
            every { id } returns "starter-1"
            justRun { execute() }
        }
        every { starters.stream() } returns Stream.of(starter)
        every { repository.findById("starter-1") } returns null
        justRun { repository.persistOrUpdate(any<StarterDocument>()) }

        val result = service.runAll()

        assertThat(result).isTrue()
        verify { starter.execute() }
        verify {
            repository.persistOrUpdate(
                match<StarterDocument> { it.starterId == "starter-1" && it.lastStatus == StarterStatus.SUCCEEDED.name },
            )
        }
    }

    @Test
    fun `runAll records failure and returns false when starter throws`() {
        val starter = mockk<Starter> {
            every { id } returns "starter-fail"
            every { execute() } throws RuntimeException("boom")
        }
        every { starters.stream() } returns Stream.of(starter)
        every { repository.findById("starter-fail") } returns null
        justRun { repository.persistOrUpdate(any<StarterDocument>()) }

        val result = service.runAll()

        assertThat(result).isFalse()
        verify {
            repository.persistOrUpdate(
                match<StarterDocument> { it.starterId == "starter-fail" && it.lastStatus == StarterStatus.FAILED.name },
            )
        }
    }

    @Test
    fun `runAll processes starters in id order`() {
        val order = mutableListOf<String>()
        val starterB = mockk<Starter> {
            every { id } returns "b-starter"
            every { execute() } answers { order.add("b-starter"); Unit }
        }
        val starterA = mockk<Starter> {
            every { id } returns "a-starter"
            every { execute() } answers { order.add("a-starter"); Unit }
        }
        every { starters.stream() } returns Stream.of(starterB, starterA)
        every { repository.findById(any()) } returns null
        justRun { repository.persistOrUpdate(any<StarterDocument>()) }

        service.runAll()

        assertThat(order).containsExactly("a-starter", "b-starter")
    }
}
