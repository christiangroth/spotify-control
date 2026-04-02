# Frontend UI/UX Code Review

This document captures the remaining open findings from a code review of the frontend layer (`adapter-in-web`) with focus on code quality, naming consistency, clean & readable code, and minimal JS duplication. Findings are grouped by category and ordered by severity.

The following high-severity findings have already been fixed: **F01** (htmx integration), **F02** (CSS classes), **F03** (SVG sprite), **F20** (CSS custom properties).

---

## 1. Bugs

### F04 – `resyncArtist` relies on implicit global `event` object
**File:** `catalog.html`

```javascript
function resyncArtist(btn, artistId) {
    event.stopPropagation();   // 'event' is implicit browser global, not a parameter
    ...
}
```

The inline `onclick` handler does not pass `event`:
```html
onclick="resyncArtist(this, '{artist.artistId}')"
```

Relying on the implicit global `event` object is considered bad practice and may fail in strict mode or future browser environments. The function signature should be `resyncArtist(event, btn, artistId)` and the onclick should pass `event` explicitly, or the function should be refactored to use `addEventListener`.

---

## 2. JS Code Duplication & Helper Functions

### F05 – `showBanner` duplicated as `showRuntimeConfigBanner` in `config.html`
**Files:** `settings-utils.js`, `config.html`

`settings-utils.js` defines `showBanner(message, type)` targeting a fixed element ID `'status-banner'`. The `config.html` page needs a separate banner (ID `'runtime-config-status-banner'`), so it inlines an almost identical function:

```javascript
// config.html – local duplicate
function showRuntimeConfigBanner(message, type) {
    const banner = document.getElementById('runtime-config-status-banner');
    ...
}
```

Both functions are identical except for the hard-coded element ID. The `showBanner` helper should accept the target element ID as a parameter (e.g. `showBanner(elementId, message, type)`) to avoid this duplication. All call sites would need updating, but the number is small.

### F06 – Throttle save and reset in `config.html` are near-duplicates of each other
**File:** `config.html`

The Save and Reset handlers are ~25 lines each and differ only in:
- The URL parameter value (`intervalSeconds` vs `defaultThrottleIntervalSeconds`)
- The success banner message
- Reset also pre-sets `input.value` before the fetch

Both follow the identical pattern: disable btn → fetch POST with JSON body → parse response → show banner → re-enable btn. Extracting a shared function would remove the near-duplicate.

---

## 3. Naming Consistency

### F07 – Fragment ID naming prefix inconsistent across templates (`snippet_` vs `fragment_`)
**Files:** `dashboard.html`, `health.html`, `catalog.html`, `settings/playback.html`

Qute fragment IDs use two different prefixes without clear distinction:
- `dashboard.html` and `health.html`: `snippet_playlist_metadata`, `snippet_playback_data`, `snippet_cronjobs`, etc.
- `catalog.html`: `fragment_artist_list`, `fragment_album_list`, `fragment_track_list`
- `settings/playback.html`: `fragment_artist_items`

The convention for fragment IDs should be unified to one prefix (e.g., always `snippet_`) and container IDs should mirror the fragment name.

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

## 4. Template Quality

### F12 – Custom modal implementation instead of Bootstrap modal
**File:** `catalog.html`

The wipe-catalog confirmation modal is built from scratch with a `position:fixed` overlay div, manual `style.display = 'flex'` / `'none'` toggling, and custom HTML/CSS. Bootstrap 5 ships a fully featured `.modal` component that handles focus management, keyboard dismissal (`Escape`), scroll lock, and animations out of the box. The custom implementation should be replaced with Bootstrap's modal.

### F13 – `{#scripts}` block placed after `{/content}` in `outbox-viewer.html`
**File:** `outbox-viewer.html`

In all other templates the `{#scripts}` block is placed *before* `{#content}`. The template structure should be consistent: `{#title}`, `{#scripts}`, `{#content}`.

### F14 – Inline event handlers (`oninput`, `onclick`) mixed with `addEventListener`
**Files:** `catalog.html`, `dashboard.html`

Most interactions use `addEventListener` in `<script>` blocks (good), but some use inline handlers:
- `dashboard.html`: `onmouseover="this.style.opacity='0.7'"` and `onmouseout="this.style.opacity='1'"` on histogram bars
- `catalog.html`: `onclick="toggleArtistAlbums(…)"` and `onclick="toggleAlbumTracks(…)"` on table rows

These should be consistently migrated to `addEventListener` bindings.

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

## 5. CSS/Styling

### F18 – `font-variant-numeric: tabular-nums` as repeated inline style
**File:** `settings/playlist.html`

This appears twice as `style="font-variant-numeric: tabular-nums"`. Should be a CSS class (`class="tabular-nums"`) defined in `layout.html`.

### F19 – Hardcoded pixel depths for nested table hierarchy in `catalog.html`
**File:** `catalog.html`

The three-level artist → album → track hierarchy uses progressively darker border colors and background colors as inline styles. These depth values should be named and centralized using CSS custom properties.

---

## 6. UX & Accessibility

### F20 – No active navigation item highlighting
**File:** `layout.html`

The role guidelines state: *"Navigation state is reflected visually (active nav item highlighted)."* The navbar currently renders all nav items identically regardless of which page is active.

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
| F04 | Medium | Bug | `resyncArtist` uses implicit global `event` object |
| F05 | Medium | Duplication | `showBanner` duplicated as `showRuntimeConfigBanner` |
| F06 | Medium | Duplication | Throttle save/reset are near-duplicate handlers |
| F07 | Medium | Naming | Fragment ID prefix inconsistent (`snippet_` vs `fragment_`) |
| F08 | Low | Naming | `.isEmpty()` and `.empty` mixed in templates |
| F09 | Low | Naming | `imageLink` vs `imageUrl` for same concept |
| F10 | Low | Naming | `var` / `const` / `let` mixed inconsistently |
| F11 | Low | Naming | Page titles missing on dashboard and catalog |
| F12 | Medium | Template | Custom modal instead of Bootstrap modal component |
| F13 | Low | Template | `{#scripts}` after `{/content}` in outbox-viewer |
| F14 | Medium | Template | Inline event handlers mixed with `addEventListener` |
| F15 | Low | Template | Repeated conditional SVG for status icons |
| F16 | Low | Template | Separate `{#if}` blocks instead of `{#else if}` |
| F17 | Low | Template | Long SVG paths inline in nav; Grafana icon duplicated |
| F18 | Low | CSS | `font-variant-numeric` repeated as inline style |
| F19 | Low | CSS | Magic depth colors in catalog hierarchy |
| F20 | Medium | UX | No active nav item highlighting |
| F21 | Low | Accessibility | Missing `aria-label` on some interactive controls |
| F22 | Low | UX | Empty state messages lack context on some pages |
| F23 | Low | UX | `deduplicationKey` column not truncated in outbox viewer |
