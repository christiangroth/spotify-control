package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import java.util.UUID

@QuarkusTest
@TestSecurity(user = "test-user-a")
class SettingsPageTests {

  @Inject
  lateinit var playlistRepository: PlaylistRepositoryPort

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Inject
  lateinit var appArtistRepository: AppArtistRepositoryPort

  @Inject
  lateinit var playlistPort: PlaylistPort

  @BeforeEach
  fun ensureCurrentUser() {
    val now = Clock.System.now()
    userRepository.upsert(
      User(
        spotifyUserId = UserId("test-user-a"),
        displayName = "Test User",
        encryptedAccessToken = "encrypted-access",
        encryptedRefreshToken = "encrypted-refresh",
        tokenExpiresAt = now + 1.hours,
        lastLoginAt = now,
      ),
    )
  }

  @Test
  fun `playlists settings page is available and displays playlists heading`() {
    given()
      .`when`()
      .get("/playlists/settings")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playlists"))
  }

  @Test
  fun `playlist settings page displays artists column header`() {
    val now = Clock.System.now()
    playlistRepository.replaceAll(
      listOf(
        PlaylistInfo(
          spotifyPlaylistId = "playlist-${UUID.randomUUID()}",
          snapshotId = "snap-1",
          lastSnapshotIdSyncTime = now - 1.hours,
          name = "Playlist For Artists Column Test",
          syncStatus = PlaylistSyncStatus.ACTIVE,
        ),
      ),
    )
    playlistPort.rebuildPlaylistSettingsView()

    given()
      .`when`()
      .get("/playlists/settings")
      .then()
      .statusCode(200)
      .body(containsString("Artists"))
  }

  @Test
  fun `playlist settings page displays computed artist stats for a synced playlist`() {
    val now = Clock.System.now()
    val playlistId = "playlist-${UUID.randomUUID()}"
    playlistRepository.replaceAll(
      listOf(
        PlaylistInfo(
          spotifyPlaylistId = playlistId,
          snapshotId = "snap-1",
          lastSnapshotIdSyncTime = now - 1.hours,
          name = "Playlist For Artists Cell Value Test",
          syncStatus = PlaylistSyncStatus.ACTIVE,
        ),
      ),
    )
    playlistRepository.save(
      Playlist(
        spotifyPlaylistId = playlistId,
        tracks = listOf(
          PlaylistTrack(
            trackId = TrackId("t1"),
            artistIds = listOf(ArtistId("artists-cell-a1"), ArtistId("artists-cell-a2")),
            albumId = null,
          ),
        ),
      ),
    )
    playlistPort.rebuildPlaylistSettingsView()

    given()
      .`when`()
      .get("/playlists/settings")
      .then()
      .statusCode(200)
      .body(containsString("1 <button"))
      .body(containsString("badge rounded-pill text-bg-secondary"))
      .body(containsString(">1</span></button>"))
  }

  @Test
  fun `playlist settings page reflects artist already present in catalog`() {
    val now = Clock.System.now()
    val playlistId = "playlist-${UUID.randomUUID()}"
    val knownArtistId = "artists-known-${UUID.randomUUID()}"
    val unknownArtistId = "artists-unknown-${UUID.randomUUID()}"
    appArtistRepository.upsertAll(
      listOf(
        AppArtist(id = ArtistId(knownArtistId), artistName = "Known Artist", lastSync = now),
      ),
    )
    playlistRepository.replaceAll(
      listOf(
        PlaylistInfo(
          spotifyPlaylistId = playlistId,
          snapshotId = "snap-1",
          lastSnapshotIdSyncTime = now - 1.hours,
          name = "Playlist For Known Artist Test",
          syncStatus = PlaylistSyncStatus.ACTIVE,
        ),
      ),
    )
    playlistRepository.save(
      Playlist(
        spotifyPlaylistId = playlistId,
        tracks = listOf(
          PlaylistTrack(
            trackId = TrackId("t1"),
            artistIds = listOf(ArtistId(knownArtistId), ArtistId(unknownArtistId)),
            albumId = null,
          ),
        ),
      ),
    )
    playlistPort.rebuildPlaylistSettingsView()

    given()
      .`when`()
      .get("/playlists/settings")
      .then()
      .statusCode(200)
      .body(containsString("Playlist For Known Artist Test"))
      .body(not(containsString("badge rounded-pill text-bg-secondary")))
  }

  @Test
  fun `playlist settings page displays sync now button`() {
    given()
      .`when`()
      .get("/playlists/settings")
      .then()
      .statusCode(200)
      .body(containsString("Sync Now"))
  }

  @Test
  fun `playlists checks page is available at new url and displays playlist checks heading`() {
    given()
      .`when`()
      .get("/playlists/checks")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playlist Checks"))
  }

  @Test
  fun `playback page is available at new url and displays playback heading`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playback"))
  }

  @Test
  fun `playback page displays playback event stats tiles`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .body(containsString("Playback Events (Last 30 Days)"))
      .body(containsString("Total Playback Events"))
  }

  @Test
  fun `playback page stats tiles link to playback events page without date by default`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .body(containsString("""href="/playback/events" """))
  }

  @Test
  fun `playback page stats tiles link to playback events page for requested date`() {
    given()
      .`when`()
      .get("/playback?date=2026-06-01")
      .then()
      .statusCode(200)
      .body(containsString("""href="/playback/events?date=2026-06-01" """))
  }

  @Test
  fun `playback page displays recreate playback data button`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .body(containsString("Recreate Playback Data"))
  }

  @Test
  fun `playback page displays sync missing artists button`() {
    given()
      .`when`()
      .get("/playback")
      .then()
      .statusCode(200)
      .body(containsString("Sync Missing Artists"))
  }

  @Test
  fun `playback events page is available and displays heading`() {
    given()
      .`when`()
      .get("/playback/events")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Playback Events"))
  }

  @Test
  fun `stats page is available and displays stats heading`() {
    given()
      .`when`()
      .get("/stats")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Stats"))
      .body(containsString("""data-testid="stats-tabs""""))
      .body(containsString("Day"))
      .body(containsString("Week"))
      .body(containsString("Month"))
      .body(containsString("Quarter"))
      .body(containsString("Year"))
  }

  @Test
  fun `stats page displays aggregation detail sections`() {
    given()
      .`when`()
      .get("/stats")
      .then()
      .statusCode(200)
      .body(containsString("Top Artists"))
      .body(containsString("Top Albums"))
      .body(containsString("Top Tracks"))
      .body(containsString("""data-testid="stats-activity-section-week""""))
      .body(not(containsString("""data-testid="stats-activity-section-day"""")))
  }

  @Test
  fun `stats page does not show a selected date label for day tab without date`() {
    given()
      .`when`()
      .get("/stats")
      .then()
      .statusCode(200)
      .body(not(containsString("Selected (")))
  }

  @Test
  fun `stats page shows only the requested date for day tab when date is given`() {
    given()
      .`when`()
      .get("/stats?date=2026-06-01")
      .then()
      .statusCode(200)
      .body(containsString("Selected (2026-06-01)"))
  }

  @Test
  fun `stats page links day tab periods to playback events for the same date`() {
    given()
      .`when`()
      .get("/stats?date=2026-06-01")
      .then()
      .statusCode(200)
      .body(containsString("""data-testid="stats-day-events-link""""))
      .body(containsString("/playback/events?date=2026-06-01"))
  }

}
