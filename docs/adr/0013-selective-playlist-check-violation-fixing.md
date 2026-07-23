# Selective Playlist Check Violation Fixing

* Status: accepted
* Deciders: Chris
* Date: 2026-07-23

## Context and Problem Statement

Fixing a playlist check ([ADR-0011](0011-playlist-checks-framework.md)) was all-or-nothing: clicking
"Fix" recomputed and corrected every current violation of a check for a playlist in one go. Users
sometimes want to keep one violation untouched (e.g. a duplicate that is actually intentional) while
still fixing the rest. How can a user select a subset of violations to fix without changing the
fundamentally stateless, recompute-on-fix design of the existing runners?

## Decision Drivers

* Violations previously had no stable identity — a `List<String>` of display messages — so there was
  nothing to select against.
* Runners recompute violations fresh in both `run()` and `fix()` rather than persisting fix intent;
  selection must fit into that same recompute-and-filter shape.
* The outbox event carrying a fix must remain uniquely deduplicable per distinct partial fix, not
  collapse different selections of the same playlist/check into one deduplication key.

## Decision Outcome

Chosen option: give every violation a stable `id` derived from data already available in each runner
(track ID, or `trackId@position` where the same track can appear more than once), and thread a
`selectedViolationIds: Set<String>` through `fix()` so each runner filters its freshly recomputed
violations down to the selection before acting.

* `AppPlaylistCheck.violations` changes from `List<String>` to `List<PlaylistCheckViolation>`
  (`id`, `message`).
* `PlaylistCheckRunner.fix()` gains a `selectedViolationIds: Set<String>` parameter; each of the
  three fixable runners (`DuplicateTrackIdsCheckRunner`, `YearSongsInAllCheckRunner`,
  `TrackFromLatestReleaseCheckRunner`) filters its recomputed violations against the selection before
  applying changes. `SingleArtistTrackCheckRunner` has no fix and is unaffected.
* `DomainOutboxEvent.FixPlaylistCheck` carries the selected `violationIds`; the deduplication key
  includes the sorted IDs so distinct partial fixes for the same playlist/check queue independently.
* The playlist checks page shows only a violation count per row; clicking it opens a modal listing
  every violation with a toggle (defaulting to all selected) plus "select all"/"select none", styled
  after the existing sync-toggle icons rather than plain checkboxes.

### Positive Consequences

* Users can exclude specific violations (e.g. an intentional duplicate) from a fix run.
* Violation IDs are derived from data the runners already compute, no extra lookups needed.
* The playlist checks table is far less cluttered for playlists with many violations.

### Negative Consequences

* `AppPlaylistCheckDocument` in MongoDB changes shape (`violations` from `List<String>` to a list of
  `{id, message}` documents); rolled out via a `WipePlaylistChecksStarter` version bump since checks
  re-populate automatically on the next playlist sync.
* Every fixable runner's `fix()` signature grows by one parameter that must be threaded through
  service and outbox event layers.

## Links

* Refines [ADR-0011](0011-playlist-checks-framework.md)
* [arc42.md](../arc42/arc42.md)
