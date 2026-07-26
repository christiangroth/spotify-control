package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings for the docs domain (arc42/ADR/coding-guidelines doc viewer and the release notes page). The
 * Markdown content rendered from the docs directory and `RELEASENOTES.md` is out of scope for this bundle -
 * only the template chrome around it (headings, labels) is covered here.
 */
@MessageBundle("docs")
interface DocsMessages {

  // release notes page
  @Message
  fun releaseNotesTitle(): String

  @Message
  fun releaseNotesVersionSingular(): String

  @Message
  fun releaseNotesVersionPlural(): String
}
