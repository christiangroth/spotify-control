package de.chrgroth.spotify.control.adapter.`in`.web

internal object DocsUtils {

  fun readMarkdown(resourcePath: String): String? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
      ?: return null
    return stream.bufferedReader(Charsets.UTF_8).readText()
  }

  fun extractTitle(content: String, fallback: String): String =
    content.lineSequence()
      .firstOrNull { it.startsWith("# ") }
      ?.removePrefix("# ")
      ?.trim()
      ?: fallback

  fun stripFirstHeading(content: String): String {
    val lines = content.lines()
    val idx = lines.indexOfFirst { it.startsWith("# ") }
    return if (idx >= 0) {
      lines.drop(idx + 1).dropWhile { it.isBlank() }.joinToString("\n")
    } else {
      content
    }
  }
}
