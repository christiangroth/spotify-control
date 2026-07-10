package de.chrgroth.spotify.control.adapter.`in`.web

data class ReleaseNotesEntry(
  val version: String,
  val date: String,
  val highlights: List<String>,
  val breakingChanges: List<String>,
  val features: List<String>,
  val bugfixes: List<String>,
)

data class ReleaseNotesMinorGroup(
  val minorVersion: String,
  val versions: List<String>,
  val fromDate: String,
  val toDate: String,
  val highlights: List<String>,
  val breakingChanges: List<String>,
  val features: List<String>,
  val bugfixes: List<String>,
)

object ReleaseNotesParser {

  private const val BREAKING_CHANGES_HEADER = "Breaking Changes"
  private const val NEW_FEATURES_HEADER = "New Features"
  private const val BUGFIXES_HEADER = "Bugfixes / Chore"

  private val versionHeaderRegex = Regex("""^#\s+(\S+)\s+\((\S+)\)\s*$""")

  fun parse(content: String): List<ReleaseNotesEntry> {
    val lines = content.lines()
    val entries = mutableListOf<ReleaseNotesEntry>()

    var index = 0
    while (index < lines.size) {
      val headerMatch = versionHeaderRegex.matchEntire(lines[index].trim())
      if (headerMatch == null) {
        index++
        continue
      }
      val version = headerMatch.groupValues[1]
      val date = headerMatch.groupValues[2]
      index++

      val bodyLines = mutableListOf<String>()
      while (index < lines.size && lines[index].trim() != "---" && versionHeaderRegex.matchEntire(lines[index].trim()) == null) {
        bodyLines.add(lines[index])
        index++
      }
      if (index < lines.size && lines[index].trim() == "---") {
        index++
      }

      entries.add(toEntry(version, date, bodyLines))
    }

    return entries
  }

  fun groupByMinorVersion(entries: List<ReleaseNotesEntry>): List<ReleaseNotesMinorGroup> {
    val groups = mutableListOf<MutableList<ReleaseNotesEntry>>()
    for (entry in entries) {
      val currentGroup = groups.lastOrNull()
      if (currentGroup != null && minorVersionOf(currentGroup.last().version) == minorVersionOf(entry.version)) {
        currentGroup.add(entry)
      } else {
        groups.add(mutableListOf(entry))
      }
    }

    return groups.map { group ->
      ReleaseNotesMinorGroup(
        minorVersion = minorVersionOf(group.first().version),
        versions = group.map { it.version },
        fromDate = group.minOf { it.date },
        toDate = group.maxOf { it.date },
        highlights = group.flatMap { it.highlights },
        breakingChanges = group.flatMap { it.breakingChanges },
        features = group.flatMap { it.features },
        bugfixes = group.flatMap { it.bugfixes },
      )
    }
  }

  private fun toEntry(version: String, date: String, bodyLines: List<String>): ReleaseNotesEntry {
    val highlights = mutableListOf<String>()
    val breakingChanges = mutableListOf<String>()
    val features = mutableListOf<String>()
    val bugfixes = mutableListOf<String>()

    var currentBucket = highlights
    for (line in bodyLines) {
      val trimmed = line.trim()
      when {
        trimmed.isEmpty() -> Unit
        trimmed.startsWith("## ") -> currentBucket = bucketFor(trimmed.removePrefix("## ").trim(), highlights, breakingChanges, features, bugfixes)
        trimmed.startsWith("* ") -> currentBucket.add(trimmed.removePrefix("* ").trim())
        else -> currentBucket.add(trimmed)
      }
    }

    return ReleaseNotesEntry(
      version = version,
      date = date,
      highlights = highlights,
      breakingChanges = breakingChanges,
      features = features,
      bugfixes = bugfixes,
    )
  }

  private fun bucketFor(
    header: String,
    highlights: MutableList<String>,
    breakingChanges: MutableList<String>,
    features: MutableList<String>,
    bugfixes: MutableList<String>,
  ): MutableList<String> = when (header) {
    BREAKING_CHANGES_HEADER -> breakingChanges
    NEW_FEATURES_HEADER -> features
    BUGFIXES_HEADER -> bugfixes
    else -> {
      highlights.add(header)
      highlights
    }
  }

  private fun minorVersionOf(version: String): String = version.split(".").take(2).joinToString(".")
}
