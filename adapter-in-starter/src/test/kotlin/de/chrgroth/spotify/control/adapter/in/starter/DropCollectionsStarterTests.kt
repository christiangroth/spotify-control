package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.DatabaseMigrationPort
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DropCollectionsStarterTests {

    private val databaseMigration: DatabaseMigrationPort = mockk(relaxed = true)
    private val starter = DropCollectionsStarter(databaseMigration)

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("DropCollectionsStarter-v1")
    }

    @Test
    fun `execute drops recently_partial_played collection`() {
        starter.execute()

        verify(exactly = 1) { databaseMigration.dropCollectionIfExists("recently_partial_played") }
    }
}
