package de.chrgroth.spotify.control.adapter.`in`.http.frontend.i18n

import java.util.Locale

/**
 * Supported UI languages. [UNDERSCORE] is an artificial pseudo-locale generated at build time from the English properties
 * (see `generatePseudoLocaleMessages` in `adapter-in-http-frontend/build.gradle.kts`) - every non-whitespace character is
 * replaced with `__` while whitespace is preserved. It exists purely to make missing i18n placeholders in templates
 * visually obvious during UI testing.
 */
enum class Language(val code: String, val locale: Locale) {
  ENGLISH("en", Locale.ENGLISH),
  UNDERSCORE("xx", Locale.forLanguageTag("xx")),
  ;

  companion object {
    const val COOKIE_NAME = "lang"

    fun fromCode(code: String?): Language = entries.find { it.code == code } ?: ENGLISH
  }
}
