package de.chrgroth.starters

import io.quarkus.runtime.LaunchMode
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "UnusedParameter")
class StarterStartup(
    private val starterService: StarterService,
    private val completionFlag: StarterCompletionFlag,
) {

    fun onStart(@Observes event: StartupEvent) {
        if (LaunchMode.current() != LaunchMode.NORMAL) {
            logger.info { "Skipping starters – not in NORMAL (prod) mode. Marking completion flag immediately." }
            completionFlag.markCompleted()
            return
        }
        val allSucceeded = starterService.runAll()
        if (allSucceeded) {
            completionFlag.markCompleted()
            logger.info { "All starters succeeded – scheduler unblocked." }
        } else {
            logger.warn { "Some starters failed – scheduler remains blocked until next application start." }
        }
    }

    companion object : KLogging()
}
