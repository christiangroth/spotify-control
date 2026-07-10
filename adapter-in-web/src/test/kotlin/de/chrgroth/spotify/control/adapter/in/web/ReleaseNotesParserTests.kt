package de.chrgroth.spotify.control.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReleaseNotesParserTests {

  @Test
  fun `parses a single version entry with all categories`() {
    val content = """
      # 0.107.16 (2026.07.10)

      ## Breaking Changes
      * Removed the legacy config endpoint.

      ## New Features
      * Added a new dashboard panel.

      ## Bugfixes / Chore
      * Fixed a slow database query.
      * Unified log format.



      ---

    """.trimIndent()

    val entries = ReleaseNotesParser.parse(content)

    assertThat(entries).hasSize(1)
    val entry = entries.single()
    assertThat(entry.version).isEqualTo("0.107.16")
    assertThat(entry.date).isEqualTo("2026.07.10")
    assertThat(entry.breakingChanges).containsExactly("Removed the legacy config endpoint.")
    assertThat(entry.features).containsExactly("Added a new dashboard panel.")
    assertThat(entry.bugfixes).containsExactly("Fixed a slow database query.", "Unified log format.")
    assertThat(entry.highlights).isEmpty()
  }

  @Test
  fun `parses multiple version entries separated by dashes`() {
    val content = """
      # 0.107.15 (2026.07.10)

      ## Bugfixes / Chore
      * Fixed a slow database query on the Catalog Sync page.



      ---

      # 0.107.14 (2026.07.10)

      ## Bugfixes / Chore
      * The displayed user name is now cached instead of being fetched on every page load.



      ---

    """.trimIndent()

    val entries = ReleaseNotesParser.parse(content)

    assertThat(entries.map { it.version }).containsExactly("0.107.15", "0.107.14")
    assertThat(entries[0].bugfixes).containsExactly("Fixed a slow database query on the Catalog Sync page.")
    assertThat(entries[1].bugfixes).containsExactly("The displayed user name is now cached instead of being fetched on every page load.")
  }

  @Test
  fun `parses free text before the first category header as a highlight`() {
    val content = """
      # 0.0.1 (2026.02.24)

      The basic project skeleton was developed and deployed.

      ## New Features
      * Basic technical documentation and project plans.
      * Release notes structure.

      ---

    """.trimIndent()

    val entries = ReleaseNotesParser.parse(content)

    val entry = entries.single()
    assertThat(entry.highlights).containsExactly("The basic project skeleton was developed and deployed.")
    assertThat(entry.features).containsExactly("Basic technical documentation and project plans.", "Release notes structure.")
  }

  @Test
  fun `parses an unrecognized header as a highlight`() {
    val content = """
      # 0.108.0 (2026.07.11)

      ## single-user-migration: Migrated the whole application to a strict single-user design.

      ## New Features
      * Added a new dashboard panel.

      ---

    """.trimIndent()

    val entries = ReleaseNotesParser.parse(content)

    val entry = entries.single()
    assertThat(entry.highlights).containsExactly("single-user-migration: Migrated the whole application to a strict single-user design.")
    assertThat(entry.features).containsExactly("Added a new dashboard panel.")
  }

  @Test
  fun `handles the last entry in the file without a trailing dash separator`() {
    val content = """
      # 0.1.0 (2026.02.25)

      ## New Features
      * Added login page.

      ---

      # 0.0.1 (2026.02.24)

      ## New Features
      * Basic technical documentation and project plans.
    """.trimIndent()

    val entries = ReleaseNotesParser.parse(content)

    assertThat(entries.map { it.version }).containsExactly("0.1.0", "0.0.1")
    assertThat(entries[1].features).containsExactly("Basic technical documentation and project plans.")
  }

  @Test
  fun `handles version entries without a blank line before the category header`() {
    val content = """
      # 0.1.5 (2026.02.25)
      ## Bugfixes / Chore
      * Fixed build version not being shown in the UI in dev mode.

      ---

    """.trimIndent()

    val entries = ReleaseNotesParser.parse(content)

    val entry = entries.single()
    assertThat(entry.version).isEqualTo("0.1.5")
    assertThat(entry.bugfixes).containsExactly("Fixed build version not being shown in the UI in dev mode.")
  }

  @Test
  fun `groups consecutive entries with the same minor version and merges their bullets`() {
    val entries = listOf(
      ReleaseNotesEntry(
        version = "0.107.16",
        date = "2026.07.10",
        highlights = emptyList(),
        breakingChanges = emptyList(),
        features = emptyList(),
        bugfixes = listOf("Renamed a dashboard row.", "Unified log format."),
      ),
      ReleaseNotesEntry(
        version = "0.107.15",
        date = "2026.07.10",
        highlights = emptyList(),
        breakingChanges = emptyList(),
        features = emptyList(),
        bugfixes = listOf("Fixed a slow database query."),
      ),
      ReleaseNotesEntry(
        version = "0.106.3",
        date = "2026.07.08",
        highlights = emptyList(),
        breakingChanges = emptyList(),
        features = listOf("Added a new dashboard panel."),
        bugfixes = emptyList(),
      ),
    )

    val groups = ReleaseNotesParser.groupByMinorVersion(entries)

    assertThat(groups).hasSize(2)

    val newest = groups[0]
    assertThat(newest.minorVersion).isEqualTo("0.107")
    assertThat(newest.versions).containsExactly("0.107.16", "0.107.15")
    assertThat(newest.fromDate).isEqualTo("2026.07.10")
    assertThat(newest.toDate).isEqualTo("2026.07.10")
    assertThat(newest.bugfixes).containsExactly("Renamed a dashboard row.", "Unified log format.", "Fixed a slow database query.")

    val older = groups[1]
    assertThat(older.minorVersion).isEqualTo("0.106")
    assertThat(older.versions).containsExactly("0.106.3")
    assertThat(older.features).containsExactly("Added a new dashboard panel.")
  }

  @Test
  fun `does not merge non-consecutive entries that share a minor version`() {
    val entries = listOf(
      ReleaseNotesEntry("0.107.2", "2026.07.08", emptyList(), emptyList(), emptyList(), listOf("First fix.")),
      ReleaseNotesEntry("0.106.5", "2026.07.07", emptyList(), emptyList(), emptyList(), listOf("Unrelated fix.")),
      ReleaseNotesEntry("0.107.1", "2026.07.06", emptyList(), emptyList(), emptyList(), listOf("Second fix.")),
    )

    val groups = ReleaseNotesParser.groupByMinorVersion(entries)

    assertThat(groups.map { it.minorVersion }).containsExactly("0.107", "0.106", "0.107")
  }

  @Test
  fun `parses the real release notes file without losing any entries or bullets`() {
    val content = DocsUtils.readMarkdown("docs/releasenotes/RELEASENOTES.md")
    assertThat(content).isNotNull()

    val entries = ReleaseNotesParser.parse(content!!)

    val expectedVersionCount = content.lines().count { versionHeaderPattern.matches(it.trim()) }
    assertThat(entries).hasSize(expectedVersionCount)

    val knownHeaders = setOf("Bugfixes / Chore", "New Features", "Breaking Changes")
    val expectedContentLineCount = content.lines().count { line ->
      val trimmed = line.trim()
      trimmed.isNotEmpty() &&
        trimmed != "---" &&
        !versionHeaderPattern.matches(trimmed) &&
        !(trimmed.startsWith("## ") && trimmed.removePrefix("## ").trim() in knownHeaders)
    }
    val actualContentLineCount = entries.sumOf { it.highlights.size + it.breakingChanges.size + it.features.size + it.bugfixes.size }
    assertThat(actualContentLineCount).isEqualTo(expectedContentLineCount)

    val groups = ReleaseNotesParser.groupByMinorVersion(entries)
    assertThat(groups.flatMap { it.versions }).isEqualTo(entries.map { it.version })
  }

  companion object {
    private val versionHeaderPattern = Regex("""^#\s+(\S+)\s+\((\S+)\)\s*$""")
  }
}
