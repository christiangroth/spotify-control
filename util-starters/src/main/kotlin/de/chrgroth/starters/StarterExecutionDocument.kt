package de.chrgroth.starters

import java.time.Instant

class StarterExecutionDocument {
    lateinit var startedAt: Instant
    var finishedAt: Instant? = null
    lateinit var status: String
    var errorMessage: String? = null
}
