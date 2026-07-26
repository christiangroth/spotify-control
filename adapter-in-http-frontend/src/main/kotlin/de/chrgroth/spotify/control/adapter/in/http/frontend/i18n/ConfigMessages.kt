package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * UI strings for the config page (`templates/config.html`) and its backing resources (`ConfigResource`, `RuntimeConfigResource`).
 */
@MessageBundle("config")
interface ConfigMessages {

  @Message
  fun configTitle(): String

  @Message
  fun configHeading(): String

  // runtime config section
  @Message
  fun configRuntimeSectionHeading(): String

  @Message
  fun configRuntimeTransientNoteBefore(): String

  @Message
  fun configRuntimeTransientWord(): String

  @Message
  fun configRuntimeTransientNoteAfter(): String

  @Message
  fun configThrottlingCardTitle(): String

  @Message
  fun configThrottleDescription(): String

  @Message
  fun configThrottleDefaultLabel(): String

  @Message
  fun configThrottleSecondsUnit(): String

  @Message
  fun configThrottleIntervalLabel(): String

  @Message
  fun configThrottleSaveButton(): String

  @Message
  fun configThrottleResetButton(): String

  @Message
  fun configThrottleValidationError(): String

  @Message("Throttle interval updated to {seconds} s.")
  fun configThrottleUpdatedSuccess(seconds: String): String

  @Message("Throttle interval reset to default ({seconds} s).")
  fun configThrottleResetSuccess(seconds: String): String

  @Message
  fun configThrottleUpdateFailedPrefix(): String

  @Message
  fun configThrottleNonNegativeError(): String

  // environment / config sections
  @Message
  fun configEnvironmentSectionTitle(): String

  @Message
  fun configTableKeyHeader(): String

  @Message
  fun configTableValueHeader(): String
}
