package de.chrgroth.spotify.control.domain.model

data class MongoViewerField(
    val name: String,
    val fieldType: MongoViewerFieldType,
    val containsValue: String = "",
    val equalsValue: String = "",
    val inValue: String = "",
    val notInValue: String = "",
)
