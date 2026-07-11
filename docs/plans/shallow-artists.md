# Plan: Shallow Artists

Stand: 2026.07

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

"Shallow Artists" soll dieses Konzept ablösen bzw. erweitern: Für als "shallow" markierte Artists soll **nur** das
Artist-Dokument selbst (Name, Bild, Typ) synchronisiert werden – keine Alben, keine Tracks – und sämtliche Playback-Events
dieses Artists sollen weder gespeichert noch aggregiert werden. Zusätzlich soll das System bei bisher unbekannten Artists
automatisch eine Einschätzung (Assumption) treffen, die der Nutzer über eine neue Settings-UI bestätigen oder korrigieren
kann.

## Datenmodell-Konzept

Neues Feld `AppArtist.syncStatus: ArtistSyncStatus` (ersetzt `blockedFromAggregation` vollständig, siehe
[Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback)) mit vier Ausprägungen:

- **`SYNC`** – finaler Status. Artist wird voll synchronisiert (Alben, Tracks), Playback-Events werden gespeichert und
  aggregiert. Entspricht dem heutigen Standardverhalten.
- **`SHALLOW`** – finaler Status. Nur das Artist-Dokument wird synchronisiert. Keine Alben-/Track-Synchronisation,
  vorhandene Alben/Tracks werden beim Wechsel gelöscht. Playback-Events dieses Artists werden weder gespeichert noch
  aggregiert.
- **`SYNC_ASSUMPTION`** – Übergangsstatus für einen neu entdeckten, noch nicht vom Nutzer bestätigten Artist, bei dem das
  System vermutet, dass voller Sync gewünscht ist (weil der Artist auf einer synchronisierten Playlist vorkommt).
- **`SHALLOW_ASSUMPTION`** – Übergangsstatus für einen neu entdeckten Artist ohne Playlist-Bezug (z. B. nur über
  Recently-Played-Historie gesehen). Vermutung: nur Shallow-Sync gewünscht.

Zustandsübergänge (aktualisiert nach Review-Feedback):

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

- Ein Assumption-Status wird **ausschließlich automatisch vom System** vergeben, sobald ein bisher unbekannter Artist
  entdeckt wird. Er ist reiner Anfangszustand und macht sichtbar, dass der Nutzer für diesen Artist noch keine bewusste
  Entscheidung getroffen hat.
- Ein manueller Wechsel zwischen den beiden Assumption-Status (`SYNC_ASSUMPTION` ↔ `SHALLOW_ASSUMPTION`) ist **nicht**
  vorgesehen (Korrektur ggü. vorheriger Planversion).
- Aus einem Assumption-Status heraus kann der Nutzer über die Settings-UI direkt in einen der beiden finalen Status
  wechseln (`SYNC` oder `SHALLOW`) – unabhängig davon, welchen Status das System vermutet hatte.
- Erst nach diesem "Bestätigen" befindet sich der Artist in einem finalen Status; der reguläre Wechsel zwischen den
  finalen Status `SYNC` und `SHALLOW` erfolgt danach über die Catalog-UI (analog zu den bestehenden Block/Unblock-Buttons
  in `catalog.html`, die durch dieses Konzept ersetzt werden, siehe [Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback)).
- Ein direkter Rücksprung von `SYNC`/`SHALLOW` in einen Assumption-Status ist nicht vorgesehen.

## Ansätze

### Option A: Playback-Filterung zur Ingestion-Zeit (verworfen)

Beim Empfang neuer Playback-Daten (`PlaybackService.appendNewPlaybackData()`) wird der Artist-Status der betroffenen
Tracks geprüft; Items von `SHALLOW`/`SHALLOW_ASSUMPTION`-Artists werden gar nicht erst in `app_playback` (bzw. den
Recently-Played/Partial-Played-Collections) geschrieben.

- Vorteil: Aggregation bleibt unverändert (arbeitet weiterhin auf allen gespeicherten Rohdaten), kein doppelter
  Filter-Layer.
- Nachteil (Ausschlusskriterium): Ein späterer Wechsel zurück zu `SYNC` kann historische Playback-Events nicht mehr
  rekonstruieren – Spotifys `recently-played`-Endpunkt liefert nur ein kurzes Zeitfenster (ca. 50 Einträge). Da laut
  Review-Feedback **kein Datenverlust der rohen Spotify-Playback-Events** akzeptabel ist, scheidet diese Option aus.

### Option B: Soft-Flag mit Filterung zur Aggregations-/Query-Zeit (analog heutigem `blockedFromAggregation`) – gewählt

Playback-Events werden wie heute ungefiltert und dauerhaft gespeichert; die Filterung nach Artist-Status erfolgt
ausschließlich in `PlaybackAggregationService.aggregateDay()` und ggf. in Lesepfaden (z. B. "zuletzt gehört"-Ansichten) –
genau wie beim heutigen `blockedFromAggregation`-Mechanismus, den dieser Status ersetzt.

- Vorteil: Kein Datenverlust – ein Wechsel `SHALLOW → SYNC` macht historische Daten sofort wieder vollständig sichtbar,
  ein einfacher `rebuildAllAggregations()`-Aufruf reicht aus (bereits vorhandener Mechanismus).
- Klarstellung (Review-Feedback): Die Anforderung "von Playback Events ausschließen" aus dem Issue bezieht sich auf die
  **Sichtbarkeit in Aggregation/Statistik**, nicht auf physisches Löschen der Rohdaten. Rohe Playback-Events für
  Shallow-Artists werden weiterhin dauerhaft gespeichert, aber nicht ausgewertet/angezeigt.

## Empfehlung

**Option B (Soft-Flag, Filterung zur Aggregations-/Query-Zeit) für Playback-Events – keine Ausnahme.** Playback-Events
werden nie gelöscht oder bei der Ingestion verworfen, unabhängig vom Artist-Status. Der bestehende
`blockedFromAggregation`-Mechanismus wird vollständig durch `ArtistSyncStatus` ersetzt (siehe
[Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback)).

Das harte Löschen bestehender **Katalog-Daten** (Alben/Tracks, nicht Playback-Events) beim Wechsel zu `SHALLOW` bleibt
davon unberührt und Teil des Konzepts, siehe Umsetzungsschritt 5 – hier geht es nicht um Playback-Rohdaten, sondern um
den Sync-Umfang des Katalogs.

## Umsetzungsschritte (grob)

1. **Datenmodell:** `AppArtist`/`AppArtistDocument` um `syncStatus: ArtistSyncStatus` (Enum `SYNC`, `SHALLOW`,
   `SYNC_ASSUMPTION`, `SHALLOW_ASSUMPTION`) ergänzen, `blockedFromAggregation: Boolean` entfällt (Feld und alle
   Verwendungsstellen werden entfernt, siehe [Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback)). Migration
   bestehender Artists auf `SYNC_ASSUMPTION` (siehe [Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback) und
   Offene Frage 2 zum Sonderfall bisher geblockter Artists).
2. **Discovery/Assumption-Zuordnung:** `SyncController.syncForTracks()`/`syncArtists()` unterscheiden bereits heute
   zwischen Playlist- und Playback-Ursprung (Aufrufkontext). Bei erstmaligem Anlegen eines Artists (in
   `CatalogService.syncArtistDetails()`, nach dem Spotify-Fetch) wird anhand des Ursprungs automatisch
   `SYNC_ASSUMPTION` (Artist auf einer aktiv synchronisierten Playlist, siehe
   [Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback)) oder `SHALLOW_ASSUMPTION` (nur Playback) gesetzt.
   Dazu muss der Ursprung bis zum `SyncArtistDetails`-Handler durchgereicht werden – aktuell trägt das Event nur
   `artistId` (`DomainOutboxEvent.SyncArtistDetails`, Zeile 118).
3. **Catalog-Sync-Branching:** `CatalogService.syncArtistDetails()` enqueued `SyncArtistAlbums` für die Status `SYNC`
   **und** `SYNC_ASSUMPTION` (spekulativer, optimistischer Voll-Sync – siehe
   [Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback)). Für `SHALLOW` und `SHALLOW_ASSUMPTION` wird kein
   Alben-Sync angestoßen.
4. **Kein Playback-Ingestion-Filter:** `PlaybackService.appendNewPlaybackData()` bleibt unverändert – Playback-Events
   werden unabhängig vom Artist-Status immer vollständig gespeichert (Option B, kein Datenverlust).
5. **Regulärer Statuswechsel (Catalog-UI):** Neue Methoden `CatalogPort.setArtistSyncStatus(artistId, SYNC|SHALLOW)`
   ersetzen `blockArtistFromAggregation`/`unblockArtistFromAggregation`:
   - `→ SYNC`: enqueued `SyncArtistAlbums(artistId)`.
   - `→ SHALLOW`: löscht alle `AppAlbum`/`AppTrack`-Einträge, deren **Main-Artist** (erster Artist in der jeweiligen
     Spotify-Artist-Liste – dasselbe Prinzip verwendet bereits `CatalogService.buildCatalogSyncRequest()`, Zeile 219,
     das nur `artistIds.firstOrNull()` berücksichtigt) diesem Artist entspricht (neue Repository-Methoden
     `deleteByMainArtistId` in `AppAlbumRepositoryPort`/`AppTrackRepositoryPort`, aktuell nicht vorhanden). Alben/Tracks,
     bei denen der Artist nur als Nebenkünstler auftaucht, bleiben erhalten (siehe Offene Frage 4 zu Playlist-Referenzen
     auf so gelöschte Tracks). Playback-Events (`app_playback`) werden **nicht** gelöscht (Option B). Danach
     `rebuildAllAggregations()`, damit die Aggregations-/Query-Zeit-Filterung (Schritt 7) greift.
   - Bereits laufende Outbox-Tasks (`SyncArtistAlbums`/`SyncAlbumDetails`) für den Artist sollen beim Wechsel zu
     `SHALLOW` gelöscht werden. `OutboxPort`/`OutboxAdminPort` bieten aktuell keine Möglichkeit, einzelne Tasks gezielt
     zu löschen (nur `enqueue`, `getPartitionStats`, `getTasksByPartition` bzw. admin-seitig `wipeAll()`) – siehe
     [Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback) für den geplanten Feature Request an die
     `quarkus-outbox`-Bibliothek und Offene Frage 1 zum Umgang mit der verbleibenden Race Condition bis dahin.
   - Neue Buttons/Routen ersetzen `PlaybackSettingsResource.blockArtist()`/`unblockArtist()` (siehe
     [Entschiedene Fragen](#entschiedene-fragen-aus-review-feedback)).
6. **Settings-UI für Assumption-Auflösung:** Neue Seite (z. B. `/settings/artists`), die alle Artists mit Status
   `SYNC_ASSUMPTION`/`SHALLOW_ASSUMPTION` auflistet, mit Aktionen "auf Sync setzen" und "auf Shallow setzen" (jeweils
   direkter Übergang in den finalen Status). Ein Umschalten zwischen den beiden Assumption-Status ist **nicht**
   vorgesehen (Korrektur ggü. vorheriger Planversion, siehe Datenmodell-Konzept oben).
7. **Aggregation:** Da Playback-Events unabhängig vom Artist-Status immer gespeichert werden (Schritt 4), filtert
   `PlaybackAggregationService.aggregateDay()` weiterhin anhand des Artist-Status (analog zum heutigen
   `blockedFromAggregation`-Filter, Zeilen 217-224) – Items von `SHALLOW`/`SHALLOW_ASSUMPTION`-Artists werden von der
   Aggregation/Anzeige ausgeschlossen, bleiben aber in `app_playback` erhalten.
8. **`docs/arc42/arc42.md` aktualisieren:** Die dort beschriebene, im Code nicht existierende
   `playbackProcessingStatus`/`ACTIVE`/`INACTIVE`/`UNDECIDED`-Doku durch das tatsächlich implementierte
   `ArtistSyncStatus`-Konzept ersetzen.

## Entschiedene Fragen (aus Review-Feedback)

Die folgenden, ursprünglich offenen Fragen wurden durch das Review-Feedback von @christiangroth beantwortet:

- **Kein Datenverlust bei Playback-Events (vormals Fragen 9/10):** Ein Wechsel `SYNC → SHALLOW → SYNC` darf keine
  historischen Playback-Events unbrauchbar machen. Damit ist Option A (Ingestion-Filterung) ausgeschlossen; es gilt
  Option B (Soft-Flag, Filterung zur Aggregations-/Query-Zeit, siehe oben). Playback-Events werden nie gelöscht.
- **`blockedFromAggregation` wird vollständig ersetzt (vormals Frage 1):** Kein Parallelbetrieb der beiden Mechanismen.
  Die zugehörige Catalog-UI (Block/Unblock-Buttons, vormals Frage 14) wird durch die neuen Sync/Shallow-Buttons ersetzt,
  nicht ergänzt.
- **Kein manueller Wechsel zwischen den Assumption-Status (vormals Frage 5):** `SYNC_ASSUMPTION`/`SHALLOW_ASSUMPTION`
  sind ausschließlich automatisch vom System vergebene Anfangszustände für neu entdeckte Artists. Der Nutzer kann aus
  einem Assumption-Status heraus nur direkt in `SYNC` oder `SHALLOW` wechseln (Settings-UI); ein Umschalten zwischen
  den beiden Assumption-Status entfällt. Damit ist auch **Frage 12 beantwortet**: Der reguläre Statuswechsel in der
  Catalog-UI (`SYNC` ↔ `SHALLOW`) gilt ausschließlich für Artists in einem finalen Status; Assumption-Status-Artists
  sind ausschließlich über die Settings-UI editierbar.
- **Migration bestehender Artists (vormals Frage 2):** Alle heute bereits existierenden `app_artist`-Dokumente erhalten
  bei der Migration `SYNC_ASSUMPTION` (siehe aber neue Offene Frage 2 unten zum Sonderfall `blockedFromAggregation=true`).
- **Definition "auf synchronisierter Playlist" (vormals Frage 3):** Die Playlist muss aktiv im Sync sein. Eine dem
  Nutzer bekannte, aber nicht aktiv synchronisierte Playlist reicht nicht aus.
- **`SYNC_ASSUMPTION` löst spekulativen Voll-Sync aus (vormals Frage 4):** Ja – der Alben-/Track-Sync wird bereits im
  Assumption-Status optimistisch angestoßen. Ein späterer Wechsel zu `SHALLOW` löscht ggf. zu viel synchronisierte
  Daten wieder (Umsetzungsschritt 5).
- **Main-Artist-Regel für Multi-Artist-Alben/Tracks (vormals Frage 7):** Ausschlaggebend ist immer nur der Main-Artist
  (erster Artist in der Spotify-Artist-Liste eines Albums/Tracks). Dieses Prinzip wird bereits heute in
  `CatalogService.buildCatalogSyncRequest()` (Zeile 219) verwendet.
- **Laufende Outbox-Tasks werden gelöscht (vormals Frage 6, teilweise):** Beim Wechsel zu `SHALLOW` sollen bereits
  laufende `SyncArtistAlbums`/`SyncAlbumDetails`-Tasks für den Artist gelöscht werden. Die dafür nötige Fähigkeit fehlt
  aktuell in der externen `quarkus-outbox`-Bibliothek ([christiangroth/quarkus-outbox](https://github.com/christiangroth/quarkus-outbox),
  siehe [ADR-0007](../adr/0007-persistent-outbox-pattern.md)): Weder `OutboxPort` (`enqueue`, `getPartitionStats`,
  `getTasksByPartition`) noch `OutboxAdminPort` (nur `wipeAll()`) bieten eine gezielte Lösch-/Stornierungsfunktion für
  einzelne Tasks.
  - [ ] Feature Request in `christiangroth/quarkus-outbox` eröffnen: API zum gezielten Löschen/Stornieren einzelner
    ausstehender Tasks anhand von Partition + Event-Key/Payload-Präfix (z. B. alle `SyncArtistAlbums`/`SyncAlbumDetails`
    für eine `artistId`), analog zum bereits in [hexagonal-cleanup.md](hexagonal-cleanup.md) dokumentierten Feature
    Request für `getTasksByPartition()`. Zu klären dabei: `SyncAlbumDetails` trägt aktuell nur `albumId`, keine
    `artistId` (`DomainOutboxEvent.SyncAlbumDetails`, Zeile 137) – eine Löschung "nach Artist" für bereits enqueuete
    Album-Tasks ist damit ohne Zusatzinformation im Event nicht direkt möglich (siehe neue Offene Frage 3 unten).
  - Bis die Bibliothek diese Fähigkeit bereitstellt, bleibt die Race Condition zwischen laufendem Sync und
    `SHALLOW`-Wechsel bestehen (siehe neue Offene Frage 1 unten).

## Offene Fragen

1. **(vormals Frage 6, Rest):** Solange `quarkus-outbox` keine gezielte Task-Löschung unterstützt: Wie wird die
   Race Condition zwischen einem laufenden `SyncArtistAlbums`/`SyncAlbumDetails`-Sync und einem `SHALLOW`-Wechsel
   überbrückt? Denkbare Interims-Lösung: Der `SyncArtistAlbums`/`SyncAlbumDetails`-Handler prüft bei Ausführung erneut
   den aktuellen `syncStatus` des Artists und bricht ab, falls dieser inzwischen auf `SHALLOW` gewechselt hat – müsste
   aber explizit entschieden werden, da dies kein Ersatz für die eigentlich gewünschte Task-Löschung ist.
2. **(neu, aus vormals Frage 1/2):** Bestehende Artists mit `blockedFromAggregation=true` erhalten laut Vorgabe
   ebenso wie alle anderen bestehenden Artists pauschal `SYNC_ASSUMPTION` bei der Migration. Damit geht die Information
   "war geblockt" verloren, obwohl diese Artists inhaltlich eher `SHALLOW`/`SHALLOW_ASSUMPTION` entsprechen. Ist das
   gewollt, oder sollen `blockedFromAggregation=true`-Artists abweichend auf `SHALLOW_ASSUMPTION` (oder direkt
   `SHALLOW`) migriert werden?
3. **(neu, aus Outbox-Feature-Request):** Muss `DomainOutboxEvent.SyncAlbumDetails` um `artistId` (Main-Artist)
   erweitert werden, damit sich enqueuete Album-Sync-Tasks überhaupt einem Artist zuordnen und gezielt löschen lassen,
   sobald die Bibliothek eine entsprechende API bereitstellt?
4. Referenzieren Playlist-Items (`AppPlaylistTrack` o. ä.) Tracks/Alben, die durch den Wechsel zu `SHALLOW` gelöscht
   würden? Falls ja, wie verhält sich die Playlist-Anzeige für dann fehlende Track-Metadaten?
5. Wo genau liegt die Settings-UI-Seite (neuer Pfad `/settings/artists`, oder Erweiterung von `/settings/playback`)?
   Reicht eine schlanke Liste (nur Assumption-Status-Artists, wie im Issue beschrieben), oder soll dieselbe
   Artist-Tabellen-Komponente wie in `catalog.html` wiederverwendet werden?
6. Soll die tägliche `ArtistCatalogSyncJob`/`resyncCatalog()`-Logik ebenfalls an der Status-Prüfung teilnehmen (also
   z. B. `SHALLOW`-Artists von automatischen Resyncs ausnehmen), oder betrifft "Shallow" ausschließlich den initialen
   Discovery-Pfad über `SyncController`?
