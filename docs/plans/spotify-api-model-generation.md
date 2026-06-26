# Plan: Generierte Spotify-API-Modelle ohne handgeschriebene Dopplungen

Stand: 2026.06

## Problem

Trotz OpenAPI-Codegenerierung (`org.openapi.generator`, siehe `adapter-out-spotify/build.gradle.kts`) existiert
weiterhin ein Haufen handgeschriebener Model-Klassen in `SpotifyApiResponseModels.kt`, die generierte Klassen
1:1 ersetzen und über `exclude(...)` im Sourceset von der Generierung ausgeschlossen werden:

- `AlbumObject`, `SimplifiedAlbumObject`
- `ArtistDiscographyAlbumObject`, `PagingArtistDiscographyAlbumObject`
- `PagingSimplifiedTrackObject`
- `CurrentlyPlayingContextObject`, `PlaylistTrackObject`, `PagingPlaylistTrackObject`

Diese Dopplung ist über drei PRs entstanden (#636 initiale Generierung, #640 Fix für Artist-Discography,
#641 nochmal Artist-Discography) – jedes Mal als reaktiver Patch für einen konkreten Production-Fehler
(`MissingFieldException`), nicht als strukturelle Lösung. Das Muster wiederholt sich und wird sich mit jedem
weiteren Spotify-API-Edge-Case wiederholen, solange die Ursache nicht behoben ist.

## Root Cause

Es gibt **zwei unterschiedliche, unabhängige Ursachen**, die in der bisherigen Diskussion vermischt wurden.
Das ist wichtig, weil sie unterschiedliche Lösungen brauchen.

### Ursache 1: Spotifys eigene OpenAPI-Spec ist bei `required` unzuverlässig (Hauptursache)

Die eingecheckte Spec (`adapter-out-spotify/src/main/resources/spotify-openapi.yaml`, 1:1 von Spotify
heruntergeladen) deklariert Felder als `required`, die Spotify in der Praxis nachweislich nicht immer liefert.
Beispiel `AlbumBase` (Zeile 6883 ff.), Basis von `AlbumObject`/`SimplifiedAlbumObject`:

```yaml
AlbumBase:
  required:
    - album_type
    - total_tracks
    - available_markets
    - external_urls
    - href
    - id
    - images
    - name
    - release_date
    - release_date_precision
    - type
    - uri
```

`id`, `name`, `images`, `release_date` etc. sind laut Spec also Pflichtfelder. Genau diese Felder fehlen aber
real in Artist-Discography-Antworten (#640, #641) und mussten deshalb in der handgeschriebenen Ersatzklasse
nullable gemacht werden. Gleiches Muster bei `PagingSimplifiedTrackObject`/`ArtistDiscographyAlbumObject`.

Der openapi-generator hält sich strikt an die Spec: `required` → Kotlin-Property ohne `?`, ohne Default →
kotlinx-serialization wirft `MissingFieldException`, sobald das Feld im echten JSON fehlt. Der Generator
generiert also technisch korrekt – das Problem ist, dass die Spec lügt. Das ist ein bekanntes, dokumentiertes
Problem der offiziellen Spotify-OpenAPI-Spec (nicht Copilot, nicht der Generator).

**Das ist der Teil, den wir strukturell lösen können und sollten.**

### Ursache 2: Polymorphie (`oneOf` + discriminator) – kein Nullability-Problem

`PlaylistTrackObject.item` (und `.track`, sowie `CurrentlyPlayingContext.item`/`QueueObject.currently_playing`)
sind in der Spec als `oneOf: [TrackObject, EpisodeObject]` mit Discriminator modelliert:

```yaml
item:
  oneOf:
    - $ref: '#/components/schemas/TrackObject'
    - $ref: '#/components/schemas/EpisodeObject'
  discriminator:
    propertyName: type
```

Der `kotlin`-Generator mit `kotlinx_serialization` löst solche discriminated unions nicht zuverlässig in
funktionierende `@Serializable` Sealed-Classes auf. Das ist der Grund, warum hier mit `item: JsonElement?`
gearbeitet wird – das hat nichts mit falscher Nullability zu tun und wird durch eine Required-Korrektur der
Spec **nicht** gelöst.

**Diese Klassen bleiben unabhängig von der Lösung für Ursache 1 weiterhin handgeschrieben.** Das ist in
Ordnung, solange es eine kleine, klar begründete Ausnahme bleibt statt eines wachsenden Sammelbeckens.

## Bisherige Versuche und warum sie nicht reichen

| PR | Ansatz | Bewertung |
|----|--------|-----------|
| #636 | Generator eingeführt, erste handgeschriebene Lenient-Replacements für 4 Klassen | Hat das Muster etabliert, ohne die Ursache zu beheben |
| #640 | 2 weitere Klassen handgeschrieben + excluded, weil Artist-Discography wieder MissingFieldException warf | Reiner Symptom-Fix, gleiche Ursache wie #636 |
| #641 (nicht gemerged) | Gleiches Problem erneut, plus Regel "Spec darf nie händisch editiert werden" | Bestätigt nur, dass das Spec-Editieren der falsche Hebel ist – ohne eine Alternative zu liefern |

Allen drei gemeinsam: Es wird **pro betroffener Klasse** reagiert, nachdem ein konkreter Production-Fehler
auftrat. Es gibt keinen Mechanismus, der das für alle (auch noch nicht aufgefallenen) Klassen vorab korrigiert.

## Lösungsansatz: Spec-Transformation vor der Codegenerierung

Statt die eingecheckte Spec zu editieren (das würde den Update-Check-Workflow
`.github/workflows/spotify-openapi-check.yml` unbrauchbar machen, der die Datei 1:1 mit dem Upstream-Download
vergleicht) wird ein **Gradle-Vorverarbeitungsschritt** eingeführt, der nur für die Codegenerierung wirkt:

1. Neuer Task `prepareOpenApiSpec` (in `adapter-out-spotify/build.gradle.kts` oder als Konvention in
   `buildSrc`), der:
   - die eingecheckte `src/main/resources/spotify-openapi.yaml` unverändert liest,
   - in `components.schemas.*` rekursiv **alle `required`-Listen entfernt** (für reine Response-Modelle ist
     das vertretbar – wir sind ausschließlich Konsument dieser Schemas, nie Produzent; "optimistisch
     nullable" ist für einen API-Client immer sicherer als "optimistisch non-null"),
   - das Ergebnis nach `build/generated/openapi-spec/spotify-openapi.yaml` schreibt.
2. `openApiGenerate.inputSpec` zeigt auf die transformierte Datei statt auf die Resource; `openApiGenerate`
   bekommt `dependsOn(tasks.prepareOpenApiSpec)`.
3. Die eingecheckte `spotify-openapi.yaml` bleibt byte-identisch mit dem Upstream-Download – der
   Update-Check-Workflow funktioniert unverändert weiter.
4. Implementierung der YAML-Transformation: snakeyaml (transitive Abhängigkeit von
   `openapi-generator-gradle-plugin`, ggf. explizit in `buildSrc` deklarieren) reicht für das reine
   Entfernen von `required`-Knoten – keine eigene YAML-Library nötig.

### Warum global und nicht weiterhin pro Klasse?

Eine Lösung, die nur die *aktuell bekannten* kaputten Klassen patcht, wiederholt exakt das Muster aus #636/#640/
#641. Spotify kann jederzeit ein weiteres "required" Feld in der Praxis weglassen, das heute noch nicht
aufgefallen ist. Die globale Transformation macht das Risiko strukturell verschwinden, statt es Klasse für
Klasse einzusammeln, sobald es in Prod auffällt.

## Auswirkungen auf bestehenden Code

Nach der Spec-Transformation generiert der Generator **alle** Properties nullable. Das heißt:

- Die handgeschriebenen Lenient-Replacements für `AlbumObject`, `SimplifiedAlbumObject`,
  `ArtistDiscographyAlbumObject`, `PagingArtistDiscographyAlbumObject`, `PagingSimplifiedTrackObject` werden
  gelöscht; die zugehörigen `exclude(...)`-Einträge im Sourceset entfallen.
- `CurrentlyPlayingContextObject`, `PlaylistTrackObject`, `PagingPlaylistTrackObject` bleiben handgeschrieben
  (Ursache 2, siehe oben) – mit einem klaren Kommentar, warum diese drei Ausnahmen bleiben, damit das nicht
  erneut als "vergessene Migration" missverstanden wird.
- Aufrufstellen in `SpotifyCatalogAdapter.kt` etc. nutzen bereits an vielen Stellen defensive Null-Checks
  (`artist.id ?: ""`, `album.artists.firstOrNull()`, `mapNotNull { it.id }`) – diese Stellen sind unverändert
  korrekt. Es muss aber ein vollständiger Review aller Zugriffe auf vormals "required" generierte Felder
  erfolgen, da manche Stellen evtl. noch unconditional `!!`/Direktzugriff ohne Null-Check verwenden und jetzt
  einen Kotlin-Compile-Fehler (gut, fail-fast) statt eines Runtime-Fehlers bekommen.
- `SpotifyApiResponseModelsTests.kt` wird auf die verbleibenden 3 Klassen reduziert; Tests für die jetzt
  generierten Klassen werden – falls Regressionsschutz gewünscht – als Generated-Model-Tests im bestehenden
  Testmuster (siehe `SpotifyGeneratedArtistAlbumsModelsTests` aus #641) ergänzt.

## Out of Scope

- `SpotifyAuthApiModels.kt` (`SpotifyTokenResponse`) – stammt von `accounts.spotify.com`, einer komplett
  anderen API, die nicht Teil der Web-API-OpenAPI-Spec ist. Bleibt handgeschrieben.
- `SpotifyPlaylistRequestModels.kt` – das sind ausgehende Request-Bodies (Playlist-Mutationen), keine
  Response-Modelle. Für Requests, die wir selbst erzeugen, ist Codegenerierung kein Mehrwert und das
  Nullability-Problem existiert hier nicht (wir kontrollieren den Inhalt).

## Offene Fragen

- Reicht "alle `required` entfernen" oder sollte differenzierter vorgegangen werden (z. B. nur für
  Properties, die nicht primitive Typen sind)? Vorschlag: erstmal global, da wir reiner Konsument sind und
  ein zu defensives Modell (alles nullable) für einen Read-Adapter kein Korrektheitsrisiko ist, nur ein
  Ergonomie-Tradeoff (mehr `?.`/`?:` an den Aufrufstellen).
- Soll der Transformationsschritt zusätzlich `deprecated: true` Felder herausfiltern (z. B. `AlbumObject.genres`,
  `.label`, `.popularity`), um generierten Code weiter zu verschlanken? Nicht notwendig für das eigentliche
  Problem, aber ein günstiger Zusatznutzen, falls gewünscht.
- Migrationsreihenfolge: erst Spec-Transformation + Generator-Output verifizieren (Diff der generierten
  Klassen vor/nach), dann erst handgeschriebene Klassen entfernen und Aufrufstellen anpassen – nicht in einem
  Schritt, um Regressionen leichter zuordnen zu können.

## Umsetzungsschritte (grob)

1. `prepareOpenApiSpec`-Task bauen, `openApiGenerate` darauf umstellen.
2. Generierten Code für die 5 betroffenen Klassen lokal mit `./gradlew :adapter-out-spotify:openApiGenerate`
   prüfen – sind wirklich alle Properties jetzt nullable?
3. Handgeschriebene Lenient-Replacements + `exclude(...)`-Einträge für diese 5 Klassen entfernen.
4. `SpotifyCatalogAdapter.kt` und weitere Aufrufstellen kompilieren lassen, Compile-Fehler durch Null-Checks
   beheben.
5. Tests anpassen/ergänzen, `./gradlew build` grün bekommen.
6. Kommentar an den 3 verbleibenden handgeschriebenen Klassen ergänzen, der auf dieses Plan-Dokument verweist
   und die `oneOf`-Begründung kurz zusammenfasst.
