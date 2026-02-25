import de.chrgroth.gradle.plugins.releasenotes.ReleasenotesConfiguration
import java.time.Duration

plugins {
  id("kotlin-project")
  alias(libs.plugins.buildTimeTracker)
  alias(libs.plugins.versionCatalogUpdate)

  alias(libs.plugins.release)
  id("de.chrgroth.gradle.plugins.releasenotes")

  id("dev.iurysouza.modulegraph") version "0.13.0"
}

buildTimeTracker {
  maxWidth = 120
  minTaskDuration = Duration.ofMillis(50)
}

moduleGraphConfig {
  includeIsolatedModules = true
  readmePath = layout.buildDirectory.file("reports/modulegraph/modules.md").get().asFile.path
}

koverMerged {
  enable()
}

private val releasenotesBasePath = "docs/releasenotes/"

releasenotes {
  mainBranch = "main"
  skipReleaseNotesOnBranchPrefixes = listOf("main", "dependabot/")

  configure {
    ReleasenotesConfiguration(
      name = "repo-markdown",
      outputPath = "$releasenotesBasePath/RELEASENOTES.md",
      snippetsPath = "$releasenotesBasePath/releasenotes-snippets",
      templatesPath = "$releasenotesBasePath/releasenotes-templates",
      bugfixesHeader = "## Bugfixes / Chore",
      bugfixesFooter = "",
      featuresHeader = "## New Features",
      featuresFooter = "",
      highlightsHeader = "",
      highlightsFooter = "",
      updateNoticesHeader = "## Breaking Changes",
      updateNoticesFooter = "",
      preserveWhitespace = true,
      dateFormat = "yyyy.MM.dd",
    )
  }
}

tasks.afterReleaseBuild {
  dependsOn(":application-quarkus:imageBuild", ":application-quarkus:imagePush")
}

release {
  failOnSnapshotDependencies = false
  git {
    requireBranch = "main"
  }
}

tasks.named("checkSnapshotDependencies") {
  enabled = false
}
