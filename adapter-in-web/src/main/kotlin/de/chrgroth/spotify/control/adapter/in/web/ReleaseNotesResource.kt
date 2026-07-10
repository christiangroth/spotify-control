package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

data class ReleaseNotesGroupView(
  val id: String,
  val title: String,
  val dateRange: String,
  val versionsLabel: String,
  val markdownContent: String,
  val expanded: Boolean,
)

@Path("/docs/releasenotes/RELEASENOTES.md")
@ApplicationScoped
@Suppress("Unused")
class ReleaseNotesResource(
  @param:Location("release-notes.html")
  private val releaseNotesTemplate: Template,
) {

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  fun releaseNotes(): TemplateInstance {
    val content = DocsUtils.readMarkdown("docs/releasenotes/RELEASENOTES.md")
      ?: throw NotFoundException("Release notes not found")
    val groups = ReleaseNotesParser.groupByMinorVersion(ReleaseNotesParser.parse(content))
    return releaseNotesTemplate.instance()
      .data("groups", groups.mapIndexed { index, group -> toView(group, expanded = index < 3) })
  }

  private fun toView(group: ReleaseNotesMinorGroup, expanded: Boolean): ReleaseNotesGroupView {
    val dateRange = if (group.fromDate == group.toDate) group.toDate else "${group.fromDate} – ${group.toDate}"
    val versionCount = group.versions.size
    return ReleaseNotesGroupView(
      id = "release-notes-group-${group.minorVersion.replace(".", "-")}",
      title = group.minorVersion,
      dateRange = dateRange,
      versionsLabel = "$versionCount version${if (versionCount == 1) "" else "s"}: ${group.versions.joinToString(", ")}",
      markdownContent = toMarkdown(group),
      expanded = expanded,
    )
  }

  private fun toMarkdown(group: ReleaseNotesMinorGroup): String {
    val sections = mutableListOf<String>()
    if (group.highlights.isNotEmpty()) sections.add(group.highlights.joinToString("\n") { "* $it" })
    if (group.breakingChanges.isNotEmpty()) sections.add("### Breaking Changes\n" + group.breakingChanges.joinToString("\n") { "* $it" })
    if (group.features.isNotEmpty()) sections.add("### New Features\n" + group.features.joinToString("\n") { "* $it" })
    if (group.bugfixes.isNotEmpty()) sections.add("### Bugfixes / Chore\n" + group.bugfixes.joinToString("\n") { "* $it" })
    return sections.joinToString("\n\n")
  }
}
