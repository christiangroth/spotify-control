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

  // api tokens section
  @Message
  fun configApiTokensSectionHeading(): String

  @Message
  fun configApiTokensDescription(): String

  @Message
  fun configApiTokensScopesDescription(): String

  @Message
  fun configApiTokensEmpty(): String

  @Message
  fun configApiTokenNameHeader(): String

  @Message
  fun configApiTokenPrefixHeader(): String

  @Message
  fun configApiTokenScopesHeader(): String

  @Message
  fun configApiTokenActiveHeader(): String

  @Message
  fun configApiTokenCreatedHeader(): String

  @Message
  fun configApiTokenLastUsedHeader(): String

  @Message
  fun configApiTokenNeverUsed(): String

  @Message
  fun configApiTokenNameLabel(): String

  @Message
  fun configApiTokenCreateButton(): String

  @Message
  fun configApiTokenDeleteButton(): String

  @Message
  fun configApiTokenDeleteConfirm(): String

  @Message
  fun configApiTokenCreatedNotice(): String

  @Message
  fun configApiTokenUpdatedSuccess(): String

  @Message
  fun configApiTokenRequestFailedPrefix(): String

  @Message
  fun configApiTokenInvalidScopeError(): String

  @Message
  fun configApiTokenNotFoundError(): String

  @Message
  fun configApiTokenNameRequiredError(): String

  @Message
  fun configApiTokenScopeRequiredError(): String
}
