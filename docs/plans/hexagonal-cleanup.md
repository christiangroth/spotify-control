# Hexagonal Cleanup Plan

Dieses Dokument fasst alle identifizierten Architekturverletzungen und Verbesserungspotenziale aus dem
Architecture Review zusammen. Ziel ist eine saubere, konsequente Hexagonal-Architektur mit klarer
Subdomain-Trennung und konsistentem Naming über alle Module hinweg.

---

## domain-api

### Subdomain-Struktur einführen

Das `model/`-Paket enthält aktuell 54 Klassen ohne jede Untergliederung. Die vier Subdomains sind im Code
vorhanden, aber durch den flachen Package-Baum unsichtbar. Gleiches gilt für `port/in` und `port/out`.

- [ ] `model/` aufteilen in `model/catalog/`, `model/playback/`, `model/playlist/`, `model/user/`
  und alle Klassen in das passende Subpaket verschieben
- [ ] `port/in/` aufteilen in `port/in/catalog/`, `port/in/playback/`, `port/in/playlist/`, `port/in/user/`
- [ ] `port/out/` aufteilen in `port/out/catalog/`, `port/out/playback/`, `port/out/playlist/`,
  `port/out/user/`, `port/out/infra/` (für die querschnittlichen Ports wie Outbox, Throttling, Stats)

Zuordnung der Klassen zur jeweiligen Subdomain-Kandidaten:

| Subdomain | Modelle |
|---|---|
| catalog | `AppArtist`, `AppAlbum`, `AppTrack` (in `AppTrackData.kt`!), `AlbumSyncResult`, `ArtistPlaybackProcessingStatus`, `AlbumId`, `ArtistId`, `TrackId`, `ArtistBrowseItem`, `AlbumBrowseItem`, `TrackBrowseItem`, `CatalogStats` |
| playback | `AppPlaybackItem`, `RecentlyPlayedItem`, `RecentlyPartialPlayedItem`, `CurrentlyPlayingItem`, `RawPlaybackEvent`, `PlaybackEventEntry`, `PlaybackEventViewerResult`, `DayCount`, `TopEntry`, `ListeningStats` |
| playlist | `Playlist`, `PlaylistTrack`, `PlaylistTracksPage`, `PlaylistInfo`, `PlaylistId`, `PlaylistType`, `PlaylistSyncStatus`, `AppPlaylistCheck`, `PlaylistCheckStats`, `SpotifyPlaylistItem` |
| user | `User`, `UserId`, `SpotifyProfile`, `SpotifyProfileId`, `SpotifyTokens`, `SpotifyRefreshedTokens`, `AccessToken`, `RefreshToken` |

### Dateiname ≠ Klassenname

- [ ] `AppTrackData.kt` umbenennen in `AppTrack.kt` (enthält die Klasse `AppTrack`)

### Inkonsistente Verwendung von Wert-Objekten in Domänenklassen

Kern-Entitäten verwenden `TrackId`, `AlbumId`, `ArtistId` als Wert-Objekte, aber viele angrenzende Klassen
nutzen noch `String` und umgehen so die Typsicherheit.

- [ ] `AppArtist.artistId: String` → `AppArtist.id: ArtistId` (analog zu `AppAlbum.id: AlbumId` und `AppTrack.id: TrackId`)
- [ ] `RecentlyPlayedItem.trackId`, `.artistIds`, `.albumId` von `String`/`List<String>` auf `TrackId`, `List<ArtistId>`, `AlbumId?` umstellen
- [ ] `RecentlyPartialPlayedItem.trackId`, `.artistIds`, `.albumId` analog
- [ ] `CurrentlyPlayingItem.trackId`, `.artistIds`, `.albumId` analog
- [ ] `PlaylistTrack.trackId`, `.artistIds`, `.albumId` von `String`/`List<String>` auf Wert-Objekte umstellen
- [ ] `AppPlaylistCheck.playlistId: String` → `AppPlaylistCheck.playlistId: PlaylistId`
- [ ] `AppArtistRepositoryPort.findByArtistIds(artistIds: Set<String>)` → auf `Set<ArtistId>` umstellen
- [ ] `AppArtistRepositoryPort.updatePlaybackProcessingStatus(artistId: String, ...)` → auf `ArtistId` umstellen

### Zeit-Typen vereinheitlichen

Im domain-api werden `kotlin.time.Instant`, `java.time.Instant` und `kotlinx.datetime.LocalDate` gemischt.
Standard soll `kotlin.time.Instant` und `kotlinx.datetime.LocalDate` sein (kein `java.time`).

- [ ] `OutboxTask` auf `kotlin.time.Instant` umstellen (aktuell `java.time.Instant`)
- [ ] `OutboxPartitionStats` auf `kotlin.time.Instant` umstellen
- [ ] `CronjobStats` auf `kotlin.time.Instant` umstellen
- [ ] `PredicateStats` auf `kotlin.time.Instant` umstellen
- [ ] `PlaybackActivityPort.lastActivityTimestamp()` auf `kotlin.time.Instant?` umstellen

### Infrastruktur-Modelle aus dem Domain-Model-Paket herausnehmen

Folgende Klassen haben wenig mit dem eigentlichen Domänen-Modell zu tun und sind eher Präsentations-DTOs oder
Infrastruktur-Stats. Sie liegen aktuell im `model/`-Paket und erhöhen dessen Größe unnötig.
Langfristig sollte geprüft werden, ob diese im domain-api wirklich nötig sind oder ob die Ports
primitive Rückgabetypen nutzen und die Aggregation in den Adaptern stattfindet.

- [ ] `MongoViewerResult`, `MongoViewerField`, `MongoViewerFieldType`, `MongoViewerFilter`,
  `MongoViewerFilterOperator` – primär Präsentations-Typen für die MongoDB-Viewer-UI; prüfen ob
  diese in einen eigenständigen Bereich wie `model/viewer/` gehören
- [ ] `OutboxTask`, `OutboxPartitionStats`, `OutboxEventTypeCount` – Outbox-Infrastruktur-Stats;
  prüfen ob diese nicht direkt im Outbox-Adapter-Modul leben sollten
- [ ] `HealthStats`, `MongoCollectionStats`, `MongoQueryStats`, `CronjobStats`, `ConfigEntry`,
  `ConfigurationStats`, `OutgoingRequestStats`, `SpotifyRequestStats`, `PredicateStats` – reine
  System-Health-/Monitoring-DTOs; prüfen ob sie in `model/infra/` zusammengefasst werden sollen
- [ ] `PlaybackDetectedEvent` – eine leere Marker-Klasse, die als CDI-Event-Typ dient; CDI-Events
  gehören zu Adapter-Concerns, nicht zu Domain-Modellen; prüfen ob diese aus dem domain-api entfernt
  werden kann

### Präsentations-Logik aus Domain-Modellen heraushalten

Domain-Klassen sollen pure Kotlin `data class`/`sealed class`/`enum` sein – ohne Formatierungshilfsmethoden,
die eigentlich Template-Concerns sind.

- [ ] `NumberFormatting.kt` (Extension-Funktionen `Long.formatted()`, `Int.formatted()`) gehört nicht ins
  `model/`-Paket. Entweder in ein separates Utility-Paket (`util/`) oder die Formatierung wird
  direkt in den Qute-Templates oder Adapter-Klassen erledigt.
- [ ] Prüfen ob die `*Formatted`-Properties in Domain-Klassen wie `DashboardStats`, `HealthStats`,
  `CatalogStats` usw. wirklich Domain-Logik sind oder Presentation-Concerns – ggf. auslagern

### `OutboxViewerPartition` aus der Port-Datei herauslösen

- [ ] `OutboxViewerPartition` (aktuell inline in `OutboxViewerPort.kt` definiert) in eine eigene Datei
  oder in das `model/`-Paket verschieben, damit Port-Dateien ausschließlich die Interface-Definition
  enthalten

---

## domain-impl

### Naming: `*Adapter` → `*Service`

Der Begriff „Adapter" in der Hexagonal-Architektur bezeichnet die äußere Schicht (Inbound/Outbound
Adapters). Domain-Service-Implementierungen, die die Inbound-Ports implementieren, sollten `*Service`
oder ein gleichwertiges Suffix tragen. Die aktuelle Konvention `*Adapter` im `domain-impl` stiftet
Verwirrung, weil sie denselben Begriff wie die echten Adapter-Module verwendet.

- [ ] `CatalogAdapter` → `CatalogService`
- [ ] `CatalogBrowserAdapter` → `CatalogBrowserService`
- [ ] `DashboardAdapter` → `DashboardService`
- [ ] `HealthAdapter` → `HealthService`
- [ ] `LoginServiceAdapter` → `LoginService` (das Suffix `Adapter` fällt weg, da der Klassenname
  bereits konzeptuell ist)
- [ ] `MongoViewerAdapter` → `MongoViewerService`
- [ ] `OutboxViewerAdapter` → `OutboxViewerService`
- [ ] `PlaybackAdapter` → `PlaybackService`
- [ ] `PlaybackEventViewerAdapter` → `PlaybackEventViewerService`
- [ ] `PlaylistAdapter` → `PlaylistService`
- [ ] `PlaylistCheckAdapter` → `PlaylistCheckService`
- [ ] `RuntimeConfigAdapter` → `RuntimeConfigService`
- [ ] `UserProfileAdapter` → `UserProfileService`
- [ ] In allen Domain-Impl-Tests die Klassennamen entsprechend anpassen

### `SpotifyAccessTokenAdapter` verletzt Architektur-Regel

`SpotifyAccessTokenAdapter` implementiert `SpotifyAccessTokenPort`, das in `port/out` liegt. Out-Ports
werden von Adapter-Modulen implementiert – nicht von `domain-impl`. Diese Klasse ist technisch eine
Orchestration von Spotify-Auth, Token-Refresh und Verschlüsselung und gehört konzeptuell zu
`adapter-out-spotify`.

- [ ] `SpotifyAccessTokenAdapter` nach `adapter-out-spotify` verschieben
- [ ] Alle Domain-Service-Klassen, die `SpotifyAccessTokenPort` per Constructor-Injection empfangen,
  bleiben unverändert – nur die Implementierung wandert

### `TokenEncryptionAdapter` verletzt Architektur-Regel und nutzt `@ConfigProperty`

`TokenEncryptionAdapter` implementiert `TokenEncryptionPort` (port/out) und enthält `@ConfigProperty`.
Quarkus/MicroProfile-Annotationen (außer `@ApplicationScoped`) gehören nicht in `domain-impl`.
Zudem ist die Implementierung eine infrastrukturelle Utility-Klasse (JDK-Krypto + Config).

- [ ] `TokenEncryptionAdapter` nach `adapter-in-web` oder in ein neues kleines Adapter-Modul
  `adapter-out-crypto` verschieben (je nachdem ob Verschlüsselung als eigenständige Boundary
  sinnvoll ist)

### `@ConfigProperty` in Domain-Services entfernen

`@ConfigProperty` (MicroProfile Config) ist ein Framework-Annotation und gehört nicht in `domain-impl`.
Configuration-Werte sollen über Constructor-Parameter übergeben werden, die vom CDI-Wiring
in `application-quarkus` befüllt werden, oder die Konfiguration soll über einen Out-Port abstrahiert
werden.

- [ ] `PlaybackAdapter`: `@ConfigProperty(name = "app.playback.minimum-progress-seconds")` –
  Konfigurationswert über CDI Producer in `application-quarkus` injizieren oder als normalen
  Constructor-Parameter mit CDI-Qualifier übergeben
- [ ] `DashboardAdapter`: `@ConfigProperty(name = "dashboard.recently-played.limit")` und
  `@ConfigProperty(name = "dashboard.listening-stats.top-entries-limit")` – analog
- [ ] `LoginServiceAdapter`/`LoginService`: `@ConfigProperty(name = "app.allowed-spotify-user-ids")` –
  analog

### Subdomain-Struktur in Package-Hierarchie sichtbar machen

- [ ] Klassen nach Subdomains in Unter-Pakete aufteilen: `catalog/`, `playback/`, `playlist/`, `user/`
- [ ] `SyncController` und `CatalogSyncRequest` gehören ins `catalog/`-Paket
- [ ] `PlaylistCheckRunner` und die Check-Runner-Implementierungen sind bereits im `check/`-Subpaket –
  dieses in `playlist/check/` verschieben

---

## adapter-in-web

### `ConfigurationInfoAdapter` falsch platziert

`ConfigurationInfoAdapter` in `adapter-in-web` implementiert `ConfigurationInfoPort` (ein **Out-Port**).
Out-Port-Implementierungen gehören in `adapter-out-*`, nicht in `adapter-in-*`.

- [ ] `ConfigurationInfoAdapter` in ein eigenes Modul `adapter-out-config` verschieben, oder – falls
  der Umfang zu gering ist – in `adapter-out-scheduler` integrieren (der bereits ähnliche
  Infrastruktur-Stats liefert)

### `PlaybackEventViewerResult` vs. Rückgabe im Port

Der `PlaybackEventViewerPort` gibt `PlaybackEventViewerResult` zurück, das `PlaybackEventEntry` und
`RawPlaybackEvent`-artige Daten enthält. `PlaybackEventEntry.type` ist ein roher `String` statt Enum.

- [ ] `PlaybackEventEntry.type: String` durch ein typsicheres Enum ersetzen (z. B. `PlaybackEventType`)

---

## adapter-in-starter

### Migrations-Starter aufräumen

Die meisten Starters sind einmalige Datenmigrations-Skripte, die nach erfolgreicher Ausführung
dauerhaft im Codebase verbleiben und Startzeit kosten.

- [ ] Audit aller Starter: welche sind einmalige Migrationen, die nie wieder ausgeführt werden?
- [ ] Einmalige Migrationen (z. B. `MigrateEntityFieldsStarter`, `RenameCollectionsStarter`,
  `DropCollectionsStarter`, `RemoveGenreFieldsStarter`, `DeleteCatalogDataStarter`,
  `DeletePendingAlbumEnrichmentStarter`, `DeletePendingPerItemSyncTasksStarter`,
  `DeletePendingSyncMissingTasksStarter`, `MigrateLastEnrichmentDateFieldStarter`,
  `ReEnrichArtistNameBugfixStarter`, `RemoveNonOwnedPlaylistMetadataBugfixStarter`) nach
  Verifikation entfernen
- [ ] Starters, die dauerhaft relevant sind (z. B. `WipePlaylistDocumentsStarter`,
  `WipePlaylistChecksStarter`), behalten – aber prüfen, ob sie statt Starter besser als explizite
  Admin-Aktion über einen UI-Button ausgelöst werden sollten

---

## adapter-out-mongodb

### Naming der Spotify-Roh-Dokumente

Die Klassen `SpotifyCurrentlyPlayingDocument`, `SpotifyRecentlyPlayedDocument` und
`SpotifyRecentlyPartialPlayedDocument` tragen das „Spotify"-Präfix, obwohl sie unsere eigene
MongoDB-Repräsentation sind. Das Präfix suggeriert fälschlicherweise, es handele sich um Spotify
API-Typen.

- [ ] `SpotifyCurrentlyPlayingDocument` → `CurrentlyPlayingDocument`
- [ ] `SpotifyCurrentlyPlayingDocumentRepository` → `CurrentlyPlayingDocumentRepository`
- [ ] `SpotifyRecentlyPlayedDocument` → `RecentlyPlayedDocument`
- [ ] `SpotifyRecentlyPlayedDocumentRepository` → `RecentlyPlayedDocumentRepository`
- [ ] `SpotifyRecentlyPartialPlayedDocument` → `RecentlyPartialPlayedDocument`
- [ ] `SpotifyRecentlyPartialPlayedDocumentRepository` → `RecentlyPartialPlayedDocumentRepository`

---

## adapter-out-spotify

### Gut strukturiert – keine strukturellen Verletzungen

Das Modul hält sich an die Architektur-Regeln: Spotify-API-Modelle sind `internal` und nie im domain-api
sichtbar. Keine kritischen Findings.

- [ ] `SpotifyAccessTokenAdapter` aus `domain-impl` hierher verschieben (siehe oben)

---

## adapter-in-outbox

### `java.time.Instant` statt Kotlin-Typen

`DomainOutboxTaskDispatcher` verwendet `java.time.Instant.now().plus(error.retryAfter)` beim Bauen von
`DispatchResult.Paused`. Dieser Java-Typ schleicht sich ein, weil die quarkus-outbox-API aktuell
`java.time.Instant` erwartet. Sobald die Zeitvereinheitlichung in domain-api abgeschlossen ist und die
quarkus-outbox-API `kotlin.time.Instant` unterstützt, sollte die Konversion entfallen.

- [ ] `DomainOutboxTaskDispatcher`: `java.time.Instant.now().plus(error.retryAfter)` auf
  `kotlin.time.Instant.now() + error.retryAfter` umstellen und ggf. über `.toJavaInstant()` an die
  quarkus-outbox-API übergeben, sobald `SpotifyRateLimitError.retryAfter` auf `kotlin.time.Duration`
  umgestellt ist

---

## adapter-in-scheduler

### `java.time.Instant`/`Duration` statt Kotlin-Typen

`CurrentlyPlayingSkipPredicate` verwendet `java.time.Instant` und `java.time.Duration` intern. Standard
im Projekt ist `kotlin.time`.

- [ ] `CurrentlyPlayingSkipPredicate`: `Instant.EPOCH`, `Instant.now()`, `Duration.ofSeconds()`,
  `Duration.ofMinutes()` sowie `isBefore()` auf kotlin.time-Äquivalente umstellen (`Clock.System.now()`,
  `Duration.seconds()`, `Duration.minutes()`)

### `@JvmOverloads`-Konstruktor als Testbarkeits-Workaround

`CurrentlyPlayingSkipPredicate` verwendet `@JvmOverloads constructor` mit optionalen Parametern
(`ScheduledSkipPredicate`, `PlaybackActivityPort?`), um den Aufbau in Tests zu vereinfachen. CDI-Beans
sollten genau einen Konstruktor haben; der Workaround macht die CDI-Wiring-Intention unklar.

- [ ] Testaufbau überprüfen und `@JvmOverloads` durch einen sauberen Ansatz ersetzen – z. B. eigenen
  Testkonstruktor oder explizites MockK-Subklassen-Mocking; CDI-Beans ohne optionale Konstruktorparameter
  formulieren

---

## adapter-out-outbox

### MongoDB-Zugriff direkt in `OutboxPortAdapter` – kritische Architekturverletzung

`OutboxPortAdapter.getTasksByPartition()` enthält direkten MongoDB-Zugriff (`MongoClient`,
`Filters.eq`, `.find()`, Dokument-Mapping). MongoDB-Zugriffe dürfen ausschließlich in
`adapter-out-mongodb` stattfinden – diese Regel wird hier gebrochen.

- [ ] Neuen Out-Port `OutboxTaskRepositoryPort` in `domain-api/port/out` definieren mit
  `getTasksByPartition(partitionKey: String): List<OutboxTask>`
- [ ] Implementierung als `OutboxTaskRepositoryAdapter` in `adapter-out-mongodb` anlegen und die
  MongoDB-Abfrage (inkl. Dokument-Mapping) dorthin verschieben
- [ ] `OutboxPortAdapter` verwendet das neue Port per Constructor-Injection statt `MongoClient`
- [ ] `@ConfigProperty(name = "quarkus.mongodb.database")` und `MongoClient`-Abhängigkeit aus
  `adapter-out-outbox` vollständig entfernen – das Modul darf kein MongoDB-SDK mehr importieren

### `java.time.Instant` statt Kotlin-Typen

`OutboxPortAdapter` nutzt `Instant.EPOCH` und `Instant.MAX` aus `java.time`.

- [ ] Auf `kotlin.time.Instant` umstellen, sobald `OutboxTask` im Rahmen der domain-api
  Zeitvereinheitlichung auf `kotlin.time.Instant` umgestellt wurde

---

## adapter-out-scheduler

### `java.time.Instant`/`Duration` in `PlaybackActivityAdapter`

`PlaybackActivityAdapter` verwendet `java.time.Instant` und `java.time.Duration` sowohl intern als auch
über den Rückgabewert von `PlaybackActivityPort.lastActivityTimestamp(): Instant?`.

- [ ] `PlaybackActivityAdapter` auf kotlin.time umstellen, sobald `PlaybackActivityPort.lastActivityTimestamp()`
  im Zuge der domain-api Zeitvereinheitlichung auf `kotlin.time.Instant?` umgestellt wurde

### `PlaybackDetectedEvent` als CDI-Marker-Klasse im domain-api-Model

`CurrentlyPlayingScheduleState` feuert `Event<PlaybackDetectedEvent>`. `PlaybackDetectedEvent` ist eine
leere Marker-Klasse in `domain-api/model`, obwohl CDI-Events ein Adapter-Concern sind und nichts im
Domain-Modell zu suchen haben.

- [ ] `PlaybackDetectedEvent` aus `domain-api/model` entfernen (verknüpft mit dem entsprechenden
  Todo in der domain-api-Sektion)
- [ ] `PlaybackDetectedEvent` direkt in `adapter-out-scheduler` definieren – die Klasse ist nur lokal
  zwischen `CurrentlyPlayingScheduleState` und `PlaybackActivityAdapter` relevant; `PlaybackStatePort`
  bleibt das einzige Interface-Bindeglied nach außen

---

## adapter-out-slack

### Gut strukturiert – keine strukturellen Verletzungen

`SlackNotificationAdapter` implementiert korrekt Out-Ports (`OutboxPartitionObserver`,
`PlaylistCheckNotificationPort`). `@ConfigProperty` ist in Adapter-Modulen erwünscht und daher hier
in Ordnung. Keine kritischen Findings.

- [ ] `AppPlaylistCheck.playlistId: String` → bei Umstellung auf `PlaylistId`-Wert-Objekt
  (bereits in domain-api-Sektion beschrieben) die entsprechenden Stellen hier anpassen

---

## Übergreifende Maßnahmen

### Subdomain-Sichtbarkeit durch Package-Struktur

Ziel: Eine Entwicklerin, die zum ersten Mal in den Code schaut, soll sofort erkennen können, zu
welcher Subdomain eine Klasse gehört.

- [ ] Konsistentes Package-Schema in allen Modulen etablieren:
  - `domain-api`: `domain/model/{catalog,playback,playlist,user}/`, `domain/port/{in,out}/{catalog,playback,playlist,user,infra}/`
  - `domain-impl`: `domain/{catalog,playback,playlist,user}/`
  - `adapter-*`: keine Subdomain-Aufteilung nötig (Module sind bereits klein genug)

### Architektur-Tests ergänzen

- [ ] ArchUnit oder ähnliche Bibliothek einsetzen, um die Modul-Abhängigkeitsregeln als automatische
  Tests zu verifizieren:
  - `domain-api` darf keine externen Abhängigkeiten haben
  - `domain-impl` darf nur `domain-api` importieren (kein `adapter-*`)
  - `adapter-*` darf nur `domain-api` importieren (kein `domain-impl`, kein anderes `adapter-*`)
  - Framework-Annotationen (außer `@ApplicationScoped`) dürfen nicht in `domain-impl`-Klassen erscheinen
