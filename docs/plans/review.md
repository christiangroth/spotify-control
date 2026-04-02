# Backend Code Review

Reviewed against the coding guidelines in `docs/coding-guidelines/` and the architectural rules in `docs/arc42/arc42.md`.

---

## domain-api

* No violations found. The module is clean: all models are plain `data class` / `sealed class`, port interfaces carry no framework annotations, and the package structure (`model/`, `port/in/`, `port/out/`) is consistent and well-organized.

---

## domain-impl

* **`PlaybackService.kt` – mixed indentation.** The methods `enqueueFetchRecentlyPlayed` and `fetchRecentlyPlayed` (and their private helper `convertPartialPlays`) use 2-space indentation, while the rest of the file uses 4-space. The same inconsistency appears in the `// --- Recently Played ---` block. All methods in the file should use 4-space indentation.
* **`DashboardService.kt` – private extension function placed outside the class.** `private fun AppTrack.allArtistIds(): List<String>` is defined at file scope (last line of the file) rather than as a private member of `DashboardService`. Moving it inside the class improves encapsulation and makes the scope of the helper explicit.
* **`HealthService.kt` – infrastructure workaround without documentation.** The private `TcclContext` class and `tcclContext()` function are defined at file scope. They work around a Quarkus/coroutines classloader-propagation issue but carry no comment explaining the reason. A concise KDoc or inline comment is needed so the purpose is clear to future maintainers.
* **`SyncController.kt` – data class and service in the same file.** `CatalogSyncRequest` is a domain model that is co-located with `SyncController` in one file. Consider moving it to its own file for consistency with every other domain model in the project.

---

## adapter-in-outbox

* No violations found. `DomainOutboxTaskDispatcher` and `OutboxPartitionEventAdapter` are clean, focused, and correctly delegate to inbound ports only.

---

## adapter-in-scheduler

* No violations found. All scheduler jobs are minimal, correctly using constructor injection and delegating immediately to an inbound port.

---

## adapter-in-starter

* No violations found. Both starters are small and focused.

---

## adapter-in-web

* **Inconsistent indentation throughout the module.** `DashboardResource`, `LoginResource`, `PlaybackSettingsResource`, `PlaylistSettingsResource`, `CatalogResource`, and `OAuthResource` use 2-space indentation; `HealthResource`, `HealthSseAdapter`, and `DashboardSseAdapter` use 4-space. All classes in the module should use the same indentation style.
* **`PlaylistSettingsResource` – direct injection of outbound repository ports.** The resource injects `PlaylistRepositoryPort` and `UserRepositoryPort` directly for rendering the view. This bypasses the domain layer. Read queries used exclusively for view rendering should be exposed through inbound ports (or a dedicated read-model port) so the adapter-in layer only depends on `port/in` interfaces.
* **`DashboardResource` and `PlaybackSettingsResource` – direct injection of `UserRepositoryPort`.** Same concern as above: inbound adapters call an outbound repository port directly instead of going through an inbound port.
* **`OAuthResource` – field injection for configuration properties.** Configuration properties `clientId`, `redirectUri`, and `accountsBaseUrl` are injected using `@ConfigProperty` on `lateinit var` fields instead of constructor parameters with `@param:ConfigProperty`. Every other resource in the module uses constructor injection; `OAuthResource` should be aligned.
* **`OAuthResource` – trailing blank lines at end of file** (two extra blank lines after the closing brace). The project style is one blank line maximum.
* **`PlaylistSettingsResource.PlaylistRow` – date formatting duplicated inside a nested data class.** The computed property `lastSyncTimeFormatted` performs its own date-time formatting using `ZoneId.systemDefault()` and a `DateTimeFormatter`. This is inconsistent with `TemplateFormattingExtensions`, which centralises all formatting logic and always formats in UTC. The `PlaylistRow` formatting logic should either use `TemplateFormattingExtensions` or be aligned to UTC.
* **`PlaybackSettingsResource.playback()` – unnecessary local variable.** `val limit = PAGE_SIZE` is created and then immediately passed to the template data. The variable adds no clarity; using `PAGE_SIZE` directly is cleaner.

---

## adapter-out-config

* **`ConfigurationInfoAdapter` – `@ConfigProperty` without `@param:` target.** Constructor parameters `maskedConfigKeys` and `maskedEnvKeys` use `@ConfigProperty` without the `@param:` use-site target. All other adapters in the project consistently use `@param:ConfigProperty` for constructor-injected config. Align to the same style.
* **`ConfigurationInfoAdapter` – redundant intermediate vals.** `configKeyMasks` and `envKeyMasks` are assigned directly from the constructor parameters of the same name. Either rename the constructor parameters to match the private-val names, or remove the intermediate assignments.

---

## adapter-out-mongodb

* **Mixed injection styles across adapters.** `UserRepositoryAdapter`, `AppPlaybackRepositoryAdapter`, `CurrentlyPlayingRepositoryAdapter`, `RecentlyPlayedRepositoryAdapter`, `RecentlyPartialPlayedRepositoryAdapter`, `AppArtistRepositoryAdapter`, `AppAlbumRepositoryAdapter`, `AppTrackDataRepositoryAdapter`, `PlaylistRepositoryAdapter`, and `AppPlaylistCheckRepositoryAdapter` use `@Inject lateinit var` field injection. `PlaybackEventViewerRepositoryAdapter`, `DatabaseMigrationAdapter`, and `MongoStatsAdapter` use constructor injection. The project style for new adapters is constructor injection; the remaining adapters should be aligned.
* **Magic strings for MongoDB field names scattered across multiple adapters.** `PlaybackEventViewerRepositoryAdapter` correctly defines constants (`SPOTIFY_USER_ID_FIELD`, `PLAYED_AT_FIELD`, `OBSERVED_AT_FIELD`) in its companion object. The following adapters use raw string literals for the same or related fields instead of shared constants:
  * `UserRepositoryAdapter`: `"_id"`, `"displayName"`, `"encryptedAccessToken"`, `"encryptedRefreshToken"`, `"tokenExpiresAt"`, `"lastLoginAt"`, `"createdAt"`
  * `AppPlaybackRepositoryAdapter`: `"spotifyUserId"`, `"playedAt"`, `"trackId"`, `"secondsPlayed"` (duplicated across Panache queries, Filters, Aggregates, and `distinct` call)
  * `CurrentlyPlayingRepositoryAdapter`: `"spotifyUserId"`, `"trackId"`, `"observedAt"`, `"progressMs"`, `"isPlaying"`
  * `AppArtistRepositoryAdapter`: `"_id"`, `"artistName"`, `"imageLink"`, `"type"`, `"lastSync"`, `"playbackProcessingStatus"`
  * `AppAlbumRepositoryAdapter`: `"_id"`, `"totalTracks"`, `"title"`, `"imageLink"`, `"releaseDate"`, `"releaseDatePrecision"`, `"artistId"`, `"artistName"`, `"additionalArtistIds"`, `"additionalArtistNames"`, `"lastSync"`
  * `MongoIndexInitializer`: all field names used to build index documents (e.g. `"spotifyUserId"`, `"playedAt"`, `"trackId"`, `"secondsPlayed"`, `"observedAt"`, `"playbackProcessingStatus"`, `"artistId"`, `"albumId"`, `"syncStatus"`, `"playlistId"`, `"succeeded"`)
  * Centralizing these into per-document companion-object constants (as `PlaybackEventViewerRepositoryAdapter` does) would prevent typos and allow `MongoIndexInitializer` to reference the same values.
* **`AppTrackDataRepositoryAdapter.kt` line 67 – missing newline after opening brace.** The statement `if (trackIds.isEmpty()) return emptyList()` appears on the same line as the function signature's opening `{`. This is a formatting error that reduces readability.
* **`PlaylistRepositoryAdapter.saveAll` – misleading method name.** The implementation deletes all records for a user and re-inserts them (delete-all + insert), which is a destructive replace operation. The name `saveAll` suggests an additive save; a name such as `replaceAll` or `setAll` would communicate the actual semantics.
* **`PlaylistRepositoryAdapter.setAllSyncInactive` – magic string `"syncStatus"`.** The `Updates.set("syncStatus", ...)` call uses a raw string literal that is also present in the Panache queries in the same class. This should use a constant.

---

## adapter-out-outbox

* **`OutboxPortAdapter.kt` – 2-space indentation.** The entire file uses 2-space indentation while all other `adapter-out-*` modules use 4-space. Align to 4-space.

---

## adapter-out-scheduler

* No violations found.

---

## adapter-out-slack

* **`SlackNotificationAdapter` implements two separate port interfaces.** The adapter implements both `OutboxPartitionObserver` and `PlaylistCheckNotificationPort`. The architectural guideline states that a single adapter class should implement exactly one port interface (one concern per class). The two notification concerns should be split into separate adapter classes.

---

## adapter-out-spotify

* **`SpotifyPlaybackService`, `SpotifyPlaylistService`, `SpotifyCatalogService` – class names do not match their file names.** The files are named `SpotifyPlaybackAdapter.kt`, `SpotifyPlaylistAdapter.kt`, and `SpotifyCatalogAdapter.kt` respectively, following the `*Adapter` naming convention. The class names inside still carry the old `*Service` suffix and must be renamed to match: `SpotifyPlaybackAdapter`, `SpotifyPlaylistAdapter`, `SpotifyCatalogAdapter`.
* **`SpotifyPlaybackAdapter.kt` – mixed indentation.** The method `getCurrentlyPlaying` uses 2-space indentation while all other methods in the file use 4-space.
* **`SpotifyPlaylistAdapter.kt` – mixed indentation.** The method `getPlaylistTracks` uses 2-space indentation while `getPlaylists` and `getPlaylistTracksPage` use 4-space.
* **`SpotifyPlaylistAdapter.kt` – duplicated track parsing logic.** The block that iterates response items, filters non-tracks, guards against missing `albumId`, and constructs `PlaylistTrack` instances is copy-pasted verbatim between `getPlaylistTracks` and `getPlaylistTracksPage`. Extract it into a private helper (e.g. `parsePlaylistTracks(items: List<…>): List<PlaylistTrack>`) to eliminate the duplication.
* **`SpotifyPlaylistAdapter.getPlaylists` – manual mapping instead of `mapNotNull`/`map`.** The loop calling `items.add(SpotifyPlaylistItem(…))` inside `forEach` can be replaced with a direct `items += playlistsResponse.items.map { … }` or a `mapTo` call, which is more idiomatic Kotlin.

---

## application-quarkus

* No production-code violations found. The module correctly serves as the wiring layer. Tests are consistent with the project's `@QuarkusTest` + REST Assured pattern.
