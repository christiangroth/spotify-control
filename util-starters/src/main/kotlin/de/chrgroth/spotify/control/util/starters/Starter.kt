package de.chrgroth.spotify.control.util.starters

interface Starter {
    val id: String
    fun execute()
}
