package de.chrgroth.spotify.control.adapter.out.slack

import de.chrgroth.spotify.control.domain.model.playback.aggregation.PlaybackAggregation
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPartitionObserver
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxPort
import de.chrgroth.spotify.control.domain.port.out.infra.OutboxTaskFailedObserver
import de.chrgroth.spotify.control.domain.port.out.playback.PlaybackDigestNotificationPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistCheckNotificationPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistSyncNotificationPort
import de.chrgroth.spotify.control.domain.port.out.user.AuthNotificationPort
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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
  @param:ConfigProperty(name = "app.slack.webhook-url")
  private val webhookUrl: Optional<String>,
  @param:ConfigProperty(name = "app.slack.username")
  private val username: String,
  @param:ConfigProperty(name = "app.slack.icon-emoji")
  private val iconEmoji: String,
  @param:ConfigProperty(name = "app.slack.system-notifications.startup")
  private val startupEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.system-notifications.stopping")
  private val stoppingEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.system-notifications.outbox-partition-paused")
  private val partitionPausedEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.system-notifications.outbox-partition-resumed")
  private val partitionResumedEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.playlist-check-notifications.check-passed")
  private val checkPassedEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.playlist-check-notifications.violations-changed")
  private val violationsChangedEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.system-notifications.outbox-task-failed")
  private val taskFailedEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.auth-notifications.token-refresh-failed")
  private val tokenRefreshFailedEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.playlist-sync-notifications.sync-failed")
  private val syncFailedEnabled: Boolean,
  @param:ConfigProperty(name = "app.slack.playback-digest-notifications.weekly")
  private val weeklyDigestEnabled: Boolean,
  private val outboxPort: OutboxPort,
) : OutboxPartitionObserver,
  OutboxTaskFailedObserver,
  PlaylistCheckNotificationPort,
  AuthNotificationPort,
  PlaylistSyncNotificationPort,
  PlaybackDigestNotificationPort {

  private val enabled: Boolean = webhookUrl.orElse("").isNotBlank()

  // the outbox library re-announces every partition's recovered state via onPartitionPaused/onPartitionActivated
  // during application boot; individual notifications for those are suppressed until onStartup has run and instead
  // folded into the single startup summary below, so a restart doesn't cause one Slack message per partition
  private val startupCompleted = AtomicBoolean(false)

  init {
    if (enabled) {
      logger.info { "Slack notifications enabled (webhook URL configured)" }
    } else {
      logger.warn { "Slack notifications disabled (no webhook URL configured)" }
    }
  }

  @Suppress("UnusedParameter")
  fun onStartup(@Observes event: StartupEvent) {
    if (startupEnabled) send("SpCtl $version started")
    if (partitionPausedEnabled || partitionResumedEnabled) send(partitionSummary())
    startupCompleted.set(true)
  }

  @Suppress("UnusedParameter")
  fun onShutdown(@Observes event: ShutdownEvent) {
    if (stoppingEnabled) send("SpCtl $version about to stop")
  }

  override fun onPartitionPaused(partitionKey: String, reason: String) {
    if (!startupCompleted.get() || !partitionPausedEnabled) return
    val until = outboxPort.getPartitionStats().firstOrNull { it.name == partitionKey }?.blockedUntil
    val untilText = until?.let { ", paused until ${formatInstant(it)}" } ?: ""
    send("Outbox partition $partitionKey paused (reason: $reason)$untilText")
  }

  override fun onPartitionActivated(partitionKey: String) {
    if (startupCompleted.get() && partitionResumedEnabled) send("Outbox partition $partitionKey resumed")
  }

  private fun partitionSummary(): String {
    val stats = outboxPort.getPartitionStats()
    val inactive = stats.filterNot { it.status == ACTIVE_STATUS }
    return buildString {
      append("${stats.size - inactive.size}/${stats.size} outbox partitions active")
      if (inactive.isNotEmpty()) {
        val now = Clock.System.now()
        append(" (")
        append(
          inactive.joinToString(", ") { partition ->
            val until = partition.blockedUntil
            if (until != null && until > now) "${partition.name}: paused for ${formatRemaining(until - now)}" else partition.name
          }
        )
        append(")")
      }
    }
  }

  private fun formatRemaining(duration: Duration): String = duration.coerceAtLeast(Duration.ZERO).inWholeMinutes.minutes.toString()

  private fun formatInstant(instant: Instant): String = instantFormatter.format(instant.toJavaInstant())

  override fun notifyCheckPassed(check: AppPlaylistCheck, playlistName: String?) {
    if (checkPassedEnabled) {
      send("Playlist check passed for '${playlistName ?: check.playlistId.value}' (check: ${check.checkId})")
    }
  }

  override fun notifyViolationsChanged(check: AppPlaylistCheck, playlistName: String?, previousViolationCount: Int?) {
    if (!violationsChangedEnabled) return
    val name = playlistName ?: check.playlistId.value
    val count = check.violations.size
    val text = if (previousViolationCount == null) {
      "Playlist check failed for '$name' (check: ${check.checkId}): $count violation(s) found"
    } else {
      val delta = count - previousViolationCount
      val deltaText = if (delta > 0) "+$delta" else "$delta"
      "Playlist check violations changed for '$name' (check: ${check.checkId}): $previousViolationCount -> $count violation(s) ($deltaText)"
    }
    send(text)
  }

  override fun onTaskFailed(partitionKey: String, eventType: String) {
    if (taskFailedEnabled) send("Outbox task '$eventType' in partition $partitionKey permanently failed (all retries exhausted)")
  }

  override fun notifyTokenRefreshFailed() {
    if (tokenRefreshFailedEnabled) send("Spotify login invalid, please log in again")
  }

  override fun notifySyncFailed(reason: String) {
    if (syncFailedEnabled) send("Playlist sync failed (reason: $reason)")
  }

  override fun notifyWeeklyDigest(aggregation: PlaybackAggregation) {
    if (!weeklyDigestEnabled || aggregation.eventCount == 0L) return
    val minutes = aggregation.totalPlaybackSeconds / SECONDS_PER_MINUTE
    val topArtist = aggregation.artistEntries.firstOrNull()?.name
    val topTrack = aggregation.trackEntries.firstOrNull()?.name
    val text = buildString {
      append("Weekly listening digest: $minutes minute(s) played")
      if (topArtist != null) append(", top artist: $topArtist")
      if (topTrack != null) append(", top track: $topTrack")
    }
    send(text)
  }

  private fun send(text: String) {
    if (!enabled) return
    try {
      val body = buildString {
        append("""{"text": ${toJsonString(text)}""")
        if (username.isNotBlank()) append(""", "username": ${toJsonString(username)}""")
        if (iconEmoji.isNotBlank()) append(""", "icon_emoji": ${toJsonString(iconEmoji)}""")
        append("}")
      }
      val request = HttpRequest.newBuilder()
        .uri(URI.create(webhookUrl.get()))
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
    private const val SECONDS_PER_MINUTE = 60L
    private const val ACTIVE_STATUS = "ACTIVE"
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val instantFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
  }
}
