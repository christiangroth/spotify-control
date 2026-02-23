import java.time.Duration

plugins {
  id("kotlin-project")
  alias(libs.plugins.buildTimeTracker)
  alias(libs.plugins.versionCatalogUpdate)
  id("dev.iurysouza.modulegraph") version "0.13.0"
}

buildTimeTracker {
  maxWidth = 120
  minTaskDuration = Duration.ofMillis(50)
}

koverMerged {
  enable()
}

moduleGraphConfig {
  includeIsolatedModules = true
  readmePath = "${project.buildDir}/modules.md"
}
