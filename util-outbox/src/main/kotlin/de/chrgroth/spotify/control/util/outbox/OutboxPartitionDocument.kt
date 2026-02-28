package de.chrgroth.spotify.control.util.outbox

import io.quarkus.mongodb.panache.common.MongoEntity
import io.quarkus.mongodb.panache.kotlin.PanacheMongoCompanionBase
import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntityBase
import org.bson.codecs.pojo.annotations.BsonId
import java.time.Instant

@MongoEntity(collection = "outbox_partitions")
class OutboxPartitionDocument : PanacheMongoEntityBase() {

    companion object : PanacheMongoCompanionBase<OutboxPartitionDocument, String>

    @BsonId
    lateinit var partitionKey: String
    lateinit var status: String
    var statusReason: String? = null
    var pausedUntil: Instant? = null
}
