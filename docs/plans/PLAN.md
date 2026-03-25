# Projektplan: Spotify Playlist Manager

Stand: 2026.03.03

## Phase 4: Reports & Charts

*Ziel: Erste Insights aus den gesammelten Daten.*

- Aggregations-Pipeline: Raw → Enriched → `aggregations_monthly`
- Charts: erste Dashboards (Top Artists, Plays pro Monat, Heatmap)
- Charts-Embedding im Frontend
- Dashboard: Stats-Widget für letzte 30 Tage

**Nach Phase 4 siehst du zum ersten Mal dein Hörverhalten als Report.**

## Phase 5: ~~Genre-Overrides~~ (removed)

*Genre information is no longer provided by Spotify – this phase is cancelled.*

## Phase 6: Playlist-Logik

*Ziel: Der eigentliche Kern – automatische Playlist-Verwaltung.*

- Invariante: Jahres-Playlist → All Sync
- Duplikat-Erkennung und -Entfernung
- Album-Upgrade Matching-Heuristik
- Pending Upgrades Collection
- Dashboard: Ausstehende Entscheidungen mit Approve/Reject Links
- Slack: Incoming Webhook für Benachrichtigungen

**Nach Phase 6 hält die App deine Playlists automatisch sauber.**
