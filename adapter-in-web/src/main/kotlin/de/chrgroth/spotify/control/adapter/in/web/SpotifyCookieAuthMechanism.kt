package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.UserId
import de.chrgroth.spotify.control.domain.port.out.TokenEncryptionPort
import de.chrgroth.spotify.control.domain.port.out.UserRepositoryPort
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.security.Principal
import java.util.Optional

@ApplicationScoped
@Suppress("Unused")
class SpotifyCookieAuthMechanism(
    private val tokenEncryption: TokenEncryptionPort,
    private val userRepository: UserRepositoryPort,
) : HttpAuthenticationMechanism {

    override fun authenticate(context: RoutingContext, identityProviderManager: IdentityProviderManager): Uni<SecurityIdentity> {
        val cookieValue = context.request().getCookie(COOKIE_NAME)?.value
            ?: return Uni.createFrom().optional(Optional.empty())
        return try {
            val userId = UserId(tokenEncryption.decrypt(cookieValue))
            if (userRepository.findById(userId) == null) {
                logger.warn { "Authentication failed: user not found: ${userId.value}" }
                return Uni.createFrom().optional(Optional.empty())
            }
            logger.info { "User authenticated: ${userId.value}" }
            val identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(Principal { userId.value })
                .setAnonymous(false)
                .build()
            Uni.createFrom().item(identity)
        } catch (_: Exception) {
            logger.warn { "Authentication failed: invalid session cookie" }
            Uni.createFrom().optional(Optional.empty())
        }
    }

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().item(ChallengeData(REDIRECT_STATUS, "Location", "/"))

    companion object : KLogging() {
        const val COOKIE_NAME = "spotify-session"
        private const val REDIRECT_STATUS = 307
    }
}
