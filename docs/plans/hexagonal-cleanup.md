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

`ApplicationOutboxClient.partitionInfos()` liefert nur aggregierte Partition-Statistiken (Event-Counts,
Typ-Counts), jedoch keine einzelnen Task-Details. Die notwendige Funktionalität ist in der
`quarkus-outbox`-Bibliothek nicht vorhanden.

- [ ] Feature Request in `christiangroth/quarkus-outbox` eröffnen: `ApplicationOutboxClient` um
  `getTasksByPartition(partitionKey: String): List<OutboxTaskInfo>` erweitern, sodass der direkte
  MongoDB-Zugriff in `OutboxPortAdapter` entfällt
- [ ] Sobald die Bibliothek die API bereitstellt: `MongoClient`-Zugriff in `OutboxPortAdapter` durch
  den neuen Client-Aufruf ersetzen und `MongoClient`-Abhängigkeit aus `adapter-out-outbox` entfernen

### Gut strukturiert – keine weiteren Findings

`OutboxPortAdapter` verwendet `kotlin.time.Instant` an allen Stellen. Keine offenen Findings.

---

## adapter-out-scheduler

### Gut strukturiert – keine strukturellen Verletzungen

`PlaybackActivityAdapter` verwendet `kotlin.time` an allen Stellen.
`PlaybackDetectedEvent` wurde aus `domain-api/model` entfernt und durch den Port
`PlaybackDetectedObserver` in `domain-api/port/out/playback/` ersetzt. Keine offenen Findings.

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
