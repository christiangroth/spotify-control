package de.chrgroth.spotify.control.domain.port.out.user

interface AuthNotificationPort {
  fun notifyTokenRefreshFailed()
}
