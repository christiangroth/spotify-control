package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.domain.port.out.DatabaseMigrationPort
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RenameCollectionsStarterTests {

    private val databaseMigration: DatabaseMigrationPort = mockk(relaxed = true)
    private val starter = RenameCollectionsStarter(databaseMigration)

    @Test
    fun `id is stable`() {
        assertThat(starter.id).isEqualTo("RenameCollectionsStarter-v1")
    }

    @Test
    fun `execute renames all five collections in order`() {
        starter.execute()

        verifyOrder {
            databaseMigration.renameCollectionIfExists("user", "app_user")
            databaseMigration.renameCollectionIfExists("playlist", "spotify_playlist")
            databaseMigration.renameCollectionIfExists("playlist_metadata", "spotify_playlist_metadata")
            databaseMigration.renameCollectionIfExists("recently_played", "spotify_recently_played")
            databaseMigration.renameCollectionIfExists("currently_playing", "spotify_currently_playing")
        }
        verify(exactly = 5) { databaseMigration.renameCollectionIfExists(any(), any()) }
    }
}
