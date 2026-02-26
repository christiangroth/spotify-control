package de.chrgroth.spotify.control.adapter.`in`.web

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import java.net.URI

@Provider
@ApplicationScoped
@Suppress("Unused")
class AuthFilter : ContainerRequestFilter {

    @Inject
    private lateinit var sessionStore: SessionStore

    private val protectedPrefixes = setOf("/ui/", "/api/")

    override fun filter(ctx: ContainerRequestContext) {
        val path = "/" + ctx.uriInfo.path.trimStart('/')
        if (protectedPrefixes.none { path.startsWith(it) }) return

        val sessionId = ctx.cookies["spotify-session"]?.value
        if (sessionId == null || sessionStore.getUser(sessionId) == null) {
            ctx.abortWith(Response.temporaryRedirect(URI.create("/")).build())
        }
    }
}
