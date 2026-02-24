# Technisches Konzept: Backend

> Fachlicher Kontext: siehe `01-fachlicher-kontext.md`
> Rollen & Code-Stil: siehe `roles/role-backend-developer.md`

## Stack

- **Sprache:** Kotlin
- **Framework:** Quarkus (Gradle)
- **Datenbank:** MongoDB Atlas (zwei Projekte: prod + dev)
- **Reaktiv:** Mutiny (`Uni`, `Multi`)
- **Testing:** `@QuarkusTest`

## Modulstruktur (Hexagonal)

```
adapter-in-rest/        → REST Endpoints, OAuth Callback, SSE, Action-Endpoints
adapter-in-outbox/      → Outbox-Poller, Event-Router zu Domain-Ports
adapter-in-slack/       → Slack Interaction Endpoint (Phase 2), Signatur-Validierung
domain-api/                 → Ports (Interfaces)
domain-impl/                → fachliche Services, Domain-Objekte, CDI Events
adapter-out-spotify/    → SpotifyApiClient, TokenRefresh, Token Bucket, Backoff
adapter-out-mongodb/    → Repository-Implementierungen
adapter-out-slack/      → SlackApiClient, Block Kit Message Builder
```

**Invariante:** Domain hat keine Abhängigkeiten auf Adapter. Spotify-HTTP nur in `adapter-out-spotify`. MongoDB-Queries nur in `adapter-out-mongodb`.

## Outbox-Pattern

Alle Spotify-Operationen laufen über die persistente Outbox. Kein direkter Spotify-Call außerhalb von `adapter-out-spotify`.

**Partitionen und Event-Typen:**

| Partition | Event-Typ |
|---|---|
| `spotify` | `SyncPlaylist`, `SyncTrack`, `SyncArtist`, `PushPlaylistEdit`, `PollRecentlyPlayed` |
| `domain` | `EnrichPlaybackEvents`, `RecomputeAggregations`, `ApplyGenreOverride`, `SyncPlaylistInvariant`, `CheckAlbumUpgrades`, `ApplyAlbumUpgrade` |

Erfolgreich verarbeitete Events → `outbox_archive` (Audit-Log).
Interne Trigger zwischen Services → CDI Events (nicht Outbox).

```kotlin
enum class EnrichmentTrigger { NEW_EVENTS, GENRE_OVERRIDE_ARTIST, FULL_RECOMPUTE }
```

## MongoDB Collections

### `tracks`
```json
{
  "_id": "spotify:track:id",
  "name": "", "duration_ms": 0, "explicit": false, "popularity": 0,
  "album": { "spotify_id": "", "name": "", "release_year": 0, "release_date": "" },
  "artists": [{ "spotify_id": "", "name": "" }],
  "genre": {
    "values": [], "source": "artist|album|override", "source_ref": "",
    "overridden": false, "override_values": null, "override_updated_at": null
  },
  "spotify_synced_at": ""
}
```

### `artists`
```json
{ "_id": "spotify:artist:id", "name": "", "genres": [], "popularity": 0, "spotify_synced_at": "" }
```

### `playlists`
```json
{
  "_id": "spotify:playlist:id",
  "name": "", "snapshot_id": "", "included_in_sync": true, "playlist_type": "ENUM_VALUE",
  "track_count": 0,
  "tracks": [{ "track_ref": "spotify:track:id", "added_at": "", "position": 0 }],
  "spotify_synced_at": ""
}
```

Tracks als Referenzen (nicht eingebettet) – Dokument-Größenlimit und Redundanzvermeidung.

### `playback_events_raw` (append-only, niemals mutieren)
```json
{
  "track_ref": "spotify:track:id",
  "played_at": "", "poll_captured_at": "",
  "source": "poll|sdk",
  "enrichment_status": "pending|enriched|stale"
}
```

### `playback_events_enriched`
```json
{
  "raw_event_ref": "ObjectId", "track_ref": "", "played_at": "",
  "track_name": "", "artist_names": [], "album_name": "", "release_year": 0,
  "duration_ms": 0, "genres": [], "genre_source": "artist|album|override",
  "likely_skipped": false, "enriched_at": "", "enrichment_version": 2
}
```

### `aggregations_monthly` (Charts-Vertrag – Feldnamen sind public API)
```json
{
  "period": "2025-02", "granularity": "month",
  "top_tracks": [{ "track_ref": "", "name": "", "play_count": 0 }],
  "top_artists": [{ "artist_name": "", "play_count": 0 }],
  "top_genres": [{ "genre": "", "play_count": 0 }],
  "plays_by_hour": [], "plays_by_weekday": [],
  "total_plays": 0, "total_ms": 0, "estimated_skips": 0,
  "computed_at": "", "based_on_genre_source": "override_preferred"
}
```

### `pending_upgrades`
```json
{
  "token": "uuid-v4", "status": "pending|approved|rejected",
  "expires_at": "",
  "upgrade": {
    "playlist_id": "", "playlist_name": "", "position": 0,
    "old_track_ref": "", "old_track_name": "", "old_release_type": "single|ep",
    "new_track_ref": "", "new_track_name": "", "new_album_name": ""
  },
  "created_at": "", "decided_at": null
}
```

### Indexes
```
tracks:                   { "artists.spotify_id": 1 }, { "genre.values": 1 }, { "album.release_year": 1 }
playlists:                { "included_in_sync": 1 }, { "tracks.track_ref": 1 }
playback_events_raw:      { "played_at": -1 }, { "enrichment_status": 1 }
playback_events_enriched: { "track_ref": 1, "played_at": -1 }, { "enrichment_version": 1 }
pending_upgrades:         { "token": 1 }, { "status": 1 }, { "expires_at": 1 }
```

## Spotify API

- Rate Limits undokumentiert, ~50 Requests/30s Token Bucket (konservativ)
- Verstecktes 24h-Limit bei aggressiven Bulk-Requests → initialen Sync drosseln
- `recently_played`: max. 50 Tracks, alle 5 Min pollen
- Playlist-Check: `GET /v1/me/playlists` (liefert alle Snapshot-IDs in einem Request)
- Eigene Playlist-Edits: Spotify antwortet mit neuer Snapshot-ID → direkt in DB schreiben
- Scopes: `user-read-private`, `user-read-email`, `playlist-read-private`, `playlist-read-collaborative`, `playlist-modify-public`, `playlist-modify-private`, `user-read-recently-played`, `user-top-read`
- Refresh Tokens verschlüsselt in MongoDB persistieren

## Auth & Zugriffsbeschränkung

- Spotify OAuth 2.0 Authorization Code Flow
- Im OAuth-Callback: Spotify-User-ID gegen `app.allowed-spotify-user-id` (Umgebungsvariable) prüfen
- Bei Abweichung: Session invalidieren, nichts persistieren
- Session-basierte Auth für alle Endpoints inkl. Action-Endpoints
- `return_to`-Parameter in Session für Redirect nach Login

## Action-Endpoints (Approve/Reject)

```
GET /actions/{action}/{token}
    → Session-Auth (kein Login → Redirect /login?return_to=...)
    → Token aus pending_upgrades laden
    → status != "pending" → Dashboard mit Info
    → Aktion ausführen
    → Redirect /dashboard?success={action}_applied
```

## SSE & Live-Updates

CDI Events als Brücke zwischen Backend-Services und SSE-Streams:

```kotlin
@ApplicationScoped
class LiveUpdateService {
    private val outboxProcessor = BroadcastProcessor.create<OutboxChangedEvent>()
    fun onOutboxChanged(@Observes event: OutboxChangedEvent) = outboxProcessor.onNext(event)
    fun outboxStream(): Multi<OutboxChangedEvent> = outboxProcessor.toHotStream()
}
```

SSE-Endpoint liefert beim Connect initialen State, danach push-basiert.

## Scheduler-Jobs

| Job | Intervall | Outbox-Event |
|---|---|---|
| `PlaybackPollJob` | 5 Min | `PollRecentlyPlayed` |
| `PlaylistCheckJob` | 15 Min | `SyncPlaylist` (nur bei Snapshot-Änderung) |
| `AggregationJob` | täglich nachts | `RecomputeAggregations` |

## MongoDB Charts Vertrag

- Charts arbeitet ausschließlich auf `aggregations_*` Collections
- Feldnamen sind stabil (public API) – Breaking Changes erfordern Contract-Test-Anpassung
- `@QuarkusTest` Contract-Tests validieren Schema bei jedem Build
- Additive Changes (neue Felder) sind unkritisch

## Lokale Entwicklung

- `%dev` Profil zeigt auf Atlas Dev-Cluster (separates Atlas-Projekt)
- `@IfBuildProfile("dev") @Startup` DevFixtures für Playback-Events
- Guard: `if (count() == 0L)` – nur beim ersten Start
- Echter Spotify-Login lokal via `http://localhost:8080/oauth/callback` (in Spotify App registrieren)

## Konfiguration (Umgebungsvariablen)

```
APP_ALLOWED_SPOTIFY_USER_ID
SPOTIFY_CLIENT_ID
SPOTIFY_CLIENT_SECRET
MONGODB_CONNECTION_STRING
APP_TOKEN_ENCRYPTION_KEY
SLACK_WEBHOOK_URL
```
