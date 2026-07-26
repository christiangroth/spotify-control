package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings specific to the health and logs pages (`templates/health.html`, `templates/logs-viewer.html`). Shared/reusable
 * app-shell strings live in [AppMessages] instead.
 */
@MessageBundle("health")
interface HealthMessages {

  // health page
  @Message
  fun healthTitle(): String

  @Message
  fun healthHeading(): String

  @Message
  fun healthCommunicationHeading(): String

  @Message
  fun healthOutgoingRequestsHeading(): String

  @Message
  fun healthColumnEndpoint(): String

  @Message
  fun healthColumnCount(): String

  @Message
  fun healthOutboxPartitionsHeading(): String

  @Message
  fun healthColumnPartition(): String

  @Message
  fun healthColumnBlocked(): String

  @Message
  fun healthCronjobsStateHeading(): String

  @Message
  fun healthCronjobsHeading(): String

  @Message
  fun healthColumnJob(): String

  @Message
  fun healthColumnCron(): String

  @Message
  fun healthColumnNext(): String

  @Message
  fun healthStateHeading(): String

  @Message
  fun healthColumnPredicate(): String

  @Message
  fun healthColumnSince(): String

  @Message
  fun healthMongodbHeading(): String

  @Message
  fun healthCollectionsHeading(): String

  @Message
  fun healthColumnCollection(): String

  @Message
  fun healthColumnSize(): String

  @Message
  fun healthTotalLabel(): String

  @Message("{sizeKbFormatted} kb")
  fun healthSizeKb(sizeKbFormatted: String): String

  @Message
  fun healthQueriesHeading(): String

  @Message
  fun healthColumnQuery(): String

  @Message
  fun healthColumnExecutions(): String

  // logs page
  @Message
  fun logsTitle(): String

  @Message
  fun logsHeading(): String

  @Message
  fun logsViewModeAriaLabel(): String

  @Message
  fun logsViewChronological(): String

  @Message
  fun logsViewGrouped(): String

  @Message
  fun logsEmpty(): String

  @Message
  fun logsGroupedHint(): String

  @Message
  fun logsChronologicalHint(): String

  @Message
  fun logsColumnTime(): String

  @Message
  fun logsColumnMessage(): String

  @Message
  fun logsColumnStacktrace(): String

  @Message
  fun logsColumnLevel(): String

  @Message
  fun logsColumnClass(): String

  @Message
  fun logsStacktraceShow(): String

  @Message
  fun logsBadgeWarn(): String

  @Message
  fun logsBadgeError(): String
}
