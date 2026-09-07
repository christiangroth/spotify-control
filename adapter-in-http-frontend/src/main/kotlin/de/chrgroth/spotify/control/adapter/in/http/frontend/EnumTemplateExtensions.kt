package de.chrgroth.spotify.control.adapter.`in`.http.frontend

import io.quarkus.qute.TemplateExtension

// `name` is inherited from `java.lang.Enum`, not declared on the enum class itself, so @TemplateData (which only
// covers members declared on the annotated class/indexed supertypes) can't expose it - this generic extension covers
// every enum used in a template without per-enum registration.
@TemplateExtension
@Suppress("Unused")
object EnumTemplateExtensions {

  @JvmStatic
  fun name(value: Enum<*>): String = value.name
}
