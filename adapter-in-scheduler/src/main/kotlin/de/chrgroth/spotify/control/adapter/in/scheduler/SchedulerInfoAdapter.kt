package de.chrgroth.spotify.control.adapter.`in`.scheduler

import de.chrgroth.spotify.control.domain.model.CronjobStats
import de.chrgroth.spotify.control.domain.port.out.CronjobInfoPort
import io.quarkus.scheduler.Scheduled
import io.quarkus.scheduler.Scheduler
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

@ApplicationScoped
@Suppress("Unused")
class SchedulerInfoAdapter(
    private val scheduler: Scheduler,
) : CronjobInfoPort {

    override fun getCronjobStats(): List<CronjobStats> =
        scheduler.scheduledJobs
            .mapNotNull { trigger ->
                val id = trigger.id
                val hashIdx = id.lastIndexOf('#')
                if (hashIdx < 0) return@mapNotNull null
                val className = id.substring(0, hashIdx)
                val methodName = id.substring(hashIdx + 1)
                try {
                    val clazz = Class.forName(className)
                    val method = clazz.getDeclaredMethod(methodName)
                    val scheduled = method.getAnnotation(Scheduled::class.java) ?: return@mapNotNull null
                    CronjobStats(
                        simpleName = clazz.simpleName,
                        cronSchedule = scheduled.cron,
                        nextExecution = trigger.nextFireTime ?: Instant.now(),
                    )
                } catch (_: ReflectiveOperationException) {
                    null
                }
            }
            .sortedBy { it.simpleName }
            .toList()
}
