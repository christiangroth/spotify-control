plugins {
  id("kotlin-project")
}

dependencies {
  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-arc")
  api("io.quarkus:quarkus-vertx")
}
