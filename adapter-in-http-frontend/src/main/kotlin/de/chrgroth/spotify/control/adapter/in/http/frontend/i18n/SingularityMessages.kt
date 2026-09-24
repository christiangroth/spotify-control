package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the singularity challenger workflow (`templates/singularity-challengers.html`) and
 * the challenger accept/discard endpoints it calls. Shared/reusable app-shell strings live in [AppMessages] instead.
 */
@MessageBundle("singularity")
interface SingularityMessages {

  @Message
  fun challengersTitle(): String

  @Message
  fun challengersEmptyState(): String

  @Message
  fun challengersCurrentTrackLabel(): String

  @Message
  fun challengersAcceptButton(): String

  @Message
  fun challengersDiscardButton(): String

  @Message("Accept '{trackTitle}' for {artistName}")
  fun challengersAcceptAriaLabel(trackTitle: String, artistName: String): String

  @Message("Discard '{trackTitle}' for {artistName}")
  fun challengersDiscardAriaLabel(trackTitle: String, artistName: String): String

  @Message
  fun challengersAcceptSuccess(): String

  @Message
  fun challengersDiscardSuccess(): String

  @Message
  fun challengersAcceptErrorPrefix(): String

  @Message
  fun challengersDiscardErrorPrefix(): String
}
