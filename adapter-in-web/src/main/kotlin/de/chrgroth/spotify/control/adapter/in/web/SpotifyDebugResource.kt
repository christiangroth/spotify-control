package de.chrgroth.spotify.control.adapter.`in`.web

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistAlbumsPage
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage
import de.chrgroth.spotify.control.domain.model.playlist.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.`in`.playlist.PlaylistPort
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAccessTokenPort
import de.chrgroth.spotify.control.domain.port.out.user.SpotifyAuthPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import kotlin.time.Instant

@Path("/spotify-debug")
@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught", "TooManyFunctions")
class SpotifyDebugResource(
  @param:Location("spotify-debug.html")
  private val spotifyDebugTemplate: Template,
  private val catalogBrowser: CatalogBrowserPort,
  private val playlist: PlaylistPort,
  private val securityIdentity: SecurityIdentity,
  private val spotifyAccessToken: SpotifyAccessTokenPort,
  private val spotifyAuth: SpotifyAuthPort,
  private val spotifyPlayback: SpotifyPlaybackPort,
  private val spotifyPlaylist: SpotifyPlaylistPort,
  private val spotifyCatalog: SpotifyCatalogPort,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun page(): TemplateInstance = spotifyDebugTemplate.instance()

  @GET
  @Path("/api/artists")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun searchArtists(@QueryParam("filter") filter: String?): List<PickerOption> {
    if (filter.isNullOrBlank()) return emptyList()
    return catalogBrowser.getArtists(filter).map { PickerOption(it.artistId, it.artistName) }
  }

  @GET
  @Path("/api/albums")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun searchAlbums(@QueryParam("filter") filter: String?): List<PickerOption> {
    if (filter.isNullOrBlank()) return emptyList()
    return catalogBrowser.getAlbums(filter).map { PickerOption(it.albumId, it.toPickerLabel()) }
  }

  @GET
  @Path("/api/playlists")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun searchPlaylists(@QueryParam("filter") filter: String?): List<PickerOption> {
    if (filter.isNullOrBlank()) return emptyList()
    val userId = UserId(securityIdentity.principal.name)
    val filterLower = filter.lowercase()
    return playlist.getPlaylists(userId)
      .filter { it.name.lowercase().contains(filterLower) }
      .map { PickerOption(it.spotifyPlaylistId, it.name) }
  }

  @POST
  @Path("/execute/{operation}")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  fun execute(@PathParam("operation") operation: String, @Context uriInfo: UriInfo): Response {
    val params = uriInfo.queryParameters.entries
      .associate { (key, values) -> key to values.firstOrNull()?.takeIf { it.isNotBlank() } }
    val userId = UserId(securityIdentity.principal.name)

    val accessToken = try {
      spotifyAccessToken.getValidAccessToken()
    } catch (e: Exception) {
      return errorResponse(operation, "Failed to obtain a valid Spotify access token: ${e.message}")
    }

    return try {
      when (operation) {
        "current-user" -> spotifyAuth.getUserProfile(accessToken).toResponse(operation) { it.toJson() }

        "currently-playing" -> spotifyPlayback.getCurrentlyPlaying(userId, accessToken).toResponse(operation) { it?.toJson() }

        "recently-played" -> {
          val after = params["after"]?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
          spotifyPlayback.getRecentlyPlayed(userId, accessToken, after).toResponse(operation) { items -> items.map { it.toJson() } }
        }

        "user-playlists" -> spotifyPlaylist.getPlaylists(userId, accessToken).toResponse(operation) { items -> items.map { it.toJson() } }

        "playlist-tracks" -> {
          val playlistId = params["playlistId"] ?: return errorResponse(operation, "playlistId is required")
          val pageUrl = params["pageUrl"]
          spotifyPlaylist.getPlaylistTracksPage(userId, accessToken, playlistId, pageUrl).toResponse(operation) { it.toJson() }
        }

        "artist" -> {
          val artistId = params["artistId"] ?: return errorResponse(operation, "artistId is required")
          spotifyCatalog.getArtist(userId, accessToken, artistId).toResponse(operation) { it?.toJson() }
        }

        "artist-albums" -> {
          val artistId = params["artistId"] ?: return errorResponse(operation, "artistId is required")
          val nextUrl = params["nextUrl"]
          spotifyCatalog.getArtistAlbumsPage(userId, accessToken, artistId, nextUrl).toResponse(operation) { it.toJson() }
        }

        "album" -> {
          val albumId = params["albumId"] ?: return errorResponse(operation, "albumId is required")
          spotifyCatalog.getAlbum(userId, accessToken, albumId).toResponse(operation) { it.toJson() }
        }

        "album-tracks" -> {
          val albumId = params["albumId"] ?: return errorResponse(operation, "albumId is required")
          spotifyCatalog.getAlbum(userId, accessToken, albumId).fold(
            ifLeft = { errorResponse(operation, it.code) },
            ifRight = { syncResult -> fetchAlbumTracks(operation, userId, accessToken, syncResult.album) },
          )
        }

        else -> errorResponse(operation, "Unknown operation: $operation")
      }
    } catch (e: Exception) {
      errorResponse(operation, "Unexpected error: ${e.message}")
    }
  }

  private fun fetchAlbumTracks(operation: String, userId: UserId, accessToken: AccessToken, album: AppAlbum): Response =
    spotifyCatalog.getAlbumTracks(userId, accessToken, album).toResponse(operation) { tracks -> tracks.map { it.toJson() } }

  private fun <T> Either<DomainError, T>.toResponse(operation: String, mapper: (T) -> Any?): Response =
    fold(
      ifLeft = { errorResponse(operation, it.code) },
      ifRight = { Response.ok(mapOf("operation" to operation, "status" to "ok", "data" to mapper(it))).build() },
    )

  private fun errorResponse(operation: String, message: String): Response =
    Response.status(Response.Status.BAD_GATEWAY)
      .entity(mapOf("operation" to operation, "status" to "error", "message" to message))
      .build()

  private fun SpotifyProfile.toJson() = mapOf(
    "id" to id.value,
    "displayName" to displayName,
  )

  private fun CurrentlyPlayingItem.toJson() = mapOf(
    "trackId" to trackId.value,
    "trackName" to trackName,
    "artistIds" to artistIds.map { it.value },
    "artistNames" to artistNames,
    "albumId" to albumId?.value,
    "progressMs" to progressMs,
    "durationMs" to durationMs,
    "isPlaying" to isPlaying,
    "observedAt" to observedAt.toString(),
    "startTime" to startTime.toString(),
  )

  private fun RecentlyPlayedItem.toJson() = mapOf(
    "trackId" to trackId.value,
    "trackName" to trackName,
    "artistIds" to artistIds.map { it.value },
    "artistNames" to artistNames,
    "albumId" to albumId?.value,
    "albumName" to albumName,
    "imageLink" to imageLink,
    "durationSeconds" to durationSeconds,
    "playedAt" to playedAt.toString(),
    "startTime" to startTime?.toString(),
  )

  private fun SpotifyPlaylistItem.toJson() = mapOf(
    "id" to id,
    "name" to name,
    "snapshotId" to snapshotId,
    "ownerId" to ownerId,
  )

  private fun PlaylistTrack.toJson() = mapOf(
    "trackId" to trackId.value,
    "artistIds" to artistIds.map { it.value },
    "albumId" to albumId?.value,
  )

  private fun PlaylistTracksPage.toJson() = mapOf(
    "snapshotId" to snapshotId,
    "tracks" to tracks.map { it.toJson() },
    "nextUrl" to nextUrl,
  )

  private fun AppArtist.toJson() = mapOf(
    "id" to id.value,
    "artistName" to artistName,
    "imageLink" to imageLink,
    "type" to type,
    "lastSync" to lastSync.toString(),
    "blockedFromAggregation" to blockedFromAggregation,
  )

  private fun AppAlbum.toJson() = mapOf(
    "id" to id.value,
    "title" to title,
    "totalTracks" to totalTracks,
    "imageLink" to imageLink,
    "releaseDate" to releaseDate,
    "releaseDatePrecision" to releaseDatePrecision,
    "type" to type,
    "artistId" to artistId?.value,
    "artistName" to artistName,
    "additionalArtistIds" to additionalArtistIds?.map { it.value },
    "additionalArtistNames" to additionalArtistNames,
    "lastSync" to lastSync.toString(),
  )

  private fun ArtistAlbumsPage.toJson() = mapOf(
    "albums" to albums.map { it.toJson() },
    "nextUrl" to nextUrl,
  )

  private fun AppTrack.toJson() = mapOf(
    "id" to id.value,
    "title" to title,
    "albumId" to albumId?.value,
    "albumName" to albumName,
    "artistId" to artistId.value,
    "artistName" to artistName,
    "additionalArtistIds" to additionalArtistIds.map { it.value },
    "additionalArtistNames" to additionalArtistNames,
    "discNumber" to discNumber,
    "durationMs" to durationMs,
    "trackNumber" to trackNumber,
    "type" to type,
    "lastSync" to lastSync.toString(),
  )

  private fun AlbumSyncResult.toJson() = mapOf(
    "album" to album.toJson(),
    "tracks" to tracks.map { it.toJson() },
  )

  private fun AlbumBrowseItem.toPickerLabel(): String {
    val albumTitle = title ?: UNTITLED_ALBUM
    return if (artistName != null) "$albumTitle — $artistName" else albumTitle
  }

  data class PickerOption(val id: String, val name: String)

  companion object {
    private const val UNTITLED_ALBUM = "(untitled)"
  }
}
