package de.chrgroth.spotify.control.adapter.out.slack

import de.chrgroth.outbox.OutboxPartition
import de.chrgroth.outbox.OutboxPartitionObserver
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Sends system notifications to a configured Slack webhook.
 *
 * Two categories of notifications are supported:
 * - **System notifications** – lifecycle and infrastructure events. Notification toggles are
 *   configured via application properties; the webhook URL is set via the `SLACK_WEBHOOK_URL`
 *   environment variable in production (this class).
 * - **User notifications** – user-facing alerts configured through the UI (postponed).
 */
@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SlackNotificationAdapter(
    @param:ConfigProperty(name = "quarkus.application.version")
    private val version: String,
    @param:ConfigProperty(name = "app.slack.webhook-url", defaultValue = "")
    private val webhookUrl: String,
    @param:ConfigProperty(name = "app.slack.system-notifications.startup", defaultValue = "false")
    private val startupEnabled: Boolean,
    @param:ConfigProperty(name = "app.slack.system-notifications.stopping", defaultValue = "false")
    private val stoppingEnabled: Boolean,
    @param:ConfigProperty(name = "app.slack.system-notifications.outbox-partition-paused", defaultValue = "false")
    private val partitionPausedEnabled: Boolean,
    @param:ConfigProperty(name = "app.slack.system-notifications.outbox-partition-resumed", defaultValue = "false")
    private val partitionResumedEnabled: Boolean,
) : OutboxPartitionObserver {

    @Suppress("UnusedParameter")
    fun onStartup(@Observes event: StartupEvent) {
        if (startupEnabled) send("SpCtl $version started")
    }

    @Suppress("UnusedParameter")
    fun onShutdown(@Observes event: ShutdownEvent) {
        if (stoppingEnabled) send("SpCtl $version about to stop")
    }

    override fun onPartitionPaused(partition: OutboxPartition) {
        if (partitionPausedEnabled) send("Outbox partition ${partition.key} paused/rate Limited")
    }

    override fun onPartitionActivated(partition: OutboxPartition) {
        if (partitionResumedEnabled) send("Outbox partition ${partition.key} resumed")
    }

    private fun send(text: String) {
        if (webhookUrl.isBlank()) {
            logger.debug { "Slack webhook URL not configured, skipping notification: $text" }
            return
        }
        try {
            val body = """{"text": ${toJsonString(text)}}"""
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != HTTP_OK) {
                logger.warn { "Slack notification failed with status ${response.statusCode()}: ${response.body()}" }
            } else {
                logger.info { "Slack notification sent: $text" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send Slack notification: $text" }
        }
    }

    private fun toJsonString(value: String): String = buildString {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

    companion object : KLogging() {
        private const val HTTP_OK = 200
        private val httpClient: HttpClient = HttpClient.newHttpClient()
    }
}
