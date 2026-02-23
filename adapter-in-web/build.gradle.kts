plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  implementation(project(":domain"))

  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-rest-jackson")
  api("io.quarkus:quarkus-security")
  api("io.quarkus:quarkus-web-dependency-locator")
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
