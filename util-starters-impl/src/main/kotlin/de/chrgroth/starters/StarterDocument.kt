package de.chrgroth.starters

import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId

@MongoEntity(collection = "starters")
class StarterDocument {

    @BsonId
    lateinit var starterId: String
    lateinit var lastStatus: String
    var executions: List<StarterExecutionDocument> = emptyList()
}
