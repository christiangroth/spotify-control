plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
  alias(libs.plugins.openapiGenerator)
}

dependencies {
  implementation(project(":domain-api"))

  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-rest-jackson")
  api("io.quarkus:quarkus-security")
  api("io.quarkus:quarkus-web-dependency-locator")
}
val processedOpenApiSpec = layout.buildDirectory.file("generated/openapi/rest-api-spec.yml")

openApiGenerate {
  generatorName = "kotlin-server"
  inputSpec = processedOpenApiSpec.get().asFile.path
  outputDir = project.layout.buildDirectory.dir("generated/openapi").get().asFile.path

  packageName = "de.chrgroth.spotify.control.adapter.incoming.web"
  apiPackage = "de.chrgroth.spotify.control.adapter.incoming.web.api"
  modelPackage = "de.chrgroth.spotify.control.adapter.incoming.web.api.model"

  generateModelDocumentation = false
  generateApiDocumentation = false
  generateModelTests = false
  generateApiTests = false

  configOptions = mapOf(
    "library" to "jaxrs-spec",
    "useJakartaEe" to "true",
    "interfaceOnly" to "true",
    "returnResponse" to "false",
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
  val prepareOpenApiSpec by registering(Copy::class) {
    from("src/main/openapi/rest-api-spec.yml")
    into(processedOpenApiSpec.get().asFile.parentFile)
    rename { "rest-api-spec.yml" }

    filteringCharset = "UTF-8"
    filter { line ->
      line.replace("@projectVersion@", project.version.toString())
    }
  }

  named("openApiGenerate") {
    dependsOn(prepareOpenApiSpec)
  }

  compileKotlin {
    dependsOn(openApiGenerate)
  }
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
}

