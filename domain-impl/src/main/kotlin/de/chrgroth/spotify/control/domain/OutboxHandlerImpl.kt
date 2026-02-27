package de.chrgroth.spotify.control.domain

import arrow.core.Either
import de.chrgroth.spotify.control.domain.outbox.OutboxError
import de.chrgroth.spotify.control.domain.outbox.PollRecentlyPlayedPayload
import de.chrgroth.spotify.control.domain.port.out.OutboxHandlerPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
class OutboxHandlerImpl : OutboxHandlerPort {

    override fun handlePollRecentlyPlayed(payload: PollRecentlyPlayedPayload): Either<OutboxError, Unit> {
        logger.info { "Handling PollRecentlyPlayed (version=${payload.version})" }
        return Either.Right(Unit)
    }

    companion object : KLogging()
}
