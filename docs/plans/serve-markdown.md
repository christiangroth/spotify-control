# Concept: Serving Release Notes and Documentation

## Goals

Serve the Markdown content from `docs/arc42`, `docs/adr`, and `docs/releasenotes` inside the
SpCtl web application, rendered in a GitHub-like style, for the logged-in user only – without a
separate build step, without npm, and without Node.js.

Specific requirements:

1. A **"Docs"** entry in the menu bar for logged-in users that opens an arc42 documentation page.
2. Individual **ADR pages** accessible from the docs area (logged-in users only).
3. **Release notes** accessible by clicking the application version in the menu bar, and also
   reachable via a dedicated `/docs/releasenotes` page (logged-in users only).
4. Markdown rendered in a GitHub-like style using a lightweight WebJar dependency.
5. No extra build toolchain – consistent with the existing no-npm, no-Node.js constraint.

---

## Markdown Rendering Library

### Decision: `marked`

The chosen library is **marked** (`org.webjars.npm:marked`).

| Criterion          | Assessment |
|--------------------|------------|
| Available as WebJar | Yes – `org.webjars.npm:marked` (Maven Central) |
| Bundle size        | ~22 KB minified |
| API simplicity     | `marked.parse(markdownString)` returns an HTML string |
| GitHub-like output | Yes – GitHub-Flavored Markdown (GFM) support built in |
| Zero build step    | Yes – single `<script>` include, vanilla JS |
| Actively maintained | Yes |

`marked` is integrated the same way as Bootstrap, htmx, and Font Awesome: declared in the Gradle
version catalog, added as a dependency in `adapter-in-web/build.gradle.kts`, and included via a
`<script>` tag in `layout.html`.

See [ADR-0005](../adr/0005-markdown-rendering-library.md) for the full decision record.

---

## File Bundling Strategy

The Markdown files live in the `docs/` source tree. They must be available at runtime inside the
Docker container. The solution must not duplicate the files manually.

**Approach: Gradle copy task**

A `Sync` task in `adapter-in-web/build.gradle.kts` copies the relevant Markdown files into
`adapter-in-web/src/main/resources/docs/` before compilation. This directory is listed in
`.gitignore` (it is a generated artifact, not a source file). The originals remain in `docs/` as
the single source of truth.

Files to be copied:

| Source                                   | Target classpath path                              |
|------------------------------------------|----------------------------------------------------|
| `docs/arc42/arc42-EN.md`                 | `docs/arc42/arc42-EN.md`                           |
| `docs/adr/*.md` (excluding template)     | `docs/adr/<filename>.md`                           |
| `docs/releasenotes/RELEASENOTES.md`      | `docs/releasenotes/RELEASENOTES.md`                |

The Quarkus resource loader (`ClassPathResourceLoader` or plain `getResourceAsStream`) is used to
read the files at request time.

---

## Module Placement

All new code lives in `adapter-in-web` – consistent with the hexagonal structure where
`adapter-in-web` owns all inbound HTTP concerns. No new module is required.

---

## Routing and Pages

All new routes require an authenticated session (logged-in user). Routes follow the existing
security pattern (annotated with `@RolesAllowed("user")` or the equivalent Quarkus security
annotation used for the rest of the application).

| Route                      | Description |
|----------------------------|-------------|
| `GET /docs`                | Redirects to `/docs/arc42` |
| `GET /docs/arc42`          | Renders `arc42-EN.md` |
| `GET /docs/adr`            | Index page listing all ADRs with links |
| `GET /docs/adr/{filename}` | Renders a single ADR Markdown file |
| `GET /docs/releasenotes`   | Renders `RELEASENOTES.md` |

A single `DocsResource.kt` in `adapter-in-web` handles all routes. The resource reads the
requested Markdown file from the classpath and passes the raw string to a Qute template.

---

## Template Structure

### `docs.html` (reusable single-file renderer)

One shared template is used for all Markdown pages:

```html
{#include layout.html}
    {#title}SpCtl – {title}{/title}
    {#content}
    <div class="container py-4">
        <h5 class="text-muted mb-3">{title}</h5>
        <div id="docs-rendered"></div>
        <textarea id="docs-raw" class="d-none">{markdownContent}</textarea>
    </div>
    <script>
        document.getElementById('docs-rendered').innerHTML =
            marked.parse(document.getElementById('docs-raw').value);
    </script>
    {/content}
{/include}
```

The raw Markdown is embedded in a hidden `<textarea>` to avoid HTML-escaping and JSON-escaping
issues. JavaScript reads it with `.value` and passes it to `marked.parse()`. No AJAX call is
needed.

### `docs-adr-index.html` (ADR index)

A dedicated template lists all available ADRs with their titles and links, rendered server-side
by Qute. No JavaScript is required for this page.

---

## Navigation Changes

### Menu bar – "Docs" entry

A new **Docs** link is added to the `<nav>` in `layout.html`, visible only for logged-in users.
It points to `/docs` (which redirects to `/docs/arc42`).

The menu bar uses Bootstrap's navbar component and the existing dark styling. The Docs link
follows the same styling as any future nav links.

### Version as release-notes link

The application version displayed in the top-right of the navbar (`0.1.0-SNAPSHOT`) is turned
into an anchor tag pointing to `/docs/releasenotes`. For logged-in users the link is active; for
unauthenticated users the version remains plain text (no release notes page is accessible without
login anyway).

```html
<!-- logged in -->
<a href="/docs/releasenotes" class="app-version text-decoration-none">0.1.0-SNAPSHOT</a>

<!-- not logged in -->
<span class="app-version">0.1.0-SNAPSHOT</span>
```

The Qute layout template already has access to a `loggedIn` boolean (or equivalent) that can be
used to conditionally render the two variants.

---

## Security

All `/docs/**` routes must be protected. The Quarkus Security extension already guards the
application. Applying `@RolesAllowed` (or a `@Authenticated` equivalent) to `DocsResource` is
sufficient. The existing OAuth session mechanism handles authentication transparently – unauthenticated
requests are redirected to the login page.

---

## Styling

- The rendered HTML from `marked` is wrapped in a `<div class="docs-content">` container.
- A small CSS block in `layout.html` (or a dedicated `<style>` block in `docs.html`) applies
  GitHub-like Markdown prose styling: readable line width, code block background, heading separators.
- Bootstrap utility classes provide the base spacing and typography.
- No additional CSS library is required.

---

## `layout.html` Changes Summary

| Change | Detail |
|--------|--------|
| Add `<script src="/webjars/marked/marked.min.js">` | Before `</body>` in `layout.html`, alongside the existing Bootstrap bundle script |
| Add "Docs" nav link (authenticated only) | In the `<nav>` element |
| Make version a conditional link | Version span becomes `<a>` for authenticated users |

---

## Implementation Checklist

- [ ] Add `marked` to `gradle/libs.versions.toml` (new entry under `[versions]` and `[libraries]`)
- [ ] Add `marked` WebJar dependency to `adapter-in-web/build.gradle.kts`
- [ ] Add Gradle `Sync` task to copy docs Markdown files into classpath resources
- [ ] Add `docs/` to `adapter-in-web/.gitignore` (generated, not committed)
- [ ] Implement `DocsResource.kt` in `adapter-in-web`
- [ ] Create Qute templates: `docs.html`, `docs-adr-index.html`
- [ ] Update `layout.html`: add marked script tag, Docs nav link, version link
- [ ] Write `@QuarkusTest` integration tests for the new endpoints (auth required, 200 OK when logged in, 401/redirect when not)
