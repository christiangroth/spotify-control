package de.chrgroth.spotify.control.domain.port.`in`

import de.chrgroth.spotify.control.domain.model.UserId

interface UserProfileUpdatePort {
    fun updateUserProfiles()
    fun updateUserProfile(userId: UserId)
}
