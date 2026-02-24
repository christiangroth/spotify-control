# Technisches Konzept: Frontend

## Stack

- **Templates:** Qute (Quarkus SSR) – kein separates Frontend-Projekt
- **CSS:** Bootstrap 5 via WebJar
- **Interaktivität:** htmx via WebJar
- **Icons:** Font Awesome via WebJar
- **Charts:** MongoDB Charts Embedding SDK (via CDN oder gevendort)
- **Live-Updates:** Server-Sent Events via htmx `hx-ext="sse"`
- **Kein:** React, Vue, npm, Node.js, Build-Step

## WebJar Dependencies (Gradle)

Jeweils aktuellste Version verwenden.

```kotlin
implementation("org.webjars:bootstrap:5.3.2")
implementation("org.webjars.npm:htmx.org:1.9.10")
implementation("org.webjars:font-awesome:6.5.1")
```

Alle Includes ausschließlich in `layout.html`.

## Routen

| Route                          | Beschreibung                                       |
|--------------------------------|----------------------------------------------------|
| `/`                            | Redirect: Session → `/dashboard`, sonst → `/login` |
| `/ui/login`                    | Spotify Login                                      |
| `/oauth/callback`              | OAuth Callback (kein Template)                     |
| `/ui/dashboard`                | Hauptseite                                         |
| `/ui/charts`                   | MongoDB Charts Vollansicht                         |
| `/ui/admin/playlists`          | Playlist-Sync-Konfiguration + Typ-Zuweisung        |
| `/ui/admin/genres`             | Genre-Override-Verwaltung                          |
| `/ui/actions/{action}/{token}` | Approve/Reject → Redirect `/dashboard`             |
| `/ui/sse/outbox`               | SSE Endpoint                                       |
| `/ui/sse/playback`             | SSE Endpoint                                       |

Erster Login → `setup_complete: false` in Session → Redirect auf `/admin/playlists`.

## Template-Struktur

```
templates/
├── layout.html                ← Basis: Nav, alle WebJar-Includes, Dark Theme Variablen
├── login.html                 ← Zentrierte Card, Spotify-Logo, ein CTA
├── dashboard.html
├── charts.html
├── admin/
│   ├── playlists.html
│   └── genres.html
└── fragments/
    ├── outbox-stats.html      ← SSE-Target
    ├── playback-live.html     ← SSE-Target
    ├── playlist-card.html
    ├── stats-widget.html
    ├── pending-upgrades.html  ← Approve/Reject Links
    └── success-banner.html    ← verschwindet nach 10s via htmx
```

Fragments sind eigenständig renderbar – funktionieren als SSE-Push und als initialer Page-Load.

## Design-System

Dunkles, technisches Erscheinungsbild. Definiert in `layout.html` `<style>`:

```css
:root {
  --bs-body-bg: #0d1117;
  --bs-body-color: #e6edf3;
  --accent: #1db954;        /* Spotify Grün – sparsam für CTAs, aktive States */
  --accent-muted: #1a9e47;
  --surface: #161b22;
  --border: #30363d;
}
```

Monospace-Font für technische Werte (IDs, Timestamps, Queue-Zahlen). Live-Indikatoren (●) in `--accent` mit CSS Puls-Animation.

## Dashboard-Layout

```
┌─────────────────────────────────────────────┐
│  Stats-Row: Plays 30d | Unique Tracks | Top Artist  │
├──────────────────────┬──────────────────────┤
│  Pending Upgrades    │  Live-Widget         │
│  (Approve/Reject)    │  ● Outbox Status     │
├──────────────────────┤  ● Letzter Sync      │
│  Playlist-Übersicht  │  ● Letzte 3 Plays    │
│  (Typ-Badge, Sync)   │                      │
├──────────────────────┴──────────────────────┤
│  → MongoDB Charts          → Admin          │
└─────────────────────────────────────────────┘
```

## htmx-Muster

```html
<!-- Toggle (Admin) -->
<button hx-post="/admin/playlists/{id}/toggle-sync"
        hx-target="#playlist-card-{id}"
        hx-swap="outerHTML">
  Sync aktivieren
</button>

<!-- SSE Live-Widget -->
<div hx-ext="sse" sse-connect="/live/outbox" sse-swap="message" id="outbox-stats">
  <!-- initialer Inhalt vom Server -->
</div>

<!-- Success Banner (verschwindet nach 10s) -->
<div id="success-banner"
     hx-get="/fragments/empty"
     hx-trigger="load delay:10s"
     hx-swap="outerHTML"
     class="alert alert-success">
  ✅ Aktion ausgeführt
</div>
```

Banner wird nur gerendert wenn `?success=` Query-Parameter gesetzt (kommt vom Action-Endpoint Redirect).

## MongoDB Charts Embedding

```javascript
// Minimales Vanilla JS – nur für Charts SDK nötig
const sdk = new ChartsEmbedSDK({ baseUrl: 'https://charts.mongodb.com/charts-xxx' });
const chart = sdk.createChart({
  chartId: 'your-chart-id',
  filter: { period: currentPeriod }
});
chart.render(document.getElementById('chart-container'));
```

JWT für Charts-Auth wird vom Backend ausgestellt (`GET /api/charts/token`).
