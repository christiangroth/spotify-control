# Hexagonal Cleanup Plan

Dieses Dokument fasst alle identifizierten Architekturverletzungen und Verbesserungspotenziale aus dem
Architecture Review zusammen. Ziel ist eine saubere, konsequente Hexagonal-Architektur mit klarer
Subdomain-Trennung und konsistentem Naming über alle Module hinweg.

---

## domain-api

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

- [ ] Prüfen ob die `*Formatted`-Properties in Domain-Klassen wie `DashboardStats`, `HealthStats`,
  `CatalogStats` usw. wirklich Domain-Logik sind oder Presentation-Concerns – ggf. auslagern

---

## domain-impl

### `SpotifyAccessTokenAdapter` verletzt Architektur-Regel

`SpotifyAccessTokenAdapter` implementiert `SpotifyAccessTokenPort`, das in `port/out` liegt. Out-Ports
werden von Adapter-Modulen implementiert – nicht von `domain-impl`. Diese Klasse ist technisch eine
Orchestration von Spotify-Auth, Token-Refresh und Verschlüsselung und gehört konzeptuell zu
`adapter-out-spotify`.

- [ ] Alle Domain-Service-Klassen, die `SpotifyAccessTokenPort` per Constructor-Injection empfangen,
  bleiben unverändert – nur die Implementierung wandert

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
