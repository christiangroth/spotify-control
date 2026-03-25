# Plan: Alternatives Genre-Management

Stand: 2026.03

## Problem

Spotify liefert keine Genre-Daten mehr über die API. Die bisherigen Felder `genre`, `additionalGenres` (Artist) und `genreOverrides` (Album) wurden bereits aus dem Datenmodell entfernt (siehe `RemoveGenreFieldsStarter`). Damit fehlt aktuell jede Möglichkeit, Tracks oder Playlists nach Genre zu klassifizieren oder zu filtern.

## Datenmodell-Konzept

Das grundlegende Konzept:

- **Artist-Ebene:** Ein Artist hat ein primäres Genre (und ggf. weitere Genres).
- **Album-Ebene:** Ein Album kann das Genre seines Künstlers optional überschreiben.
- **Effektives Genre:** Album-Genre-Überschreibung (falls vorhanden) → Artist-Genre.

Dieses Konzept ist einfach und ausreichend für den vorliegenden Anwendungsfall. Eine Track-spezifische Genre-Vergabe ist explizit nicht vorgesehen, da sie unverhältnismäßig viel Pflegeaufwand erzeugt.

## Ansätze

### Option A: Externe API

Es existieren mehrere frei verfügbare Musikdatenbanken, die Genre-Informationen bereitstellen. Ein neuer Outbound-Adapter `adapter-out-musicmeta` würde die externe API-Integration kapseln – analog zu `adapter-out-spotify`.

#### MusicBrainz (empfohlen)

- Open-Source-Musikenzyklopädie, komplett kostenlos, kein API-Key erforderlich.
- Genre-Tags für Artists (`/ws/2/artist?query=...`) und Releases (`/ws/2/release-group?query=...`).
- Suche erfolgt über Künstlernamen bzw. Albumtitel – ein exakter Match ist nicht garantiert, kann aber durch Trefferquote-Schwellenwerte abgesichert werden.
- Rate Limit: 1 Anfrage/Sekunde (mit User-Agent-Header; ohne wird aggressiver gedrosselt).
- Integration über die bestehende Outbox-Infrastruktur, um Rate-Limits zuverlässig zu handhaben.

#### Last.fm

- Freie API (API-Key erforderlich, kostenlos registrierbar).
- Tags pro Artist (`artist.getTopTags`) und Album (`album.getTopTags`) – enthält Genre-ähnliche Tags.
- Suche erfolgt über Name; Top-Tag lässt sich als primäres Genre interpretieren.
- Erfordert einen weiteren API-Key, der sicher als Secret verwaltet werden muss.

#### Discogs

- API für Releases mit expliziten Genres und Sub-Genres (Styles).
- Kostenfrei mit Token; Rate Limit vorhanden.
- Stärker auf physische Releases fokussiert; Album-Coverage kann variieren.

### Option B: Manuelles Genre-Management

- Genres werden direkt im Katalog über die Web-UI gepflegt (Artist/Album-Bearbeitungsmaske).
- Volle Kontrolle, keine externe Abhängigkeit.
- Erheblicher Pflegeaufwand bei mehreren hundert Artists/Alben.
- Geeignet als Ergänzung, nicht als alleinige Lösung.

### Option C: Hybrid (empfohlen)

Kombination aus automatischer Befüllung via externer API und manueller Überschreibungsmöglichkeit:

1. **Automatische Befüllung:** Beim Artist-Sync (bereits vorhanden) wird zusätzlich ein Genre-Lookup über MusicBrainz angestoßen. Analog beim Album-Sync für Album-spezifische Genre-Überschreibungen.
2. **Manuelle Überschreibung:** Im Katalog kann jedes Artist-Genre und jedes Album-Genre-Override manuell gesetzt oder korrigiert werden. Manuell gesetzte Werte werden durch spätere automatische Sync-Läufe nicht überschrieben.

Dieser Ansatz minimiert den Pflegeaufwand, gibt dem Nutzer aber volle Kontrolle über Ausnahmen.

## Empfehlung

**Option C (Hybrid) mit MusicBrainz als externer API.**

Begründung:
- MusicBrainz ist kostenlos, kein zusätzliches Secret nötig, gute Abdeckung für westliche Popmusik.
- Die bestehende Outbox-Infrastruktur ist bereits für externe API-Calls mit Rate-Limiting ausgelegt.
- Manuelle Überschreibungen als Sicherheitsnetz für falsche oder fehlende Matches.

## Umsetzungsschritte (grob)

1. **Datenmodell:** `AppArtist` um `genre: String?` und `genreSource: GenreSource?` (AUTO / MANUAL) ergänzen. `AppAlbum` analog um `genreOverride: String?` und `genreOverrideSource: GenreSource?`.
2. **MusicBrainz-Adapter:** Neues Modul `adapter-out-musicmeta` mit MusicBrainz-REST-Client. Suche nach Artist-Genre und Release-Group-Genre per Name. Ergebnis-Mapping: Top-Genre-Tag → `genre`-Feld.
3. **Outbox-Events:** Neue Events `SyncArtistGenre` und `SyncAlbumGenre` (Partition: `domain` oder neues `to-musicmeta`). Nur auslösen, wenn kein manuelles Genre gesetzt ist (`genreSource != MANUAL`).
4. **Sync-Integration:** `SyncController` ruft Genre-Sync nach Artist-Details-Sync auf.
5. **UI:** Bearbeitungsmasken für Artist-Genre und Album-Genre-Override im Katalog. Anzeige des effektiven Genres im Katalog und im Artist-Detail-View.
6. **Playlist-Checks / Filters:** Genre als filterbare Dimension in zukünftigen Auswertungen.

## Offene Fragen

- Wie hoch ist die MusicBrainz-Trefferquote für die vorhandenen Artists/Alben? → Validierung mit einem Testlauf empfohlen, bevor die vollständige Integration gebaut wird.
- Soll Last.fm als Fallback eingebaut werden, wenn MusicBrainz kein Genre liefert?
- Welcher Score-Schwellenwert (MusicBrainz liefert eine Trefferquote) gilt als akzeptabler Match?
