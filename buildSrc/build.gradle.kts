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
  implementation("org.openapitools:openapi-generator-gradle-plugin:7.9.0")
}
