# Hexagonal Cleanup Plan

Dieses Dokument fasst alle identifizierten Architekturverletzungen und Verbesserungspotenziale aus dem
Architecture Review zusammen. Ziel ist eine saubere, konsequente Hexagonal-Architektur mit klarer
Subdomain-Trennung und konsistentem Naming über alle Module hinweg.

---

## domain-api

---

## domain-impl

---

## adapter-in-web

---

## adapter-in-starter

### Gut strukturiert – keine strukturellen Verletzungen

Alle einmaligen Migrations-Starter wurden entfernt. Dauerhaft relevante Starters
(`WipePlaylistDocumentsStarter`, `WipePlaylistChecksStarter`) sind erhalten geblieben.

---

## adapter-out-mongodb

### Gut strukturiert – keine strukturellen Verletzungen

Spotify-Präfix aus MongoDB-Dokumentnamen entfernt. Keine offenen Findings.

---

## adapter-out-spotify

### Gut strukturiert – keine strukturellen Verletzungen

Das Modul hält sich an die Architektur-Regeln: Spotify-API-Modelle sind `internal` und nie im domain-api
sichtbar. Keine kritischen Findings.

---

## adapter-in-outbox

### Gut strukturiert – keine strukturellen Verletzungen

Keine offenen Findings.

---

## adapter-in-scheduler

### Gut strukturiert – keine strukturellen Verletzungen

`CurrentlyPlayingSkipPredicate` auf `kotlin.time` migriert. Keine offenen Findings.

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

---

## Übergreifende Maßnahmen

### Subdomain-Sichtbarkeit durch Package-Struktur

Ziel: Eine Entwicklerin, die zum ersten Mal in den Code schaut, soll sofort erkennen können, zu
welcher Subdomain eine Klasse gehört.

- [x] Konsistentes Package-Schema in allen Modulen etablieren:
  - `domain-api`: `domain/model/{catalog,playback,playlist,user,infra,viewer}/`, `domain/port/{in,out}/{catalog,playback,playlist,user,infra}/`
  - `domain-impl`: `domain/{catalog,playback,playlist,user,infra}/` (inkl. `playlist/check/` für Check-Runner)
  - `adapter-*`: keine Subdomain-Aufteilung nötig (Module sind bereits klein genug)

### Architektur-Tests ergänzen

- [ ] ArchUnit oder ähnliche Bibliothek einsetzen, um die Modul-Abhängigkeitsregeln als automatische
  Tests zu verifizieren:
  - `domain-api` darf keine externen Abhängigkeiten haben
  - `domain-impl` darf nur `domain-api` importieren (kein `adapter-*`)
  - `adapter-*` darf nur `domain-api` importieren (kein `domain-impl`, kein anderes `adapter-*`)
  - Framework-Annotationen (außer CDI- und Config-Annotationen wie `@ApplicationScoped`, `@ConfigProperty`) dürfen nicht in `domain-impl`-Klassen erscheinen
