package de.chrgroth.spotify.control.adapter.out.spotify

import jakarta.annotation.Priority
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.ext.MessageBodyReader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.InputStream
import java.lang.reflect.Type
import kotlin.reflect.full.hasAnnotation

private const val READER_PRIORITY_OFFSET = 100

@Priority(Priorities.USER - READER_PRIORITY_OFFSET)
@Consumes(MediaType.APPLICATION_JSON)
internal class KotlinxSerializationReader : MessageBodyReader<Any> {

  private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  override fun isReadable(
    type: Class<*>,
    genericType: Type,
    annotations: Array<Annotation>,
    mediaType: MediaType,
  ): Boolean = type.kotlin.hasAnnotation<Serializable>()

  @Suppress("UNCHECKED_CAST")
  override fun readFrom(
    type: Class<Any>,
    genericType: Type,
    annotations: Array<Annotation>,
    mediaType: MediaType,
    httpHeaders: MultivaluedMap<String, String>,
    entityStream: InputStream,
  ): Any {
    val body = entityStream.bufferedReader().readText()
    val kSerializer = serializer(type)
    return json.decodeFromString(kSerializer, body)
  }
}
