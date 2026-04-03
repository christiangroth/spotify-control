# Frontend UI/UX Code Review

This document captures the remaining open findings from a code review of the frontend layer (`adapter-in-web`) with focus on code quality, naming consistency, clean & readable code, and minimal JS duplication. Findings are grouped by category and ordered by severity.

The following findings have already been fixed: **F02** (CSS classes), **F03** (SVG sprite), **F04** (implicit event), **F05** (banner duplication), **F06** (throttle handler duplication), **F07** (fragment naming), **F12** (Bootstrap modal), **F14** (inline handlers), **F20** (CSS custom properties + active nav).

---

## 1. Naming Consistency

### F08 – `.isEmpty()` and `.empty` used inconsistently in templates
**Files:** Multiple templates

Qute templates mix two syntactically different but functionally equivalent empty checks:
- `{#if rows.isEmpty()}` – calls the Kotlin method
- `{#if stats.recentlyPlayedTracks.empty}` – uses Qute's built-in collection property

Qute's `.empty` property is the idiomatic choice inside templates. All checks should use the same form.

### F09 – `imageLink` vs `imageUrl` for the same concept
**Files:** `catalog.html`, `dashboard.html`, `settings/playback.html`

Artist/album image URLs are surfaced under different property names depending on context:
- `{artist.imageLink}`, `{album.imageLink}` in `catalog.html` and `settings/playback.html`
- `{entry.imageUrl}`, `{track.imageUrl}` in `dashboard.html`

The naming should be unified to either `imageUrl` or `imageLink` consistently across all view models.

### F10 – Mixed `var` / `const` / `let` across JS blocks
**Files:** `catalog.html`, `settings/playlist.html`, `settings/playback.html`

The JS should consistently use `const` for values that are not reassigned and `let` for those that are. `var` should not appear in new code.

### F11 – Page `<title>` tags missing on some pages
**Files:** `dashboard.html`, `catalog.html`

All pages should set a meaningful title via `{#title}`:
- `dashboard.html` and `catalog.html` have no `{#title}` block and fall back to the default `SpCtl`.
- All other pages provide a descriptive title (e.g., `SpCtl – Health`, `SpCtl – Playlists`).

---

## 2. Template Quality

### F13 – `{#scripts}` block placed after `{/content}` in `outbox-viewer.html`
**File:** `outbox-viewer.html`

In all other templates the `{#scripts}` block is placed *before* `{#content}`. The template structure should be consistent: `{#title}`, `{#scripts}`, `{#content}`.

### F15 – Repeated conditional SVG for status icon (active/inactive)
**Files:** `health.html`, `playlist-checks.html`, `settings/playlist.html`, `outbox-viewer.html`

The pattern of rendering a green check or red X icon based on a condition appears in ~10 places across templates. A reusable `status-icon` Qute fragment (parameterized on the boolean condition and icon size) should be extracted to reduce this repetition.

### F16 – `playback-event-viewer.html` uses separate `{#if}` blocks instead of `{#else if}`
**File:** `playback-event-viewer.html`

Each event type uses its own independent `{#if event.type.name() == "…"}` block. These are mutually exclusive; they should be `{#if} … {#else if} … {#else if} … {/if}` to make the exclusivity explicit and avoid unnecessary evaluation.

### F17 – Large inline SVG paths for navigation icons in `layout.html`
**File:** `layout.html`

The Technical dropdown menu contains full inline SVG paths for each menu item's icon. The two Grafana links (Logs, Metrics) duplicate identical long SVG polygon paths. All navigation icons should be declared as `<symbol>` elements in the SVG sprite and referenced with `<use href="#icon-..."/>`.

---

## 3. CSS/Styling

### F18 – `font-variant-numeric: tabular-nums` as repeated inline style
**File:** `settings/playlist.html`

This appears twice as `style="font-variant-numeric: tabular-nums"`. Should be a CSS class (`class="tabular-nums"`) defined in `layout.html`.

### F19 – Hardcoded pixel depths for nested table hierarchy in `catalog.html`
**File:** `catalog.html`

The three-level artist → album → track hierarchy uses progressively darker border colors and background colors as inline styles. These depth values should be named and centralized using CSS custom properties.

---

## 4. UX & Accessibility

### F21 – Missing `aria-label` on several interactive controls
**Files:** `catalog.html`, `settings/playback.html`, `dashboard.html`

Some interactive elements lack accessible labels:
- Catalog filter input: no `aria-label` or `<label>` element
- Artist filter in playback settings: no `<label>` (only a `placeholder`)
- Histogram bar links in `dashboard.html` use `title` attribute but no `aria-label`

### F22 – Empty state text quality inconsistent
**Files:** Various templates

Some empty states are terse without context on when data will appear:
- "No artists found." (catalog.html)
- "No data yet." (dashboard.html – top tracks/artists)
- "No pending tasks." (outbox-viewer.html)

The role guidelines state: *"Empty states are designed – no raw 'No data found' text; include a descriptive message and context."*

### F23 – `deduplicationKey` column has no truncation styling in `outbox-viewer.html`
**File:** `outbox-viewer.html`

The deduplication key is shown with only `title` attribute for the full value. Long keys can overflow the table. `text-overflow: ellipsis` with a `max-width` should be applied, or the column should use Bootstrap's `text-truncate` class with a `max-width`.

---

## Summary Table

| ID | Severity | Category | Short Description |
|----|----------|----------|-------------------|
| F08 | Low | Naming | `.isEmpty()` and `.empty` mixed in templates |
| F09 | Low | Naming | `imageLink` vs `imageUrl` for same concept |
| F10 | Low | Naming | `var` / `const` / `let` mixed inconsistently |
| F11 | Low | Naming | Page titles missing on dashboard and catalog |
| F13 | Low | Template | `{#scripts}` after `{/content}` in outbox-viewer |
| F15 | Low | Template | Repeated conditional SVG for status icons |
| F16 | Low | Template | Separate `{#if}` blocks instead of `{#else if}` |
| F17 | Low | Template | Long SVG paths inline in nav; Grafana icon duplicated |
| F18 | Low | CSS | `font-variant-numeric` repeated as inline style |
| F19 | Low | CSS | Magic depth colors in catalog hierarchy |
| F21 | Low | Accessibility | Missing `aria-label` on some interactive controls |
| F22 | Low | UX | Empty state messages lack context on some pages |
| F23 | Low | UX | `deduplicationKey` column not truncated in outbox viewer |
