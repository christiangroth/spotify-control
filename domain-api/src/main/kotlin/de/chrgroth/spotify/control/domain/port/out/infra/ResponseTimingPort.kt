package de.chrgroth.spotify.control.domain.port.out.infra

interface ResponseTimingPort {
  fun <T> timed(operation: String, block: (ResponseTimingDetails) -> T): T
}

interface ResponseTimingDetails {
  fun <T> detail(name: String, block: () -> T): T

  suspend fun <T> detailSuspend(name: String, block: suspend () -> T): T
}
