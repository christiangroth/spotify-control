package de.chrgroth.spotify.control.util.outbox

import io.quarkus.mongodb.panache.common.MongoEntity
import io.quarkus.mongodb.panache.kotlin.PanacheMongoCompanionBase
import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntityBase
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "outbox_archive")
class OutboxArchiveDocument : PanacheMongoEntityBase() {

    companion object : PanacheMongoCompanionBase<OutboxArchiveDocument, String>

    @BsonId
    lateinit var id: String
    lateinit var partition: String
    lateinit var eventType: String
    lateinit var deduplicationKey: String
    lateinit var payload: String
    lateinit var status: String
    var attempts: Int = 0
    lateinit var createdAt: Instant
    lateinit var updatedAt: Instant
    var nextRetryAt: Instant? = null
    lateinit var priority: String
    var lastError: String? = null
    lateinit var completedAt: Instant
}
