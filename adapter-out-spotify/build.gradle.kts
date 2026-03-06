import java.net.URI

plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
  alias(libs.plugins.openapiGenerator)
}

val spotifyOpenApiVersion: String = libs.versions.spotifyOpenApi.get()

dependencies {
  api(project(":domain-api"))

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-jackson")
  implementation("io.quarkus:quarkus-micrometer")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

val downloadSpotifySpec by tasks.registering {
  val outputFile = layout.buildDirectory.file("openapi/fixed-spotify-open-api.yml")
  inputs.property("spotifyOpenApiVersion", spotifyOpenApiVersion)
  outputs.file(outputFile)
  doLast {
    val specUrl = "https://github.com/sonallux/spotify-web-api/releases/download/v$spotifyOpenApiVersion/fixed-spotify-open-api.yml"
    try {
      outputFile.get().asFile.also { it.parentFile.mkdirs() }.writeBytes(URI(specUrl).toURL().readBytes())
    } catch (e: Exception) {
      throw GradleException(
        "Failed to download Spotify OpenAPI spec v$spotifyOpenApiVersion from $specUrl. " +
          "Check network connectivity or verify the version exists on GitHub Releases.",
        e,
      )
    }
  }
}

val patchSpotifySpec by tasks.registering {
  dependsOn(downloadSpotifySpec)
  val inputFile = layout.buildDirectory.file("openapi/fixed-spotify-open-api.yml")
  val outputFile = layout.buildDirectory.file("openapi/patched-spotify-open-api.yml")
  inputs.file(inputFile)
  outputs.file(outputFile)
  doLast {
    var spec = inputFile.get().asFile.readText()

    fun applyPatch(description: String, original: String, replacement: String) {
      val patched = spec.replace(original, replacement)
      check(patched != spec) { "Patch '$description' could not be applied: pattern not found in spec v$spotifyOpenApiVersion" }
      spec = patched
    }

    // Patch 1: SectionObject.mode type: number → integer
    // The Kotlin generator emits BigDecimal enum values for number types, producing uncompilable code.
    applyPatch(
      "SectionObject.mode type: number → integer",
      "        mode:\n          type: number\n          description: \"Indicates the modality",
      "        mode:\n          type: integer\n          description: \"Indicates the modality",
    )

    // Patch 2: PlaylistTrackObject - replace oneOf[TrackObject,EpisodeObject] with allOf[TrackObject],
    // and add non-deprecated item field alongside the now-deprecated track field.
    // The generator produces a sealed interface with empty @JsonSubTypes for oneOf, making Jackson
    // polymorphic deserialization non-functional. Episodes are still filtered in the adapter via
    // TrackObject.Type enum (READ_UNKNOWN_ENUM_VALUES_AS_NULL maps episode type to null).
    applyPatch(
      "PlaylistTrackObject oneOf → allOf[TrackObject] + item field",
      "        track:\n          oneOf:\n          - \$ref: \"#/components/schemas/TrackObject\"\n          - \$ref: \"#/components/schemas/EpisodeObject\"\n          x-spotify-docs-type: TrackObject | EpisodeObject\n          description: Information about the track or episode.\n          discriminator:\n            propertyName: type\n    QueueObject:",
      "        item:\n          allOf:\n          - \$ref: \"#/components/schemas/TrackObject\"\n          x-spotify-docs-type: TrackObject\n          description: Information about the track or episode.\n        track:\n          allOf:\n          - \$ref: \"#/components/schemas/TrackObject\"\n          x-spotify-docs-type: TrackObject\n          deprecated: true\n          description: |\n            **Deprecated:** Use `item` instead. Information about the track or episode.\n    QueueObject:",
    )

    // Patch 3: PagingPlaylistTrackObject - add snapshot_id field missing from the official spec.
    // The actual Spotify API returns it when fetching a playlist's items.
    applyPatch(
      "PagingPlaylistTrackObject.snapshot_id",
      "    PagingPlaylistTrackObject:\n      type: object\n      x-spotify-docs-type: PagingPlaylistTrackObject\n      allOf:\n      - \$ref: \"#/components/schemas/PagingObject\"\n      - type: object\n        properties:\n          items:",
      "    PagingPlaylistTrackObject:\n      type: object\n      x-spotify-docs-type: PagingPlaylistTrackObject\n      allOf:\n      - \$ref: \"#/components/schemas/PagingObject\"\n      - type: object\n        properties:\n          snapshot_id:\n            type: string\n            description: |\n              The version identifier for the current playlist. Included in the response when fetching a playlist's items.\n          items:",
    )

    outputFile.get().asFile.writeText(spec)
  }
}

openApiGenerate {
  generatorName = "kotlin"
  inputSpec = layout.buildDirectory.file("openapi/patched-spotify-open-api.yml").get().asFile.path
  outputDir = project.layout.buildDirectory.dir("generated/openapi").get().asFile.path

  modelPackage = "de.chrgroth.spotify.control.adapter.out.spotify.api.model"

  generateModelDocumentation = false
  generateApiDocumentation = false
  generateModelTests = false
  generateApiTests = false

  globalProperties = mapOf(
    "models" to "",
    "apis" to "false",
    "supportingFiles" to "false",
  )

  configOptions = mapOf(
    "serializationLibrary" to "jackson",
    "dateLibrary" to "string",
    "enumPropertyNaming" to "UPPERCASE",
  )
}

project.layout.buildDirectory.dir("generated/openapi/src/main/kotlin").get().asFile.path.also {
  kotlin.sourceSets.main {
    kotlin.srcDir(it)
  }

  java.sourceSets.main {
    kotlin.srcDir(it)
  }
}

tasks {
  named("openApiGenerate") {
    dependsOn(patchSpotifySpec)
  }

  compileKotlin {
    dependsOn(openApiGenerate)
    compilerOptions {
      // Required for auto-generated OpenAPI code which uses @JsonProperty on constructor params
      // without explicit use-site targets. Kotlin 2.3 warns about this pattern (upcoming behavior change),
      // and allWarningsAsErrors promotes it to an error. The generated code cannot be manually edited,
      // so we suppress the warning via this compiler flag instead.
      freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
  }
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
