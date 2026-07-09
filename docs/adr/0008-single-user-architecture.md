# Single-User Architecture

* Status: accepted
* Deciders: Chris
* Date: 2026-07-09

## Context and Problem Statement

spotify-control was originally designed to support a small, allow-listed set of Spotify users
(`APP_ALLOWED_SPOTIFY_USER_IDS`), even though in practice it has only ever run with a single
operator/user. This assumption of "possibly a few users" threads a `UserId` through nearly every
port, service, MongoDB collection, and outbox event in the system, without ever being exercised
beyond one active account.

The [2026-07-08 performance review](../reviews/2026-07-08-performance-review.md) surfaced two
concrete problems that trace back to this mismatch between the multi-user design and the
single-user reality:

* **Finding 1** – catalog sync picks an arbitrary user's Spotify token
  (`userRepository.findAll().firstOrNull()` in `CatalogService`) to sync *shared* catalog data.
  This already behaves like a single-token design, just without admitting it — a structural
  single point of failure "that likely came from building for the initial single/dual-user case
  and not revisiting it as multi-user support was added."
* **Finding 2** – the `to-spotify-playback` outbox partition has no per-user sub-partitioning,
  which the review flags as a potential serialization bottleneck "if user count grows."

Both findings, along with two more scalability risks noted in the review's "what breaks first"
section, are only risks *because* the system is modeled as multi-user. None of them have ever
materialized, because there has only ever been one user.

Should the application keep the allow-list/multi-user model and harden it against these risks, or
should it be redesigned as a strict single-user application?

## Decision Drivers

* The original "a few users" assumption was wrong and has never reflected real usage.
* Multi-user plumbing (`UserId` in ports, services, indexes, outbox events) adds complexity with
  no corresponding benefit.
* [ADR-0003](0003-no-separate-frontend-project.md) already leans on "single-user developer tool"
  as a design driver elsewhere in the system — this decision makes that assumption explicit and
  consistent everywhere.
* Simpler code is easier to maintain by a single developer.
* The performance review's multi-user scaling risks are cheaper to eliminate by removing the
  premise than to fix by hardening the code.

## Considered Options

1. **Keep the allow-list / multi-user model, harden the scaling risks** (fix the catalog-sync
   token shortcut, add per-user outbox sub-partitioning, add per-user locking where needed).
2. **Convert to a strict single-user application** – remove the allow-list and `UserId` plumbing
   wherever it exists only to distinguish between users, keep a single implicit user throughout.

## Decision Outcome

Chosen option: **"Convert to a strict single-user application"**, because the multi-user
capability was never used, never requested, and was based on a wrong initial assumption. Removing
it is simpler than hardening code paths for a scenario that doesn't occur, and it directly
resolves the performance review's multi-user scaling findings by eliminating their precondition
rather than defending against it.

This was a large, cross-cutting change, implemented incrementally across several follow-up PRs
rather than as a single change. The migration is complete: the allow-list, per-user scheduler
fan-out, `UserId` threading through ports/services/outbox events, and `spotifyUserId`-keyed
MongoDB schema have all been removed.

### Positive Consequences

* Removes the allow-list and OAuth "not allowed" error path entirely — login becomes "log in with
  Spotify, done."
* Eliminates the arbitrary-user catalog-sync shortcut (performance review Finding 1) by
  construction, instead of hardening it.
* Removes the multi-user outbox scaling risk (performance review Finding 2) and the "currently
  playing scales with user count" risk, since there is exactly one user.
* Removes `UserId` from ports, services, and outbox events where it only ever distinguished
  between users that never coexisted, simplifying method signatures across `domain-api` and
  `domain-impl`.
* MongoDB documents and indexes keyed by `spotifyUserId` were simplified once there was no more
  per-user partitioning to maintain.

### Negative Consequences

* Re-introducing multi-user support later would require re-adding this plumbing rather than just
  enabling a flag.
* Existing MongoDB data and indexes needed a migration path once `userId` was dropped from
  document keys; this was handled with a one-time startup migration bean rather than a manual
  operator step.
* Touches nearly every module in the system, so the migration must be done incrementally with a
  green build at every step, per this repository's [CI requirements](../../CLAUDE.md).

## Pros and Cons of the Options

### Keep the allow-list / multi-user model, harden the scaling risks

* Good, because it preserves the theoretical ability to add a second user later.
* Bad, because it keeps solving a problem ("what if there are multiple users") that has never
  actually occurred.
* Bad, because hardening (per-user token selection, per-user outbox sub-partitioning, per-user
  locking) adds *more* complexity on top of an already-unused abstraction.
* Bad, because it does nothing to simplify the existing `UserId` plumbing that has no current
  benefit.

### Convert to a strict single-user application

* Good, because it removes complexity instead of adding to it.
* Good, because it resolves the performance review's multi-user findings by eliminating their
  cause.
* Good, because it aligns the code with how the application has always actually been used.
* Bad, because it is a large, cross-cutting refactor touching most modules.
* Bad, because re-adding multi-user support later would require redesigning this plumbing again.

## Links

* [Performance review, 2026-07-08](../reviews/2026-07-08-performance-review.md)
* [No Separate Frontend Project ADR](0003-no-separate-frontend-project.md)
* [arc42.md](../arc42/arc42.md)
