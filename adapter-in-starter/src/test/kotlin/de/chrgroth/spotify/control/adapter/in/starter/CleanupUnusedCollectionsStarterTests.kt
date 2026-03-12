package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.DatabaseMigrationPort
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CleanupUnusedCollectionsStarterTests {

    private val databaseMigration: DatabaseMigrationPort = mockk(relaxed = true)
    private val starter = CleanupUnusedCollectionsStarter(databaseMigration)

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("CleanupUnusedCollectionsStarter-v1")
    }

    @Test
    fun `execute drops both collections in order`() {
        starter.execute()

        verifyOrder {
            databaseMigration.dropCollectionIfExists("recently_partial_played")
            databaseMigration.dropCollectionIfExists("spotify_recently_partial_played")
        }
        verify(exactly = 2) { databaseMigration.dropCollectionIfExists(any()) }
    }
}
