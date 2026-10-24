package de.chrgroth.spotify.control.adapter.out.spotify

internal const val HTTP_OK = 200
internal const val HTTP_NO_CONTENT = 204
internal const val HTTP_CREATED = 201
internal const val HTTP_BAD_REQUEST = 400
internal const val HTTP_RATE_LIMITED = 429
internal const val DEFAULT_RETRY_AFTER_SECONDS = 60L

// Spotify sometimes answers 429 with "Retry-After: 0" right when a long ban expires; taking that literally paused the
// partition until "now", resumed it immediately and looped pause/resume (plus Slack notifications) within a second.
internal const val MIN_RETRY_AFTER_SECONDS = 30L

internal fun String.queryParamInt(name: String): Int? =
  substringAfter('?', "").split('&')
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')
    ?.toIntOrNull()

internal fun String.queryParamLong(name: String): Long? =
  substringAfter('?', "").split('&')
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')
    ?.toLongOrNull()

