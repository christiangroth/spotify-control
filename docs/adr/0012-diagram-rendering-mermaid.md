# Diagram Rendering: Mermaid

* Status: accepted
* Deciders: Chris
* Date: 2026-07-18

## Context and Problem Statement

`docs/arc42/arc42.md` had six diagrams rendered as pre-generated PlantUML images via the public
kroki.io service (`![...](https://kroki.io/plantuml/svg/<encoded-source>)`). The diagram source was
only recoverable by decoding the URL's compressed payload — not readable or diffable in the
Markdown file itself, and any edit required re-encoding and re-embedding a new opaque URL. Two
different surfaces render this same Markdown file: GitHub's web UI (browsing the repo directly) and
this application's own in-app docs viewer (`docs.html`, `marked` WebJar rendering client-side). A
diagram format had to work acceptably on at least one of these, ideally both, without the
opaque-URL round-trip problem.

## Decision Drivers

* GitHub renders `​```mermaid` fenced code blocks as diagrams natively — no extra tooling needed
  for the GitHub-browsing use case.
* The in-app docs viewer uses `marked` for Markdown → HTML, which has no built-in diagram
  support; a `​```mermaid` block left untouched renders there as a plain code block, not a
  diagram.
* Diagram source must be plain, readable, diffable text living directly in the Markdown file —
  not an opaque encoded URL requiring an external decode step to inspect or edit.
* Chris explicitly asked for both surfaces (GitHub and in-app) to render diagrams correctly.

## Considered Options

1. **Keep kroki.io-hosted PlantUML images.**
2. **Mermaid, GitHub-native only** — use `​```mermaid` blocks, accept that the in-app viewer
   shows raw text for them.
3. **Mermaid, GitHub-native + client-side `mermaid.js` in the in-app viewer** — same source
   diagrams, but post-process the in-app viewer's rendered HTML to also render them.

## Decision Outcome

Chosen option: **"Mermaid, GitHub-native + client-side `mermaid.js` in the in-app viewer"**.
Diagrams are authored as `​```mermaid` fenced blocks, which:

- Render as diagrams automatically when browsing `arc42.md` on github.com.
- Render as diagrams in the in-app docs viewer via a small module script in `docs.html`: after
  `marked.parse()` runs, it finds `<pre><code class="language-mermaid">` blocks (marked's
  standard output for an unrecognised fenced-code language), converts each to a `<div
  class="mermaid">` containing the raw source, and calls `mermaid.run()`. The `mermaid` WebJar
  (`org.webjars.npm:mermaid`) is added as a dependency and loaded as an ES module from
  `/webjars/mermaid/dist/mermaid.esm.min.mjs`, mirroring how `marked` itself is already loaded.
- Are initialized with a fixed dark theme (`mermaid.initialize({ theme: 'dark' })`), matching the
  application's single dark UI — no theme-toggle re-render logic is needed here, unlike a
  light/dark-aware app.

All six existing diagrams (module overview, playback flow, playlist sync flow, catalog sync flow,
playlist checks flow, playback aggregation rollup) were converted from PlantUML to equivalent
Mermaid `flowchart`/`sequenceDiagram` sources.

### Positive Consequences

* Diagrams are plain, greppable, diffable text living next to the prose that describes them — no
  opaque encoded URLs, no external decode step needed to read or edit a diagram.
* Correct rendering on both surfaces that actually matter for this project (GitHub review, in-app
  reading).
* The conversion glue is generic (looks for any `language-mermaid` block) — no per-diagram wiring
  needed when new diagrams are added later.
* No dependency on an external rendering service (kroki.io) being reachable or unchanged.

### Negative Consequences

* Adds a new runtime dependency (`mermaid` WebJar, a few hundred KB) loaded on every visit to the
  Docs page, even for pages without any diagrams (the conversion script no-ops quickly if there
  are none, but the module is still fetched).
* Mermaid is loaded as an ES module (`dist/mermaid.esm.min.mjs`); older browsers without ES module
  support would silently fail to render diagrams in-app (GitHub rendering is unaffected). Not a
  concern for this project's actual user base.
* One more moving part in `docs.html` beyond the original single `marked.parse()` call.

## Pros and Cons of the Options

### Keep kroki.io-hosted PlantUML images

* Good, because no new dependency or code is needed.
* Bad, because diagram source is only recoverable by decoding the embedded URL — not readable or
  diffable in the Markdown file itself.
* Bad, because it depends on kroki.io, a third-party service, being reachable indefinitely.
* Bad, because GitHub does not render arbitrary image URLs as interactively as its native Mermaid
  support, and the in-app viewer gets no special treatment either way — an external image URL
  works the same on both surfaces, but neither renders it any better than a static picture.

### Mermaid, GitHub-native only

* Good, because zero extra dependencies or code — just write `​```mermaid` blocks.
* Bad, because the in-app docs viewer — the primary way a logged-in user reads this documentation
  without leaving the app — would show raw, unrendered diagram source.

### Mermaid, GitHub-native + client-side `mermaid.js`

* Good, because both real-world reading surfaces render actual diagrams.
* Good, because the diagram source is identical either way — no dual-authoring.
* Bad, because it adds a WebJar dependency and glue code in `docs.html`.

## Links

* [Mermaid documentation](https://mermaid.js.org/)
* [GitHub: creating diagrams with Mermaid](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams)
* [`docs.html`](../../adapter-in-http-frontend/src/main/resources/templates/docs.html)
* [ADR 0005: Markdown Rendering Library: marked](0005-markdown-rendering-library.md)
