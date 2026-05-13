plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(libs.kotlinGradlePlugin)
  implementation(libs.kotlinGradleSerializationPlugin)
  implementation(libs.detekt)
  implementation(libs.kover)
  implementation("org.openapitools:openapi-generator-gradle-plugin:7.9.0")
}
