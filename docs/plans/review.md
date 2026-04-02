# Frontend UI/UX Code Review

This document captures all findings from a code review of the frontend layer (`adapter-in-web`) with focus on code quality, naming consistency, clean & readable code, and minimal JS duplication. Findings are grouped by category and ordered by severity.

---

## 1. Architecture & Stack Deviations

### F01 – htmx not used; manual `fetch()` is used everywhere
**Files:** All template `<script>` blocks, `settings-utils.js`

The `role-frontend-developer.md` explicitly states: *"Interactions and form submissions via htmx, no manual `fetch()` or `XMLHttpRequest`"*, and lists htmx (via WebJar) as part of the technology stack. However:

- htmx is not declared as a Gradle dependency in `adapter-in-web/build.gradle.kts`.
- htmx is not loaded in `layout.html`.
- All form actions, status changes, and content updates use manual `fetch()` calls scattered across templates.

This means every interactivity pattern (button click → POST, toggle → PUT, filter → GET) is reimplemented from scratch in each template. Migrating to htmx would reduce JS volume significantly and enforce consistent patterns for loading states, error handling, and partial HTML updates. Until that migration is done, the existing `fetch()` helpers should at least be kept clean and non-duplicated (see F04, F05, F06).

### F02 – Inline CSS throughout all templates; no CSS classes
**Files:** All template files

The role states: *"All WebJar includes belong exclusively in `layout.html` – no inline CSS in templates."* In practice, every template is dense with inline `style` attributes:

- `style="background-color:#1e1e1e;border-color:#333;"` appears on cards across every page.
- `style="color:#888;font-size:.8rem;"` appears on dozens of elements.
- Table cells repeat the same 4–5 `style` attributes row by row.

These should be extracted into named CSS classes in the `<style>` block in `layout.html`. Currently only a small set of global classes exist (`.btn-spotify`, `.app-navbar`, `.docs-content`), but the vast majority of styling is done inline. This makes the templates verbose and any design change requires touching many lines in many files.

---

## 2. Bugs

### F03 – `#icon-user-placeholder` referenced but never defined
**Files:** `dashboard.html`, `catalog.html`, `settings/playback.html`

The SVG `<defs>` in `layout.html` defines four icons: `icon-check-circle`, `icon-x-circle`, `icon-outbox`, `icon-music`. However, all artist and user image placeholders reference `#icon-user-placeholder`, which does not exist:

```html
<!-- catalog.html line 78 -->
<use href="#icon-user-placeholder" x="12" y="10" width="16" height="16" fill="#888"/>

<!-- settings/playback.html line 41 -->
<svg width="18" height="18" fill="#888"><use href="#icon-user-placeholder"/></svg>
```

Since the symbol is undefined, these SVG `<use>` elements render nothing. The artist image placeholder shows only the `<rect>` background without the user silhouette icon. A `icon-user-placeholder` symbol must be added to the sprite in `layout.html`, or a different existing icon should be referenced.

### F04 – `resyncArtist` relies on implicit global `event` object
**File:** `catalog.html`, line 232

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

## 3. JS Code Duplication & Helper Functions

### F05 – `showBanner` duplicated as `showRuntimeConfigBanner` in `config.html`
**Files:** `settings-utils.js`, `config.html`

`settings-utils.js` defines `showBanner(message, type)` targeting a fixed element ID `'status-banner'`. The `config.html` page needs a separate banner (ID `'runtime-config-status-banner'`), so it inlines an almost identical function:

```javascript
// config.html – local duplicate
function showRuntimeConfigBanner(message, type) {
    const banner = document.getElementById('runtime-config-status-banner');
    banner.textContent = message;
    banner.className = 'alert alert-' + type + ' mb-3';
    banner.classList.remove('d-none');
    clearTimeout(banner._fadeTimer);
    banner._fadeTimer = setTimeout(function() { banner.classList.add('d-none'); }, 5000);
}
```

Both functions are identical except for the hard-coded element ID. The `showBanner` helper should accept the target element ID as a parameter (e.g. `showBanner(elementId, message, type)`) to avoid this duplication. All call sites would need updating, but the number is small.

### F06 – `fetch` + response-parse + banner pattern duplicated in `settings/playlist.html`
**File:** `settings/playlist.html`, lines 91–116

The sync-toggle handler manually implements the full fetch → parse JSON → show banner flow (~25 lines), which largely mirrors what `postWithButton` in `settings-utils.js` does. The reason it doesn't use `postWithButton` is that: (a) the method is PUT, not POST, and (b) it needs to update the DOM from the response. A more general helper (e.g., `fetchJsonWithButton(btn, url, method, body, onSuccess)`) in `settings-utils.js` would cover this case. The `postWithButton` function itself is just a special case.

### F07 – Artist status change in `settings/playback.html` also duplicates fetch pattern
**File:** `settings/playback.html`, lines 208–237

The artist status click listener has another bespoke implementation of the same fetch → parse JSON → check ok → show banner → disable/enable button → handle catch flow (~30 lines). Same root cause as F06: `postWithButton` only handles POST with no body and no DOM update from response. A generalized helper would remove this duplication.

### F08 – Throttle save and throttle reset in `config.html` are near-duplicates of each other
**File:** `config.html`, lines 107–162

The Save and Reset handlers are ~25 lines each and differ only in:
- The URL parameter value (`intervalSeconds` vs `defaultThrottleIntervalSeconds`)
- The success banner message
- Reset also pre-sets `input.value` before the fetch

Both follow the identical pattern: disable btn → fetch POST with JSON body → parse response → show banner → re-enable btn. Extracting a shared function (or using the generalized `fetchJsonWithButton` from F06) would remove the near-duplicate.

---

## 4. Naming Consistency

### F09 – Fragment ID naming prefix inconsistent across templates (`snippet_` vs `fragment_`)
**Files:** `dashboard.html`, `health.html`, `catalog.html`, `settings/playback.html`

Qute fragment IDs use two different prefixes without clear distinction:
- `dashboard.html` and `health.html`: `snippet_playlist_metadata`, `snippet_playback_data`, `snippet_cronjobs`, etc.
- `catalog.html`: `fragment_artist_list`, `fragment_album_list`, `fragment_track_list`
- `settings/playback.html`: `fragment_artist_items`

The corresponding container element IDs (used by JS) consistently use hyphens, but some use `snippet-` and others `artist-list-container` etc. The convention for fragment IDs should be unified to one prefix (e.g., always `snippet_`) and container IDs should mirror the fragment name (e.g., `snippet-artist-list` for fragment `snippet_artist_list`).

### F10 – `.isEmpty()` and `.empty` used inconsistently in templates
**Files:** Multiple templates

Qute templates mix two syntactically different but functionally equivalent empty checks:
- `{#if rows.isEmpty()}` – calls the Kotlin method (playlist.html, playlist-checks.html, playback.html)
- `{#if stats.recentlyPlayedTracks.empty}` – uses Qute's built-in collection property (dashboard.html)
- `{#if artists.empty}` – Qute property (catalog.html)

Qute's `.empty` property is the idiomatic choice inside templates (it doesn't require parentheses and reads naturally). All checks should use the same form. Pick one – `.empty` is preferred.

### F11 – `imageLink` vs `imageUrl` for the same concept
**Files:** `catalog.html`, `dashboard.html`, `settings/playback.html`

Artist/album image URLs are surfaced under different property names depending on context:
- `{artist.imageLink}`, `{album.imageLink}` in `catalog.html` and `settings/playback.html`
- `{entry.imageUrl}`, `{track.imageUrl}` in `dashboard.html`

Both represent the same concept (a URL pointing to a cover image). The naming should be unified to either `imageUrl` or `imageLink` consistently across all view models.

### F12 – Mixed `var` / `const` / `let` across JS blocks
**Files:** `catalog.html`, `settings/playlist.html`, `settings/playback.html`

- `catalog.html`: uses `var` for all local variables (e.g., `var albumsRow`, `var container`, `var filterTimeout`)
- `settings/playlist.html`: mixes `const` (e.g., `const playlistId`, `const isActive`) with `var` in forEach callbacks
- `settings/playback.html`: consistent `var` in some places, `function`-scoped IIFEs, but `var` in modern-looking code

The JS should consistently use `const` for values that are not reassigned and `let` for those that are. `var` should not appear in new code.

### F13 – Page `<title>` tags missing on some pages
**Files:** `dashboard.html`, `catalog.html`

All pages should set a meaningful title via `{#title}`:
- `dashboard.html` and `catalog.html` have no `{#title}` block and fall back to the default `SpCtl`.
- All other pages provide a descriptive title (e.g., `SpCtl – Health`, `SpCtl – Playlists`).

Consistent titling improves browser tab recognition and accessibility.

---

## 5. HTML/Template Quality

### F14 – Custom modal implementation instead of Bootstrap modal
**File:** `catalog.html`, lines 24–36

The wipe-catalog confirmation modal is built from scratch with a `position:fixed` overlay div, manual `style.display = 'flex'` / `'none'` toggling, and custom HTML/CSS. Bootstrap 5 ships a fully featured `.modal` component that handles focus management, keyboard dismissal (`Escape`), scroll lock, and animations out of the box. The custom implementation should be replaced with Bootstrap's modal.

### F15 – `{#scripts}` block placed after `{/content}` in `outbox-viewer.html`
**File:** `outbox-viewer.html`

```html
    {/content}
    {#scripts}          ← scripts inserted after content
    <script>...</script>
    {/scripts}
{/include}
```

In all other templates the `{#scripts}` block is placed *before* `{#content}`. In `layout.html` the scripts are injected between the `<script>` tags and the content area. Placing `{#scripts}` after `{/content}` works because Qute collects inserts before rendering the layout, but it is visually confusing and inconsistent. All templates should follow the same order: `{#title}`, `{#scripts}`, `{#content}`.

### F16 – Inline event handlers (`onmouseover`, `onmouseout`, `oninput`, `onclick`) mixed with `addEventListener`
**Files:** `dashboard.html`, `catalog.html`, `mongodb-viewer.html`

Most interactions use `addEventListener` in `<script>` blocks (good), but some use inline handlers:
- `dashboard.html`: `onmouseover="this.style.opacity='0.7'"` and `onmouseout="this.style.opacity='1'"` on histogram bars
- `catalog.html`: `oninput="filterArtists(this.value)"` on filter input, `onclick="toggleArtistAlbums(…)"` and `onclick="toggleAlbumTracks(…)"` on table rows
- `mongodb-viewer.html`: `onchange="changeCollection(this.value)"` and `onchange="changePageSize(this.value)"`

These should be consistently migrated to `addEventListener` bindings in the `<script>` block. Inline handlers make it harder to maintain, prevent CSP `unsafe-inline` removal in the future, and are harder to test.

### F17 – Repeated conditional SVG for status icon (active/inactive/warning)
**Files:** `health.html`, `playlist-checks.html`, `settings/playlist.html`, `outbox-viewer.html`

The pattern of rendering a green check or red X icon based on a condition appears in ~10 places across templates:
```html
{#if condition}
<svg ... fill="#1db954"><use href="#icon-check-circle"/></svg>
{#else}
<svg ... fill="#dc3545"><use href="#icon-x-circle"/></svg>
{/if}
```
Qute supports user-defined template fragments. A reusable `status-icon` fragment (parameterized on the boolean condition and icon size) should be extracted to reduce this repetition. Until Qute fragment includes with parameters are available, at minimum the SVG markup should be as terse as possible (avoiding `xmlns` on every inner SVG use since the document is already HTML).

### F18 – `playback-event-viewer.html` uses separate `{#if}` blocks instead of `{#else if}`
**File:** `playback-event-viewer.html`, lines 25–65

Each event type uses its own independent `{#if event.type.name() == "…"}` block:
```
{#if event.type.name() == "RECENTLY_PLAYED"}...{/if}
{#if event.type.name() == "RECENTLY_PARTIAL_PLAYED"}...{/if}
{#if event.type.name() == "CURRENTLY_PLAYING"}...{/if}
```
These are mutually exclusive; three independent `{#if}` evaluations are performed. They should be `{#if} … {#else if} … {#else if} … {/if}` to make the exclusivity explicit and avoid unnecessary evaluation.

### F19 – Large inline SVG paths for navigation icons in `layout.html`
**File:** `layout.html`, lines 131–142

The Technical dropdown menu contains full inline SVG paths for each menu item's icon (Health, Outbox, Config, Logs, Metrics, Viewer, Atlas, Docs, GitHub, Spotify API). The two Grafana links (Logs, Metrics) duplicate identical long SVG polygon paths. All navigation icons should be declared as `<symbol>` elements in the SVG sprite (already used for the four functional icons) and referenced with `<use href="#icon-..."/>`, eliminating the duplication and making the layout readable.

---

## 6. CSS/Styling

### F20 – Magic color values repeated as inline styles; CSS variables not used
**Files:** All templates

Only `--spotify-green: #1db954` is defined as a CSS variable. The following colors appear repeatedly as raw hex values in inline `style` attributes across many templates without being named:

| Color | Usage |
|-------|-------|
| `#1e1e1e` | Card background |
| `#121212` | Page background (body) |
| `#333333` / `#333` | Card borders, table borders |
| `#888888` / `#888` | Muted/secondary text |
| `#dc3545` | Danger/error state |
| `#e0e0e0` | Primary text |
| `#161616` | Deep nested background |
| `#2a2a2a` | Nested level-2 background |
| `#1a1a1a` | Nested level-3 background |

These should be defined as CSS custom properties in the `:root` block in `layout.html` and referenced via `var(--color-name)`, or better replaced with named utility classes.

### F21 – `font-variant-numeric: tabular-nums` as repeated inline style
**File:** `settings/playlist.html`

This appears twice as `style="font-variant-numeric: tabular-nums"`. Should be a CSS class (`class="tabular-nums"`) defined in `layout.html`.

### F22 – Hardcoded pixel depths for nested table hierarchy in `catalog.html`
**File:** `catalog.html`

The three-level artist → album → track hierarchy uses progressively darker border colors (`#333`, `#2a2a2a`, `#1a1a1a`) and background colors (`#161616`, `#111`) as inline styles throughout the table rows. These magic depth values should be named and centralized.

---

## 7. UX & Accessibility

### F23 – No active navigation item highlighting
**File:** `layout.html`

The role guidelines state: *"Navigation state is reflected visually (active nav item highlighted)."* The navbar currently renders all nav items identically regardless of which page is active. A CSS class on the active route item (e.g. `active` on the corresponding link) should be applied. This requires either server-side injection of the current path into the template global context, or a JS-based `window.location.pathname` comparison on page load.

### F24 – Missing `aria-label` on several interactive controls
**Files:** `catalog.html`, `settings/playback.html`, `dashboard.html`

Some interactive elements lack accessible labels:
- Catalog filter input: no `aria-label` or `<label>` element
- Artist filter in playback settings: no `<label>` (only a `placeholder`)
- Histogram bar links in `dashboard.html` use `title` attribute (accessible to hover) but no `aria-label`

### F25 – Empty state text quality inconsistent
**Files:** Various templates

Some empty states are descriptive (good):
> "No playlists found. Playlist data is synced automatically every hour."

Others are terse (needs improvement):
> "No artists found." (catalog.html)
> "No data yet." (dashboard.html – top tracks/artists)
> "No pending tasks." (outbox-viewer.html)
> "No recently played tracks available yet." (dashboard.html – acceptable)

The role guidelines state: *"Empty states are designed – no raw 'No data found' text; include a descriptive message and context."* Short empty states should include context about when data will appear or how to populate it.

### F26 – `deduplicationKey` column has no truncation styling in `outbox-viewer.html`
**File:** `outbox-viewer.html`, line 52

The deduplication key is shown with only `title` attribute for the full value. On normal-width screens, long keys can overflow the table. `text-overflow: ellipsis` with a `max-width` and `overflow: hidden` should be applied to prevent layout breakage, or the column should use `text-truncate` Bootstrap class with a `max-width`.

---

## Summary Table

| ID | Severity | Category | Short Description |
|----|----------|----------|-------------------|
| F01 | High | Architecture | htmx not used; manual `fetch()` everywhere |
| F02 | High | CSS/Architecture | Inline CSS in all templates; no CSS classes |
| F03 | High | Bug | `#icon-user-placeholder` undefined in SVG sprite |
| F04 | Medium | Bug | `resyncArtist` uses implicit global `event` object |
| F05 | Medium | Duplication | `showBanner` duplicated as `showRuntimeConfigBanner` |
| F06 | Medium | Duplication | fetch+parse+banner duplicated in playlist sync-toggle |
| F07 | Medium | Duplication | fetch+parse+banner duplicated in artist status change |
| F08 | Medium | Duplication | Throttle save/reset are near-duplicate handlers |
| F09 | Medium | Naming | Fragment ID prefix inconsistent (`snippet_` vs `fragment_`) |
| F10 | Low | Naming | `.isEmpty()` and `.empty` mixed in templates |
| F11 | Low | Naming | `imageLink` vs `imageUrl` for same concept |
| F12 | Low | Naming | `var` / `const` / `let` mixed inconsistently |
| F13 | Low | Naming | Page titles missing on dashboard and catalog |
| F14 | Medium | Template | Custom modal instead of Bootstrap modal component |
| F15 | Low | Template | `{#scripts}` after `{/content}` in outbox-viewer |
| F16 | Medium | Template | Inline event handlers mixed with `addEventListener` |
| F17 | Low | Template | Repeated conditional SVG for status icons |
| F18 | Low | Template | Separate `{#if}` blocks instead of `{#else if}` |
| F19 | Low | Template | Long SVG paths inline in nav; Grafana icon duplicated |
| F20 | High | CSS | Magic color values; only `--spotify-green` is a variable |
| F21 | Low | CSS | `font-variant-numeric` repeated as inline style |
| F22 | Low | CSS | Magic depth colors in catalog hierarchy |
| F23 | Medium | UX | No active nav item highlighting |
| F24 | Low | Accessibility | Missing `aria-label` on some interactive controls |
| F25 | Low | UX | Empty state messages lack context on some pages |
| F26 | Low | UX | `deduplicationKey` column not truncated in outbox viewer |
