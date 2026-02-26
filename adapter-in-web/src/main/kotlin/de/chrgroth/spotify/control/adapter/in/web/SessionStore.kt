package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.UserId
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
@Suppress("Unused")
class SessionStore {

    private val sessions = ConcurrentHashMap<String, UserId>()

    fun createSession(userId: UserId): String {
        val id = UUID.randomUUID().toString()
        sessions[id] = userId
        return id
    }

    fun getUser(sessionId: String): UserId? = sessions[sessionId]

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }
}
