# Fachlicher Kontext

## Produkt

Privater Spotify Playlist Manager für einen einzelnen User. Intelligentere Playlist-Verwaltung als Spotify selbst, eigene Hörstatistiken, automatische Playlist-Pflege nach festen Regeln.

## User & Zugang

- Exakt ein User (Entwickler = Nutzer)
- Login ausschließlich via Spotify OAuth
- Kein Multi-Tenancy, keine Registrierung, keine Userverwaltung

## Kern-Features (nach Priorität)

### 1. Playback-Tracking
Spotify `recently_played` wird alle 5 Min gepollt und als Raw Events gespeichert. Ziel: so früh wie möglich Daten sammeln um Hörverlauf nicht zu verpassen. Perspektivisch ergänzt durch Spotify Web Playback SDK (präzisere Timestamps, Skip-Erkennung) wenn User am Rechner hört.

### 2. Playlist-Spiegel
Lokale Kopie relevanter Spotify-Playlists (vom User ausgewählt via `included_in_sync`). Jede Playlist hat einen Typ (Enum, frei definierbar). Sync über Snapshot-IDs – nur bei Änderung wird vollständig synchronisiert.

### 3. Automatische Playlist-Pflege
Feste Regeln:
- **All-Invariante:** `All`-Playlist = Vereinigung aller Jahres-Playlists. Wird nie manuell editiert, immer automatisch aktuell gehalten.
- **Keine Duplikate:** Weder auf Jahres-Playlists noch auf `All`.
- **Album-Upgrade:** Track von Single/EP wird durch Album-Version ersetzt sobald Album erscheint. Matching via Track-Name + Artist. Erfordert Bestätigung durch User (nicht vollautomatisch).

### 4. Genre-Management
Spotify liefert Genres nur auf Artist-Ebene. Genres werden vom Artist auf Tracks abgeleitet (`genre.source = "artist"`). User kann Genres pro Album/Release überschreiben (`genre.source = "override"`). Overrides lösen Re-Enrichment und Re-Aggregation aus.

### 5. Reports & Hörstatistiken
Spotify Wrapped-Style, aber jederzeit und detaillierter. Basiert auf eigenen gesammelten Daten. Visualisierung via MongoDB Charts (embedded).

## Entscheidungs-Flow: Album-Upgrade

```
Match gefunden (Track-Name + Artist, Single/EP → Album)
    → Eintrag in pending_upgrades (UUID, expires_at)
    → Slack-Benachrichtigung mit Approve/Reject-Links
    → Links erfordern eingeloggte Session → Redirect nach Login auf Dashboard
    → Dashboard zeigt alle pending_upgrades
    → Aktion ausgeführt → Redirect auf Dashboard mit Success-Banner (verschwindet nach 10s)
```

## Slack-Integration

- **Phase 1 (jetzt):** Incoming Webhook – reine Benachrichtigungen + Links
- **Phase 2 (später):** Echter Slack Bot für Genre-Overrides und Playlist-Selection via Block Kit (erfordert Texteingabe/Mehrfachauswahl → Links reichen nicht)

## Phasenplan

Siehe `docs/05-phasenplan.md` (TODO: anlegen). Kurzfassung:
1. Fundament + Playback-Tracking (höchste Priorität – Daten sammeln)
2. Playlist-Spiegel
3. Enrichment-Pipeline
4. Reports & Charts
5. Genre-Overrides
6. Playlist-Logik (Album-Upgrade, All-Invariante)
7. Spotify Web Playback SDK
8. Slack Bot (optional)
