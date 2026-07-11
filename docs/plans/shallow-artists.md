# Plan: Shallow Artists

Stand: 2026-07-11

## Problem

Aktuell wird für jeden im Katalog auftauchenden Artist der volle Sync-Pfad durchlaufen: `SyncArtistDetails` →
`SyncArtistAlbums` (paginiert) → `SyncAlbumDetails` je Album, wodurch alle Alben und Tracks des Artists dauerhaft
gespeichert werden (`CatalogService.kt`). Das passiert auch für Artists, die nur am Rande auftauchen – z. B. Feature-Artists
auf einem einzelnen Track, Artists von Playlist-Fremdinhalten oder einmalig gehörte Tracks. Das führt zu unnötigem
Sync-Aufwand (API-Calls, Storage) und unerwünschtem Rauschen in Statistiken/Aggregationen für Artists, die der Nutzer gar
nicht aktiv verfolgen möchte.

Es gibt bereits einen verwandten, aber schwächeren Mechanismus: `AppArtist.blockedFromAggregation: Boolean`
(`AppArtist.kt:15`). Er blendet einen Artist nur aus der Aggregation aus (`PlaybackAggregationService.aggregateDay()`,
Zeilen 217-224) – Katalog-Sync und Speicherung roher Playback-Events (`app_playback`) laufen unverändert weiter, und ein
Statuswechsel löst lediglich `rebuildAllAggregations()` aus, nicht das Löschen/Neuerzeugen der zugrunde liegenden Daten.

"Shallow Artists" löst diesen Mechanismus vollständig ab: Für als "shallow" markierte Artists wird **nur** das
Artist-Dokument selbst (Name, Bild, Typ) synchronisiert – keine Alben, keine Tracks. Playback-Events dieses Artists werden
weiterhin gespeichert, aber nicht aggregiert/angezeigt. Zusätzlich trifft das System bei bisher unbekannten Artists
automatisch eine Einschätzung (Assumption), die der Nutzer über eine neue Settings-UI bestätigen oder korrigieren kann.

## Datenmodell-Konzept

Neues Feld `AppArtist.syncStatus: ArtistSyncStatus` ersetzt `blockedFromAggregation: Boolean` vollständig, mit vier
Ausprägungen:

- **`SYNC`** – finaler Status. Artist wird voll synchronisiert (Alben, Tracks), Playback-Events werden gespeichert und
  aggregiert. Entspricht dem heutigen Standardverhalten.
- **`SHALLOW`** – finaler Status. Nur das Artist-Dokument wird synchronisiert. Keine Alben-/Track-Synchronisation,
  vorhandene Alben/Tracks werden beim Wechsel gelöscht. Playback-Events dieses Artists werden weiterhin gespeichert, aber
  nicht aggregiert.
- **`SYNC_ASSUMPTION`** – Übergangsstatus für einen neu entdeckten, noch nicht vom Nutzer bestätigten Artist, bei dem das
  System vermutet, dass voller Sync gewünscht ist (weil der Artist auf einer aktiv synchronisierten Playlist vorkommt).
- **`SHALLOW_ASSUMPTION`** – Übergangsstatus für einen neu entdeckten Artist ohne Playlist-Bezug (z. B. nur über
  Recently-Played-Historie gesehen). Vermutung: nur Shallow-Sync gewünscht.

Zustandsübergänge:

```
(neu entdeckt, auf aktiv synchronisierter Playlist) ──► SYNC_ASSUMPTION ──┐
                                                                          │
(neu entdeckt, nur über Playback/Recently-Played)   ──► SHALLOW_ASSUMPTION ─┤
                                                                          │
                                                    Settings-UI: SYNC oder SHALLOW bestätigen
                                                                          │
                                                                          ▼
                                                                  SYNC  ◄────►  SHALLOW
                                                                    (Catalog-UI, regulärer Wechsel)
```

- Ein Assumption-Status wird ausschließlich automatisch vom System vergeben, sobald ein bisher unbekannter Artist
  entdeckt wird. Er ist reiner Anfangszustand und macht sichtbar, dass der Nutzer für diesen Artist noch keine bewusste
  Entscheidung getroffen hat. Ein manueller Wechsel zwischen den beiden Assumption-Status ist nicht vorgesehen.
- Aus einem Assumption-Status heraus kann der Nutzer über die Settings-UI direkt in einen der beiden finalen Status
  wechseln (`SYNC` oder `SHALLOW`) – unabhängig davon, welchen Status das System vermutet hatte.
- Erst nach diesem "Bestätigen" befindet sich der Artist in einem finalen Status; der reguläre Wechsel zwischen den
  finalen Status `SYNC` und `SHALLOW` erfolgt danach über die Catalog-UI (ersetzt die bestehenden Block/Unblock-Buttons in
  `catalog.html`). Ein direkter Rücksprung von `SYNC`/`SHALLOW` in einen Assumption-Status ist nicht vorgesehen.

**Migration bestehender Artists:** Alle heute bereits existierenden `app_artist`-Dokumente erhalten `SYNC_ASSUMPTION`, mit
einer Ausnahme: Artists mit `blockedFromAggregation=true` erhalten `SHALLOW_ASSUMPTION`. So kann der Nutzer diese Artists
noch einmal bewusst bestätigen/korrigieren, ohne dass rückwirkend etwas nachsynchronisiert wird, was vorher blockiert war.

## Playback-Event-Handling

Playback-Events werden **nie gelöscht**, unabhängig vom Artist-Status. Rohe Playback-Events (`app_playback`) werden wie
heute vollständig gespeichert; die Filterung nach Artist-Status erfolgt ausschließlich zur Aggregations-/Query-Zeit in
`PlaybackAggregationService.aggregateDay()` (Zeilen 217-224) – analog zum heutigen `blockedFromAggregation`-Mechanismus,
den `ArtistSyncStatus` ersetzt. Items von `SHALLOW`/`SHALLOW_ASSUMPTION`-Artists werden dort von Aggregation/Anzeige
ausgeschlossen, bleiben aber in `app_playback` erhalten.

Eine Filterung bereits bei der Ingestion (`PlaybackService.appendNewPlaybackData()`) findet nicht statt: Ein späterer
Wechsel `SHALLOW → SYNC` könnte historische Playback-Events sonst nicht mehr rekonstruieren, da Spotifys
`recently-played`-Endpunkt nur ein kurzes Zeitfenster (ca. 50 Einträge) liefert.

## Main-Artist-Regel

Bei Alben/Tracks mit mehreren Artists ist für Sync-Umfang und Löschung immer nur der Main-Artist (erster Artist in der
Spotify-Artist-Liste) ausschlaggebend – dasselbe Prinzip verwendet bereits `CatalogService.buildCatalogSyncRequest()`
(Zeile 219, `artistIds.firstOrNull()`).

## Umsetzungsschritte (grob)

1. **Datenmodell:** `AppArtist`/`AppArtistDocument` um `syncStatus: ArtistSyncStatus` (Enum `SYNC`, `SHALLOW`,
   `SYNC_ASSUMPTION`, `SHALLOW_ASSUMPTION`) ergänzen, `blockedFromAggregation: Boolean` entfernen (Feld und alle
   Verwendungsstellen). Migration bestehender Artists wie oben beschrieben (Sonderfall `blockedFromAggregation=true`).
2. **Discovery/Assumption-Zuordnung:** `SyncController.syncForTracks()`/`syncArtists()` unterscheiden bereits heute
   zwischen Playlist- und Playback-Ursprung (Aufrufkontext). Bei erstmaligem Anlegen eines Artists (in
   `CatalogService.syncArtistDetails()`, nach dem Spotify-Fetch) wird anhand des Ursprungs automatisch
   `SYNC_ASSUMPTION` (Artist auf einer aktiv synchronisierten Playlist) oder `SHALLOW_ASSUMPTION` (nur Playback)
   gesetzt. Dazu muss der Ursprung bis zum `SyncArtistDetails`-Handler durchgereicht werden – aktuell trägt das Event nur
   `artistId` (`DomainOutboxEvent.SyncArtistDetails`, Zeile 118).
3. **Catalog-Sync-Branching:** `CatalogService.syncArtistDetails()` enqueued `SyncArtistAlbums` für die Status `SYNC`
   **und** `SYNC_ASSUMPTION` (spekulativer, optimistischer Voll-Sync – ein späterer Wechsel zu `SHALLOW` räumt ggf. zu
   viel synchronisierte Daten wieder auf, siehe Schritt 5). Für `SHALLOW` und `SHALLOW_ASSUMPTION` wird kein
   Alben-Sync angestoßen.
4. **Kein Playback-Ingestion-Filter:** `PlaybackService.appendNewPlaybackData()` bleibt unverändert – Playback-Events
   werden unabhängig vom Artist-Status immer vollständig gespeichert (siehe Playback-Event-Handling oben).
5. **Regulärer Statuswechsel (Catalog-UI):** Neue Methoden `CatalogPort.setArtistSyncStatus(artistId, SYNC|SHALLOW)`
   ersetzen `blockArtistFromAggregation`/`unblockArtistFromAggregation`:
   - `→ SYNC`: enqueued `SyncArtistAlbums(artistId)`.
   - `→ SHALLOW`: löscht alle `AppAlbum`/`AppTrack`-Einträge, deren Main-Artist (siehe oben) diesem Artist entspricht
     (neue Repository-Methoden `deleteByMainArtistId` in `AppAlbumRepositoryPort`/`AppTrackRepositoryPort`, aktuell nicht
     vorhanden). Alben/Tracks, bei denen der Artist nur als Nebenkünstler auftaucht, bleiben erhalten. Mögliche
     Playlist-Referenzen auf so gelöschte Tracks/Alben werden vorerst nicht behandelt (Lücke bleibt bestehen, da die
     Playlist-UI ohnehin keine einzelnen Track-Einträge rendert). Playback-Events (`app_playback`) werden nicht gelöscht.
     Danach `rebuildAllAggregations()`, damit die Aggregations-/Query-Zeit-Filterung (Schritt 7) greift.
   - Bereits laufende Outbox-Tasks (`SyncArtistAlbums`/`SyncAlbumDetails`) für den Artist werden beim Wechsel zu
     `SHALLOW` **nicht** aktiv gelöscht – eine gezielte Lösch-API in `OutboxPort`/`OutboxAdminPort` wäre für die
     Outbox-Bibliothek unüblich und wird nicht angefragt. Stattdessen prüft der `SyncArtistAlbums`-/
     `SyncAlbumDetails`-Handler bei Ausführung erneut den aktuellen `syncStatus` des Artists und bricht ohne weitere
     Wirkung ab, falls dieser inzwischen auf `SHALLOW` gewechselt hat.
   - Neue Buttons/Routen ersetzen `PlaybackSettingsResource.blockArtist()`/`unblockArtist()`.
6. **Settings-UI für Assumption-Auflösung:** Neue Seite unter `/catalog/artists/settings`, erreichbar über einen
   "Artists"-Einstieg in der Catalog-UI. Listet alle Artists mit Status `SYNC_ASSUMPTION`/`SHALLOW_ASSUMPTION`, mit
   Aktionen "auf Sync setzen" und "auf Shallow setzen" (jeweils direkter Übergang in den finalen Status). Zusätzliche
   Hinweise in der Catalog-UI, damit der Nutzer erkennt, wenn Entscheidungen ausstehen:
   - Die Artists-Kachel zeigt die Anzahl "undecided" Artists (Summe aus `SYNC_ASSUMPTION` und `SHALLOW_ASSUMPTION`), sofern
     diese größer als 0 ist.
   - Der Catalog-Navigationspunkt zeigt optional einen Badge mit derselben Anzahl.
7. **Aggregation:** Da Playback-Events unabhängig vom Artist-Status immer gespeichert werden (Schritt 4), filtert
   `PlaybackAggregationService.aggregateDay()` weiterhin anhand des Artist-Status (analog zum heutigen
   `blockedFromAggregation`-Filter, Zeilen 217-224) – Items von `SHALLOW`/`SHALLOW_ASSUMPTION`-Artists werden von der
   Aggregation/Anzeige ausgeschlossen, bleiben aber in `app_playback` erhalten.
8. **Tägliche Resyncs:** `CatalogService.resyncCatalog()` (getriggert durch `ArtistCatalogSyncJob`) enqueued aktuell für
   *alle* bekannten Artists unbedingt `SyncArtistAlbums` (Zeile 109). Das wird auf Artists im Status `SYNC` oder
   `SYNC_ASSUMPTION` eingeschränkt – Artists in `SHALLOW`/`SHALLOW_ASSUMPTION` werden nie automatisch resynct, da diese
   Jobs nur bereits vorhandene Katalogdaten aktualisieren (neue Alben entdecken etc.) und Shallow-Artists explizit keine
   Alben-/Track-Daten haben sollen.
9. **`docs/arc42/arc42.md` aktualisieren:** Die dort beschriebene, im Code nicht existierende
   `playbackProcessingStatus`/`ACTIVE`/`INACTIVE`/`UNDECIDED`-Doku durch das tatsächlich implementierte
   `ArtistSyncStatus`-Konzept ersetzen.
