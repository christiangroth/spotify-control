plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  api(project(":domain-api"))
  implementation(project(":util-outbox"))

  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-jackson")
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
