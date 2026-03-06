plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
  alias(libs.plugins.openapiGenerator)
}

dependencies {
  api(project(":domain-api"))

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-jackson")
  implementation("io.quarkus:quarkus-micrometer")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

openApiGenerate {
  generatorName = "kotlin"
  inputSpec = "${projectDir}/src/main/openapi/spotify-web-api.yml"
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
