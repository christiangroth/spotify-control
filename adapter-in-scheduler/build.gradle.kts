plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  implementation(project(":domain-api"))
  implementation(libs.quarkusStartersDomainApi)

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-scheduler")
  implementation("io.quarkus:quarkus-micrometer")
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
