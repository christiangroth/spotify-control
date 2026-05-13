package de.chrgroth.spotify.control.adapter.out.spotify

import de.chrgroth.spotify.control.domain.model.user.AccessToken

internal object SpotifyApiAuthContext {

  private val holder = ThreadLocal<String?>()

  internal fun set(accessToken: AccessToken) {
    holder.set("Bearer ${accessToken.value}")
  }

  internal fun get(): String = holder.get() ?: error("No Spotify access token set for current thread")

  internal fun clear() = holder.remove()
}
