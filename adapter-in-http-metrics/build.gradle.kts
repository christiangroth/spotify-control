plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  implementation(project(":domain-api"))

  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-micrometer")
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
