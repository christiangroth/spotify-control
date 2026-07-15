plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

dependencies {
  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-micrometer")
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}
