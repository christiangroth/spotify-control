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

Neues Feld `AppArtist.syncStatus: ArtistSyncStatus` (ersetzt `blockedFromAggregation`, siehe Offene Frage 1) mit vier
Ausprägungen:

- **`SYNC`** – finaler Status. Artist wird voll synchronisiert (Alben, Tracks), Playback-Events werden gespeichert und
  aggregiert. Entspricht dem heutigen Standardverhalten.
- **`SHALLOW`** – finaler Status. Nur das Artist-Dokument wird synchronisiert. Keine Alben-/Track-Synchronisation,
  vorhandene Alben/Tracks werden beim Wechsel gelöscht. Playback-Events dieses Artists werden weder gespeichert noch
  aggregiert.
- **`SYNC_ASSUMPTION`** – Übergangsstatus für einen neu entdeckten, noch nicht vom Nutzer bestätigten Artist, bei dem das
  System vermutet, dass voller Sync gewünscht ist (weil der Artist auf einer synchronisierten Playlist vorkommt).
- **`SHALLOW_ASSUMPTION`** – Übergangsstatus für einen neu entdeckten Artist ohne Playlist-Bezug (z. B. nur über
  Recently-Played-Historie gesehen). Vermutung: nur Shallow-Sync gewünscht.

Zustandsübergänge:

```
(neu entdeckt) ──auf Playlist?──► SYNC_ASSUMPTION ◄──Settings-UI umschalten──► SHALLOW_ASSUMPTION
                                        │                                              │
                                        └───────────────► Settings-UI: bestätigen ◄────┘
                                                              │              │
                                                              ▼              ▼
                                                            SYNC  ◄────►  SHALLOW
                                                              (Catalog-UI, regulärer Wechsel)
```

- Die beiden Assumption-Status können nur ineinander (Settings-UI) oder in einen der beiden finalen Status wechseln
  (Bestätigung über Settings-UI). Ein direkter Rücksprung von `SYNC`/`SHALLOW` in einen Assumption-Status ist nicht
  vorgesehen.
- Der reguläre Wechsel zwischen den finalen Status `SYNC` und `SHALLOW` erfolgt über die Catalog-UI (analog zu den
  bestehenden Block/Unblock-Buttons in `catalog.html`).

## Ansätze

### Option A: Playback-Filterung zur Ingestion-Zeit

Beim Empfang neuer Playback-Daten (`PlaybackService.appendNewPlaybackData()`) wird der Artist-Status der betroffenen
Tracks geprüft; Items von `SHALLOW`/`SHALLOW_ASSUMPTION`-Artists werden gar nicht erst in `app_playback` (bzw. den
Recently-Played/Partial-Played-Collections) geschrieben.

- Vorteil: Aggregation bleibt unverändert (arbeitet weiterhin auf allen gespeicherten Rohdaten), kein doppelter
  Filter-Layer.
- Nachteil: Ein späterer Wechsel zurück zu `SYNC` kann historische Playback-Events nicht mehr rekonstruieren – Spotifys
  `recently-played`-Endpunkt liefert nur ein kurzes Zeitfenster (ca. 50 Einträge). Ein "Erzeugen" von Playback-Events beim
  Wechsel `SHALLOW → SYNC` ist damit nur für ab diesem Zeitpunkt neu eintreffende Daten möglich, nicht rückwirkend.

### Option B: Soft-Flag mit Filterung zur Aggregations-/Query-Zeit (analog heutigem `blockedFromAggregation`)

Playback-Events werden wie heute ungefiltert gespeichert; die Filterung nach Artist-Status erfolgt ausschließlich in
`PlaybackAggregationService.aggregateDay()` und ggf. in Lesepfaden (z. B. "zuletzt gehört"-Ansichten).

- Vorteil: Kein Datenverlust – ein Wechsel `SHALLOW → SYNC` macht historische Daten sofort wieder vollständig sichtbar,
  ein einfacher `rebuildAllAggregations()`-Aufruf reicht aus (bereits vorhandener Mechanismus).
- Nachteil: Widerspricht der Anforderung "von Playback Events ausschließen" aus dem Issue – rohe Playback-Events für
  Shallow-Artists würden weiterhin dauerhaft gespeichert, nur nicht ausgewertet.

## Empfehlung

**Option A (Ingestion-Filterung) für Neuzugänge, kombiniert mit hartem Löschen bestehender Daten beim Wechsel zu
`SHALLOW`.** Das entspricht der expliziten Anforderung "von Playback Events ausschließen" und "Cleanup ... wenn Artist
Status Change" aus dem Issue. Der Datenverlust bei einem erneuten Wechsel zurück zu `SYNC` ist eine bewusste Konsequenz des
Konzepts und wird als Offene Frage 9 zur Bestätigung durch den Nutzer aufgeführt, da er die Kernentscheidung des Features
betrifft.

## Umsetzungsschritte (grob)

1. **Datenmodell:** `AppArtist`/`AppArtistDocument` um `syncStatus: ArtistSyncStatus` (Enum `SYNC`, `SHALLOW`,
   `SYNC_ASSUMPTION`, `SHALLOW_ASSUMPTION`) ergänzen. Migration bestehender Artists auf `SYNC` (siehe Offene Frage 2).
2. **Discovery/Assumption-Zuordnung:** `SyncController.syncForTracks()`/`syncArtists()` unterscheiden bereits heute
   zwischen Playlist- und Playback-Ursprung (Aufrufkontext). Bei erstmaligem Anlegen eines Artists (in
   `CatalogService.syncArtistDetails()`, nach dem Spotify-Fetch) wird anhand des Ursprungs `SYNC_ASSUMPTION` (Playlist)
   oder `SHALLOW_ASSUMPTION` (nur Playback) gesetzt. Dazu muss der Ursprung bis zum `SyncArtistDetails`-Handler
   durchgereicht werden – aktuell trägt das Event nur `artistId` (`DomainOutboxEvent.SyncArtistDetails`, Zeile 118).
3. **Catalog-Sync-Branching:** `CatalogService.syncArtistDetails()` enqueued `SyncArtistAlbums` nur noch, wenn der
   (neue oder bestehende) Status `SYNC` ist. Für `SHALLOW`, `SYNC_ASSUMPTION` und `SHALLOW_ASSUMPTION` wird kein
   Alben-Sync angestoßen (siehe Offene Frage 4 zum Sync-Verhalten von `SYNC_ASSUMPTION`).
4. **Playback-Ingestion-Filter:** `PlaybackService.appendNewPlaybackData()` lädt den Artist-Status der betroffenen Tracks
   und überspringt Items von `SHALLOW`/`SHALLOW_ASSUMPTION`-Artists beim Schreiben nach `app_playback` (siehe Option A).
5. **Regulärer Statuswechsel (Catalog-UI):** Neue Methoden `CatalogPort.setArtistSyncStatus(artistId, SYNC|SHALLOW)`,
   analog zu `blockArtistFromAggregation`/`unblockArtistFromAggregation`:
   - `→ SYNC`: enqueued `SyncArtistAlbums(artistId)`.
   - `→ SHALLOW`: löscht alle `AppAlbum`/`AppTrack`-Einträge des Artists (neue Repository-Methoden
     `deleteByArtistId` in `AppAlbumRepositoryPort`/`AppTrackRepositoryPort`, aktuell nicht vorhanden – siehe Offene
     Frage 7 zu Cross-Artist-Alben) und die zugehörigen `app_playback`-Einträge, danach `rebuildAllAggregations()`.
   - Neue Buttons/Routen analog zu `PlaybackSettingsResource.blockArtist()`/`unblockArtist()`.
6. **Settings-UI für Assumption-Auflösung:** Neue Seite (z. B. `/settings/artists`), die alle Artists mit Status
   `SYNC_ASSUMPTION`/`SHALLOW_ASSUMPTION` auflistet, mit Aktionen "auf Sync setzen", "auf Shallow setzen" und "Assumption
   umschalten" (`SYNC_ASSUMPTION` ↔ `SHALLOW_ASSUMPTION`).
7. **Aggregation:** Da Shallow-Artist-Playback bereits bei der Ingestion ausgefiltert wird, ist in
   `PlaybackAggregationService.aggregateDay()` kein zusätzlicher Filter nötig; der bestehende `blockedFromAggregation`-Filter
   entfällt bzw. wird durch die Status-Prüfung ersetzt (siehe Offene Frage 1).
8. **`docs/arc42/arc42.md` aktualisieren:** Die dort beschriebene, im Code nicht existierende
   `playbackProcessingStatus`/`ACTIVE`/`INACTIVE`/`UNDECIDED`-Doku durch das tatsächlich implementierte
   `ArtistSyncStatus`-Konzept ersetzen.

## Offene Fragen

1. Soll das neue `syncStatus`-Feld das bestehende `blockedFromAggregation: Boolean` vollständig ersetzen, oder sollen
   beide parallel existieren (z. B. `SYNC` + `blockedFromAggregation=true` als zusätzliche Zwischenstufe "voll
   synchronisiert, aber aus Statistik ausgeblendet")? Falls ersetzt: Wie werden bestehende `blockedFromAggregation=true`
   Artists migriert (`SHALLOW` oder eigener Status)?
2. Welchen `syncStatus` erhalten alle heute bereits existierenden `app_artist`-Dokumente bei der Migration – pauschal
   `SYNC` (sicherste Variante, kein Datenverlust, aber kein rückwirkendes Shallow-Konzept), oder soll rückwirkend anhand
   von Playlist-Zugehörigkeit klassifiziert werden (wie bei Neuzugängen)?
3. Was genau bedeutet "auf einer synchronisierten Playlist vorhanden"? Reicht die Mitgliedschaft in irgendeiner aktuell
   gespiegelten Playlist (`PlaylistService`), oder nur in explizit als "aktiv verfolgt" markierten Playlists? Gibt es
   einen bestehenden Playlist-Status, der das abbildet?
4. Wird für `SYNC_ASSUMPTION` bereits spekulativ ein voller Alben-/Track-Sync angestoßen (in der Annahme, dass die
   Vermutung meist zutrifft), oder wartet der Sync bis zur expliziten Bestätigung durch den Nutzer (dann bleibt der
   Artist bis zur Bestätigung faktisch "shallow", auch wenn die Endabsicht `SYNC` ist)? Der Plan geht aktuell von
   Letzterem aus (Schritt 3), das Issue lässt das aber offen.
5. Lösen Wechsel zwischen den beiden Assumption-Status (`SYNC_ASSUMPTION` ↔ `SHALLOW_ASSUMPTION`) bereits
   Playback-/Aggregations-Aktualisierungen aus, oder erst der finale Übergang in `SYNC`/`SHALLOW`? Das Issue beschreibt
   die Aktualisierungspflicht nur für den Wechsel "von Shallow zu Sync und umgekehrt", was auf die finalen Status
   beschränkt sein könnte.
6. Was passiert mit bereits laufenden Outbox-Tasks (`SyncArtistAlbums`/`SyncAlbumDetails`), wenn ein Artist mitten im
   Sync auf `SHALLOW` gesetzt wird? Sollen offene Tasks storniert werden, oder greift die Löschung erst nach deren
   Abschluss (Race Condition zwischen Sync und Cleanup)?
7. Wie wird mit Alben/Tracks umgegangen, die mehrere Artists referenzieren (z. B. Compilations, Feature-Tracks)? Löscht
   der Wechsel zu `SHALLOW` nur Alben/Tracks, die ausschließlich diesem Artist zuzuordnen sind, oder auch solche, bei
   denen der Artist nur als Nebenkünstler auftaucht? Hinweis: `SyncAlbumDetails` verhindert laut Code-Kommentar bereits
   heute "unbounded fanout into artists without playback events" – das vorhandene Muster sollte hier wiederverwendet
   werden.
8. Referenzieren Playlist-Items (`AppPlaylistTrack` o. ä.) Tracks/Alben, die durch den Wechsel zu `SHALLOW` gelöscht
   würden? Falls ja, wie verhält sich die Playlist-Anzeige für dann fehlende Track-Metadaten?
9. Bestätigung der Kernentscheidung aus der Empfehlung: Ist es akzeptabel, dass ein Wechsel `SYNC → SHALLOW → SYNC`
   dauerhaften Datenverlust bei historischen Playback-Events erzeugt (da Spotifys Recently-Played-Historie zeitlich
   begrenzt ist und "erzeugen" beim Zurückwechseln nur neu eintreffende Daten erfassen kann)?
10. Soll das Löschen von Playback-Events beim Wechsel zu `SHALLOW` ein Hard-Delete aus `app_playback` sein, oder reicht
    ein Soft-Filter (Ausschluss bei Aggregation/Anzeige, Rohdaten bleiben erhalten)? Hängt direkt mit Frage 9 zusammen.
11. Wo genau liegt die Settings-UI-Seite (neuer Pfad `/settings/artists`, oder Erweiterung von `/settings/playback`)?
    Reicht eine schlanke Liste (nur Assumption-Status-Artists, wie im Issue beschrieben), oder soll dieselbe
    Artist-Tabellen-Komponente wie in `catalog.html` wiederverwendet werden?
12. Gilt der reguläre Statuswechsel in der Catalog-UI (`SYNC` ↔ `SHALLOW`) für jeden Artist unabhängig vom aktuellen
    Status, oder ausschließlich für Artists, die bereits einen finalen Status haben (Assumption-Status wären dann
    ausschließlich über die Settings-UI editierbar)?
13. Soll die tägliche `ArtistCatalogSyncJob`/`resyncCatalog()`-Logik ebenfalls an der Status-Prüfung teilnehmen (also
    z. B. `SHALLOW`-Artists von automatischen Resyncs ausnehmen), oder betrifft "Shallow" ausschließlich den initialen
    Discovery-Pfad über `SyncController`?
14. Falls `blockedFromAggregation` erhalten bleibt (siehe Frage 1): Muss die zugehörige Catalog-UI (Block/Unblock-Buttons
    in `catalog.html`) parallel zu den neuen Shallow/Sync-Buttons bestehen bleiben, oder werden beide UI-Elemente
    zusammengeführt?
