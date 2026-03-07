package de.chrgroth.starters

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import mu.KLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ApplicationScoped
@Suppress("TooGenericExceptionCaught")
class StarterService(
    private val starters: Instance<Starter>,
    private val repository: StarterDocumentRepository,
    private val meterRegistry: MeterRegistry,
) {

    private val statusGauges = ConcurrentHashMap<String, AtomicInteger>()

    fun runAll(): Boolean {
        val sortedStarters = starters.stream().toList().sortedBy { it.id }
        logger.info { "Running ${sortedStarters.size} starter(s)" }

        var allSucceeded = true
        for (starter in sortedStarters) {
            val existingDoc = repository.findById(starter.id)
            if (existingDoc?.lastStatus == StarterStatus.SUCCEEDED.name) {
                logger.info { "Skipping starter ${starter.id} – already SUCCEEDED" }
                updateGauge(starter.id, StarterStatus.SUCCEEDED)
                continue
            }

            logger.info { "Executing starter ${starter.id}" }
            val startedAt = Instant.now()
            val timerSample = Timer.start(meterRegistry)
            try {
                starter.execute()
                val finishedAt = Instant.now()
                val execution = StarterExecutionDocument().apply {
                    this.startedAt = startedAt
                    this.finishedAt = finishedAt
                    this.status = StarterStatus.SUCCEEDED.name
                }
                persist(starter.id, StarterStatus.SUCCEEDED, execution, existingDoc)
                timerSample.stop(
                    Timer.builder("starter_execution_duration_seconds")
                        .tag("id", starter.id)
                        .tag("status", StarterStatus.SUCCEEDED.name)
                        .register(meterRegistry),
                )
                updateGauge(starter.id, StarterStatus.SUCCEEDED)
                logger.info { "Starter ${starter.id} SUCCEEDED" }
            } catch (e: Exception) {
                val finishedAt = Instant.now()
                val execution = StarterExecutionDocument().apply {
                    this.startedAt = startedAt
                    this.finishedAt = finishedAt
                    this.status = StarterStatus.FAILED.name
                    this.errorMessage = e.message
                }
                persist(starter.id, StarterStatus.FAILED, execution, existingDoc)
                timerSample.stop(
                    Timer.builder("starter_execution_duration_seconds")
                        .tag("id", starter.id)
                        .tag("status", StarterStatus.FAILED.name)
                        .register(meterRegistry),
                )
                updateGauge(starter.id, StarterStatus.FAILED)
                logger.error(e) { "Starter ${starter.id} FAILED: ${e.message}" }
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private fun persist(
        starterId: String,
        status: StarterStatus,
        execution: StarterExecutionDocument,
        existingDoc: StarterDocument?,
    ) {
        val doc = existingDoc ?: StarterDocument().apply { this.starterId = starterId }
        doc.lastStatus = status.name
        doc.executions = doc.executions + execution
        repository.persistOrUpdate(doc)
    }

    private fun updateGauge(starterId: String, status: StarterStatus) {
        val gaugeValue = statusGauges.getOrPut(starterId) {
            val atomicInt = AtomicInteger(0)
            Gauge.builder("starter_overall_status", atomicInt) { it.get().toDouble() }
                .tag("id", starterId)
                .register(meterRegistry)
            atomicInt
        }
        gaugeValue.set(if (status == StarterStatus.SUCCEEDED) 1 else 0)
    }

    companion object : KLogging()
}
