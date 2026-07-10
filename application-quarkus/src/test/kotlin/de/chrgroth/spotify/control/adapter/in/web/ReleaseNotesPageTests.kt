package de.chrgroth.spotify.control.adapter.`in`.web

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test

@QuarkusTest
@TestSecurity(user = "test-user-a")
class ReleaseNotesPageTests {

  @Test
  fun `release notes page is available and renders a grouped accordion`() {
    given()
      .`when`()
      .get("/docs/releasenotes/RELEASENOTES.md")
      .then()
      .statusCode(200)
      .contentType(containsString("text/html"))
      .body(containsString("Release Notes"))
      .body(containsString("accordion-item"))
      .body(containsString("release-notes-raw"))
  }

  @Test
  fun `newest 3 minor version groups are expanded, older groups are collapsed`() {
    val groups = groupsFromRealFile()
    assertThat(groups.size).isGreaterThan(3)

    val body = renderedBody()

    groups.take(3).forEach { group ->
      val groupId = groupId(group)
      assertThat(body).contains("""id="$groupId-collapse" class="accordion-collapse collapse show"""")
      assertThat(body).contains("""aria-expanded="true" aria-controls="$groupId-collapse"""")
    }

    val olderGroupId = groupId(groups[3])
    assertThat(body).contains("""id="$olderGroupId-collapse" class="accordion-collapse collapse """")
    assertThat(body).contains("""aria-expanded="false" aria-controls="$olderGroupId-collapse"""")
  }

  @Test
  fun `page contains marked rendering pipeline for every group`() {
    val groups = groupsFromRealFile()
    val body = renderedBody()

    assertThat(body).contains("marked.parse(")
    assertThat(body.indexOf("marked.umd.js")).isLessThan(body.indexOf("marked.parse("))
    groups.forEach { group ->
      assertThat(body).contains("""data-group="${groupId(group)}"""")
    }
  }

  @Test
  fun `no bullet content is lost when grouping entries by minor version`() {
    val entries = ReleaseNotesParser.parse(readRealReleaseNotes())
    val groups = ReleaseNotesParser.groupByMinorVersion(entries)

    val entryBulletCount = entries.sumOf { it.highlights.size + it.breakingChanges.size + it.features.size + it.bugfixes.size }
    val groupBulletCount = groups.sumOf { it.highlights.size + it.breakingChanges.size + it.features.size + it.bugfixes.size }
    assertThat(groupBulletCount).isEqualTo(entryBulletCount)
  }

  private fun groupsFromRealFile(): List<ReleaseNotesMinorGroup> =
    ReleaseNotesParser.groupByMinorVersion(ReleaseNotesParser.parse(readRealReleaseNotes()))

  private fun readRealReleaseNotes(): String {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("docs/releasenotes/RELEASENOTES.md")
    assertThat(stream).isNotNull()
    return stream!!.bufferedReader(Charsets.UTF_8).readText()
  }

  private fun renderedBody(): String =
    given()
      .`when`()
      .get("/docs/releasenotes/RELEASENOTES.md")
      .then()
      .statusCode(200)
      .extract()
      .body()
      .asString()

  private fun groupId(group: ReleaseNotesMinorGroup): String = "release-notes-group-${group.minorVersion.replace(".", "-")}"
}
