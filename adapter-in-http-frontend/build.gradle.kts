import java.io.File

plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
}

kotlin {
  // Qute message bundle interfaces (see AppMessages) resolve `{paramName}` placeholders via reflection on method
  // parameter names, which the JVM only retains when compiled with this flag.
  compilerOptions {
    javaParameters = true
  }
}

dependencies {
  implementation(project(":domain-api"))

  api(enforcedPlatform(libs.quarkusBom))
  api("io.quarkus:quarkus-rest-jackson")
  api("io.quarkus:quarkus-rest-qute")
  api("io.quarkus:quarkus-security")
  api("io.quarkus:quarkus-web-dependency-locator")

  implementation(libs.bootstrap)
  implementation(libs.bootstrapIcons)
  implementation(libs.marked)
  implementation(libs.mermaid)
}

// Bundle qualifiers to pseudo-localize, matching the `messages/<qualifier>_en.properties` files (see AppMessages and
// its domain-specific siblings in the i18n package).
val pseudoLocaleBundleQualifiers = listOf("msg", "dashboard", "catalog", "playback", "playlists", "config", "health", "monitoring", "stats", "docs")
val pseudoLocaleOutputDir = layout.buildDirectory.dir("generated/resources/pseudoLocale")

sourceSets {
  main {
    resources {
      srcDir(pseudoLocaleOutputDir)
    }
  }
}

tasks {
  // Generates the "Underscore" test locale (`xx`) from the English properties: every non-whitespace character becomes
  // `__`, `{param}` placeholders are preserved untouched, and whitespace is kept as-is. Regenerated on every build so
  // it can never drift out of sync with the English source of truth.
  val generatePseudoLocaleMessages by registering {
    val sourceDir = layout.projectDirectory.dir("src/main/resources/messages")
    val outputDir = pseudoLocaleOutputDir.map { it.dir("messages") }
    inputs.dir(sourceDir)
    outputs.dir(outputDir)

    doLast {
      val outDir = outputDir.get().asFile
      outDir.mkdirs()
      pseudoLocaleBundleQualifiers.forEach { qualifier ->
        val enFile = sourceDir.file("${qualifier}_en.properties").asFile
        if (enFile.exists()) {
          File(outDir, "${qualifier}_xx.properties").bufferedWriter(Charsets.UTF_8).use { writer ->
            enFile.forEachLine(Charsets.UTF_8) { line ->
              val trimmed = line.trim()
              val separatorIndex = line.indexOf('=')
              val pseudoLocalizedLine = if (trimmed.isEmpty() || trimmed.startsWith("#") || separatorIndex < 0) {
                line
              } else {
                line.substring(0, separatorIndex + 1) + pseudoLocalize(line.substring(separatorIndex + 1))
              }
              writer.write(pseudoLocalizedLine)
              writer.newLine()
            }
          }
        }
      }
    }
  }

  val syncDocsMd by registering(Sync::class) {
    mustRunAfter(rootProject.tasks.named("releasenotesCopyToSources"))
    from(rootProject.layout.projectDirectory.dir("docs/arc42")) {
      include("arc42.md", "outbox.md", "starters.md")
      into("arc42")
    }
    from(rootProject.layout.projectDirectory.dir("docs/adr")) {
      include("*.md")
      exclude("0000-template.md")
      into("adr")
    }
    from(rootProject.layout.projectDirectory.dir("docs/releasenotes")) {
      include("RELEASENOTES.md")
      into("releasenotes")
    }
    from(rootProject.layout.projectDirectory.dir("docs/coding-guidelines")) {
      include("*.md")
      into("coding-guidelines")
    }
    into(layout.projectDirectory.dir("src/main/resources/docs"))
  }

  named("processResources") {
    dependsOn(syncDocsMd, generatePseudoLocaleMessages)
  }
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
}

// Replaces every non-whitespace character in an English message value with `__` for the "Underscore" pseudo-locale,
// keeping the content of `{param}` placeholders and whitespace untouched.
fun pseudoLocalize(value: String): String {
  val result = StringBuilder(value.length * 2)
  var placeholderDepth = 0
  for (c in value) {
    when {
      c == '{' -> {
        placeholderDepth++
        result.append(c)
      }
      c == '}' -> {
        placeholderDepth = maxOf(0, placeholderDepth - 1)
        result.append(c)
      }
      placeholderDepth > 0 -> result.append(c)
      c.isWhitespace() -> result.append(c)
      else -> result.append("__")
    }
  }
  return result.toString()
}
