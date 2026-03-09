pluginManagement {
  repositories {
    maven {
      url = uri("https://maven.pkg.github.com/christiangroth/gradle-release-notes-plugin")
      credentials {
        username = providers.gradleProperty("gpr.user").orNull
          ?: System.getenv("GITHUB_ACTOR")
        password = providers.gradleProperty("gpr.token").orNull
          ?: System.getenv("GHCR_PAT")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    maven {
      url = uri("https://maven.pkg.github.com/christiangroth/quarkus-starters")
      credentials {
        username = providers.gradleProperty("gpr.user").orNull
          ?: System.getenv("GITHUB_ACTOR")
        password = providers.gradleProperty("gpr.token").orNull
          ?: System.getenv("GHCR_PAT")
      }
    }
    maven {
      this.name = "Jitpack.io"
      url = uri("https://jitpack.io")
    }
    mavenCentral()
  }
}

rootProject.name = "spotify-control"

include("adapter-in-outbox")
include("adapter-in-scheduler")
include("adapter-in-starter")
include("adapter-in-web")
include("adapter-out-mongodb")
include("adapter-out-outbox")
include("adapter-out-spotify")
include("application-quarkus")
include("domain-api")
include("domain-impl")
include("util-outbox")
