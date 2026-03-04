package de.chrgroth.spotify.control.adapter.`in`.starter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HelloWorldStarterTests {

    private val starter = HelloWorldStarter()

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("HelloWorldStarter-v1")
    }

    @Test
    fun `execute runs without throwing`() {
        starter.execute()
    }
}
