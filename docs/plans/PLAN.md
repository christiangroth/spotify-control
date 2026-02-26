# Projektplan: Spotify Playlist Manager

Stand: 2026.02.24

## Leitprinzip

Jedes Inkrement ist **deploybar und in sich abgeschlossen**. Keine halbfertigen Features auf dem VPS. Nach jeder Phase kannst du zwei Monate pausieren und weißt beim Wiedereinstieg
genau wo du stehst.

## Phase 1: Fundament & Playback-Tracking

*Ziel: So schnell wie möglich Daten sammeln. Alles andere ist sekundär.*

- Lokale Entwicklung klären
- Spotify Poll-Job: `recently_played` alle 5 Minuten → Raw Events in MongoDB
- Outbox-Grundgerüst (nur Spotify-Partition, nur PollRecentlyPlayed)
- Minimalstes Dashboard: Login-Status, "X Events gesammelt seit Y"

**Nach Phase 1 läuft der Service im Hintergrund und sammelt Daten. Das ist der wichtigste Meilenstein.**

## Phase 2: Playlist-Spiegel

*Ziel: Lokale Kopie der relevanten Playlists.*

- Playlist-Auswahl im Admin (`included_in_sync`)
- Initialer Playlist-Sync (Tracks, Artists, Alben) – gedrosselt via Outbox
- Snapshot-ID-Check Job (alle 15 Min)
- `GET /v1/me/playlists` für effizienten Batch-Check
- Onboarding-Flow: erster Login → direkt zur Playlist-Auswahl

**Nach Phase 2 hast du einen lokalen Spiegel deiner Playlists.**

## Phase 3: Enrichment-Pipeline

*Ziel: Rohdaten aufwerten, Genre-Grundlage schaffen.*

- Enrichment-Service: Raw → Enriched Events
- Skip-Erkennung
- Genre-Logik: Artist-Genres auf Tracks ableiten, `genre.source` setzen
- `EnrichmentTrigger` Enum und CDI Event-Bus
- DevFixtures für Playback-Events (kontrollierte Testdaten)

**Nach Phase 3 sind alle gesammelten Raw Events aufgewertet.**

## Phase 4: Reports & Charts

*Ziel: Erste Insights aus den gesammelten Daten.*

- Aggregations-Pipeline: Raw → Enriched → `aggregations_monthly`
- Contract-Tests für Charts-Vertrag
- MongoDB Charts: erste Dashboards (Top Artists, Plays pro Monat, Heatmap)
- Charts-Embedding im Frontend
- Dashboard: Stats-Widget für letzte 30 Tage

**Nach Phase 4 siehst du zum ersten Mal dein Hörverhalten als Report.**

## Phase 5: Genre-Overrides

*Ziel: Daten nach eigenen Vorstellungen korrigieren.*

- Genre-Override Admin-UI (pro Album/Release)
- `ApplyGenreOverride` Outbox-Event
- Re-Enrichment und Re-Aggregation bei Override-Änderung
- `enrichment_version` Mechanismus

**Nach Phase 5 sind deine Reports so korrekt wie du sie haben willst.**

## Phase 6: Playlist-Logik

*Ziel: Der eigentliche Kern – automatische Playlist-Verwaltung.*

- Invariante: Jahres-Playlist → All Sync
- Duplikat-Erkennung und -Entfernung
- Album-Upgrade Matching-Heuristik
- Pending Upgrades Collection
- Dashboard: Ausstehende Entscheidungen mit Approve/Reject Links
- Slack: Incoming Webhook für Benachrichtigungen

**Nach Phase 6 hält die App deine Playlists automatisch sauber.**

## Phase 7: Spotify Web Playback SDK

*Ziel: Präziseres Tracking wenn am Rechner.*

- SDK-Integration im Frontend
- `player_state_changed` → POST an Backend
- `source: "sdk"` in Raw Events
- Poll-Job als reiner Fallback

**Nach Phase 7 sind Skip-Erkennung und Timestamps deutlich genauer.**

## Phase 8: Slack Bot (optional)

*Ziel: Genre-Overrides und Playlist-Selection aus Slack.*

- Echte Slack App mit Block Kit
- `adapter-in-slack` mit Signatur-Validierung
- Genre-Override via Slack Input
- Playlist-Selection via Slack Checkboxen

**Nice-to-have. Nur angehen wenn Phase 1–7 stabil laufen.**
