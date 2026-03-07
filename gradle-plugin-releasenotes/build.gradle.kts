plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
  `maven-publish`
}

group = "de.chrgroth.gradle.plugins"
version = "1.0.0"

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(libs.grgit)
}

gradlePlugin {
  plugins {
    create("releasenotes") {
      id = "de.chrgroth.gradle.plugins.releasenotes"
      implementationClass = "de.chrgroth.gradle.plugins.releasenotes.ReleasenotesPlugin"
    }
  }
}
