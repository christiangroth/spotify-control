package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.spotify.control.util.starters.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class HelloWorldStarter : Starter {

    override val id = "HelloWorldStarter-v1"

    override fun execute() {
        logger.info { "Hello World" }
    }

    companion object : KLogging()
}
