package de.chrgroth.spotify.control.domain

import de.chrgroth.spotify.control.domain.port.`in`.SampleUsecasePort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class SampleUsecaseAdapter : SampleUsecasePort {

  override fun sayHello(): String {
    return "Hello!".also {
      logger.info(it)
    }
  }

  companion object : KLogging()
}
