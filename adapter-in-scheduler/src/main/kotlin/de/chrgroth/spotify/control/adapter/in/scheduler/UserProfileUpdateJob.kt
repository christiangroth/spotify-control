package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.port.`in`.user.UserProfilePort
import de.chrgroth.quarkus.starters.domain.ScheduledSkipPredicate
import io.micrometer.core.annotation.Timed
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class UserProfileUpdateJob(
  private val userProfile: UserProfilePort,
) {

  @Timed(value = "scheduler.job", extraTags = ["invoker", "UserProfileUpdateJob"], histogram = true)
  @Scheduled(cron = "0 0 4 * * ?", skipExecutionIf = ScheduledSkipPredicate::class)
  fun run() {
    logger.info { "Running scheduled user profile update" }
    userProfile.enqueueUpdates()
  }

  companion object : KLogging()
}
