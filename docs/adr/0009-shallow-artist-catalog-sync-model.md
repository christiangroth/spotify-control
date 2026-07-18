# Shallow Artist Catalog Sync Model

* Status: accepted
* Deciders: Chris
* Date: 2026-07-12

## Context and Problem Statement

Every artist discovered via playback or playlist sync went through the full catalog sync path
(`SyncArtistDetails` → `SyncArtistAlbums` → `SyncAlbumDetails` per album), permanently storing all
of that artist's albums and tracks. This happened even for artists the user does not actively
follow — featured artists on a single track, artists from playlists someone else curated, or
one-off listens — causing unnecessary Spotify API calls, storage, and noise in statistics for
artists the user never intended to track.

A weaker, existing mechanism (`AppArtist.blockedFromAggregation: Boolean`) only hid an artist from
aggregation — catalog sync and raw playback storage continued unchanged, and toggling the flag
only triggered `rebuildAllAggregations()`, not any cleanup of the over-synced catalog data.

How should the application distinguish artists the user actively tracks from those that only
appear incidentally, without losing playback history for either?

## Decision Drivers

* Full catalog sync (albums + tracks) is wasted effort and storage for artists the user doesn't
  care about.
* Playback history must never be lost, regardless of an artist's tracking status — a later
  status change must not require re-fetching data Spotify's `recently-played` endpoint (short
  window, ~50 entries) can no longer provide.
* Newly discovered artists need a sensible default so the user isn't forced to triage every new
  artist immediately, but the system's guess must be clearly distinguishable from a confirmed
  decision.
* For multi-artist tracks/albums, only one artist should determine sync scope and deletion, to
  avoid deleting data still needed for a co-credited artist the user does track.

## Considered Options

1. **Keep and extend `blockedFromAggregation: Boolean`** — add a second boolean or enum to also
   suppress catalog sync, layering more flags onto the existing binary mechanism.
2. **Four-state `ArtistSyncStatus` enum** (`SYNC`, `SHALLOW`, `SYNC_ASSUMPTION`,
   `SHALLOW_ASSUMPTION`) replacing `blockedFromAggregation` entirely, with the `_ASSUMPTION`
   variants as an explicit, system-assigned "undecided" starting state.

## Decision Outcome

Chosen option: **"Four-state `ArtistSyncStatus` enum"**, because a boolean cannot express "the
system guessed this, the user hasn't confirmed it yet" — a distinction needed to drive both the
settings UI (surfacing undecided artists) and the sync behaviour (assumption states get a
speculative, optimistic full sync so nothing is missed if the user later confirms `SYNC`).

* `SYNC` / `SHALLOW` are final states, only reachable by explicit user action (Catalog UI).
* `SYNC_ASSUMPTION` / `SHALLOW_ASSUMPTION` are assigned automatically on first discovery of an
  artist: `SYNC_ASSUMPTION` when discovered via an actively-synced playlist, `SHALLOW_ASSUMPTION`
  when discovered only via playback/recently-played history. Assumption states behave like their
  final counterpart for sync purposes until confirmed, and can only transition into one of the two
  final states — never back into an assumption state, and never directly between the two
  assumption states.
* Catalog sync branches on status: `SYNC`/`SYNC_ASSUMPTION` enqueue `SyncArtistAlbums`; `SHALLOW`/
  `SHALLOW_ASSUMPTION` never do.
* Playback events are always stored unconditionally regardless of status — filtering by status
  happens only at aggregation time (`PlaybackAggregationService.aggregateDay()`), so a later
  `SHALLOW → SYNC` transition doesn't need to recover any lost history.
* Transitioning to `SHALLOW` deletes the artist's existing albums/tracks (identified by main
  artist only — secondary/featured-artist references are left alone) and triggers
  `rebuildAllAggregations()`.
* In-flight `SyncArtistAlbums`/`SyncAlbumDetails` outbox tasks for an artist that transitions to
  `SHALLOW` are not actively cancelled — the handler re-checks the artist's current status on
  execution and no-ops if it has since become `SHALLOW`.

### Positive Consequences

* No wasted catalog sync (API calls, storage) for artists the user doesn't actively track.
* Assumption states make "the system guessed, not the user" visible and actionable via a
  dedicated settings page, instead of silently applying a default forever.
* Playback history is never at risk of being lost by a sync-status change in either direction.
* The main-artist-only rule keeps the model consistent with the existing
  `CatalogService.buildCatalogSyncRequest()` main-artist convention, avoiding a second concept for
  "which artist owns this album/track."

### Negative Consequences

* Four states are more to reason about than a boolean; the assumption→final transition rules must
  be enforced consistently across the settings UI, the Catalog UI, and every discovery call site.
* `SYNC_ASSUMPTION` artists are speculatively fully synced before the user confirms them — a later
  correction to `SHALLOW` means the sync effort (and, briefly, the storage) was wasted for that
  artist.

## Pros and Cons of the Options

### Keep and extend `blockedFromAggregation: Boolean`

* Good, because no data migration is needed for the field itself.
* Bad, because a boolean cannot represent "system guess, not yet confirmed" without bolting on a
  second flag, which only postpones the same modelling problem.
* Bad, because it does nothing to stop unwanted catalog sync — the existing flag only ever
  affected aggregation, not sync scope.

### Four-state `ArtistSyncStatus` enum

* Good, because it expresses both sync scope and confirmation state in a single, well-defined
  model with clear transition rules.
* Good, because assumption states give newly discovered artists a sensible default without
  forcing immediate user triage.
* Bad, because it requires a one-time migration of every existing `app_artist` document (existing
  artists become `SYNC_ASSUMPTION`, except those previously `blockedFromAggregation=true`, which
  become `SHALLOW_ASSUMPTION`) so the user can review them once under the new model.

## Links

* [arc42.md](../arc42/arc42.md)
