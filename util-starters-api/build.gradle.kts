plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
  `maven-publish`
}

group = "de.chrgroth.starters"

dependencies {
  implementation(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-scheduler")
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
