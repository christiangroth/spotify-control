plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
  id("org.openapi.generator")
}

val openApiGeneratedDir = layout.buildDirectory.dir("generated/openapi").get().asFile

openApiGenerate {
  generatorName = "kotlin"
  inputSpec = "$projectDir/src/main/resources/spotify-openapi.yaml"
  outputDir = openApiGeneratedDir.path
  packageName = "de.chrgroth.spotify.control.adapter.out.spotify.model"
  modelPackage = "de.chrgroth.spotify.control.adapter.out.spotify.model"
  generateModelDocumentation.set(false)
  generateModelTests.set(false)
  skipValidateSpec.set(true)
  configOptions = mapOf(
    "serializationLibrary" to "kotlinx_serialization",
    "enumPropertyNaming" to "UPPERCASE",
    "dateLibrary" to "string",
    "collectionType" to "list",
  )
  globalProperties = mapOf(
    "apis" to "",
    "models" to "TrackObject,SimplifiedTrackObject,ArtistObject,SimplifiedArtistObject,ImageObject,PlayHistoryObject,CursorPagingObject,CursorObject,CursorPagingPlayHistoryObject,PagingObject,PagingPlaylistObject,SimplifiedPlaylistObject,PlaylistOwnerObject,PlaylistUserObject,EpisodeObject,EpisodeBase,ContextObject,DeviceObject,DisallowsObject,PrivateUserObject,ExternalUrlObject,FollowersObject,ResumePointObject,ShowObject,ShowBase,SimplifiedShowObject,LinkedTrackObject,TrackRestrictionObject,AlbumRestrictionObject,EpisodeRestrictionObject,ExternalIdObject,CopyrightObject,PlaylistTracksRefObject,ExplicitContentSettingsObject,AlbumObject,PagingSimplifiedTrackObject,ArtistDiscographyAlbumObject,PagingArtistDiscographyAlbumObject",
    "supportingFiles" to "",
  )
}

sourceSets {
  main {
    kotlin {
      srcDir(openApiGeneratedDir.resolve("src/main/kotlin"))
      exclude("**/infrastructure/**")
      exclude("**/apis/**")
      // hand-written lenient replacements take precedence over any stale generated copies
      exclude("**/model/CurrentlyPlayingContextObject.kt")
      exclude("**/model/PlaylistTrackObject.kt")
      exclude("**/model/PagingPlaylistTrackObject.kt")
      exclude("**/model/SimplifiedAlbumObject.kt")
    }
  }
}

tasks.compileKotlin {
  dependsOn(tasks.openApiGenerate)
}

dependencies {
  api(project(":domain-api"))

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-micrometer")
  implementation("io.quarkus:quarkus-rest-client")
  implementation(libs.kotlinxSerializationJson)
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
