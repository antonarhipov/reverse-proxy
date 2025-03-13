package com.example.reverseproxy.circuitbreaker

import com.codahale.metrics.MetricRegistry
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Logger for circuit breaker events.
 * Provides comprehensive logging and metrics for circuit breaker events.
 */
class CircuitBreakerEventLogger(
    private val metricRegistry: MetricRegistry
) {
    private val logger = LoggerFactory.getLogger(CircuitBreakerEventLogger::class.java)

    // Store recent events for analysis
    private val recentEvents = ConcurrentLinkedQueue<CircuitBreakerEventRecord>()
    private val maxRecentEvents = 1000

    // Counters for different event types
    private val successCounter = AtomicInteger(0)
    private val errorCounter = AtomicInteger(0)
    private val stateTransitionCounter = AtomicInteger(0)
    private val callNotPermittedCounter = AtomicInteger(0)

    // Metrics for circuit breaker events
    private val successMeter = metricRegistry.meter("circuit-breaker.events.success")
    private val errorMeter = metricRegistry.meter("circuit-breaker.events.error")
    private val stateTransitionMeter = metricRegistry.meter("circuit-breaker.events.state-transition")
    private val callNotPermittedMeter = metricRegistry.meter("circuit-breaker.events.call-not-permitted")

    // Counters for circuit breaker states
    private val openCircuitBreakersCounter = AtomicInteger(0)
    private val halfOpenCircuitBreakersCounter = AtomicInteger(0)
    private val closedCircuitBreakersCounter = AtomicInteger(0)

    /**
     * Logs a state transition event.
     * @param circuitBreakerName The name of the circuit breaker.
     * @param fromState The previous state.
     * @param toState The new state.
     */
    fun logStateTransition(circuitBreakerName: String, fromState: SimpleCircuitBreaker.State, toState: SimpleCircuitBreaker.State) {
        // Increment the counter
        stateTransitionCounter.incrementAndGet()

        // Mark the meter
        stateTransitionMeter.mark()

        // Update the state gauges
        updateStateGauges(circuitBreakerName, fromState, toState)

        // Add MDC context for structured logging
        MDC.put("circuitBreaker", circuitBreakerName)
        MDC.put("eventType", "STATE_TRANSITION")
        MDC.put("fromState", fromState.toString())
        MDC.put("toState", toState.toString())

        // Log the event
        logger.info("Circuit breaker '$circuitBreakerName' state changed from $fromState to $toState")

        // Add the event to the recent events queue
        addEventToQueue(CircuitBreakerEventRecord(
            timestamp = Instant.now(),
            circuitBreakerName = circuitBreakerName,
            eventType = "STATE_TRANSITION",
            details = "From $fromState to $toState"
        ))

        // Clear MDC context
        MDC.remove("circuitBreaker")
        MDC.remove("eventType")
        MDC.remove("fromState")
        MDC.remove("toState")
    }

    /**
     * Logs an error event.
     * @param circuitBreakerName The name of the circuit breaker.
     * @param errorMessage The error message.
     * @param failureCount The current failure count.
     */
    fun logError(circuitBreakerName: String, errorMessage: String, failureCount: Int) {
        // Increment the counter
        errorCounter.incrementAndGet()

        // Mark the meter
        errorMeter.mark()

        // Add MDC context for structured logging
        MDC.put("circuitBreaker", circuitBreakerName)
        MDC.put("eventType", "ERROR")
        MDC.put("errorMessage", errorMessage)
        MDC.put("failureCount", failureCount.toString())

        // Log the event
        logger.error("Circuit breaker '$circuitBreakerName' recorded an error: $errorMessage (failure count: $failureCount)")

        // Add the event to the recent events queue
        addEventToQueue(CircuitBreakerEventRecord(
            timestamp = Instant.now(),
            circuitBreakerName = circuitBreakerName,
            eventType = "ERROR",
            details = "Error: $errorMessage, Failure count: $failureCount"
        ))

        // Clear MDC context
        MDC.remove("circuitBreaker")
        MDC.remove("eventType")
        MDC.remove("errorMessage")
        MDC.remove("failureCount")
    }

    /**
     * Logs a success event.
     * @param circuitBreakerName The name of the circuit breaker.
     */
    fun logSuccess(circuitBreakerName: String) {
        // Increment the counter
        successCounter.incrementAndGet()

        // Mark the meter
        successMeter.mark()

        // Add MDC context for structured logging
        MDC.put("circuitBreaker", circuitBreakerName)
        MDC.put("eventType", "SUCCESS")

        // Log the event
        logger.debug("Circuit breaker '$circuitBreakerName' recorded a success")

        // Add the event to the recent events queue
        addEventToQueue(CircuitBreakerEventRecord(
            timestamp = Instant.now(),
            circuitBreakerName = circuitBreakerName,
            eventType = "SUCCESS",
            details = null
        ))

        // Clear MDC context
        MDC.remove("circuitBreaker")
        MDC.remove("eventType")
    }

    /**
     * Logs a call not permitted event.
     * @param circuitBreakerName The name of the circuit breaker.
     */
    fun logCallNotPermitted(circuitBreakerName: String) {
        // Increment the counter
        callNotPermittedCounter.incrementAndGet()

        // Mark the meter
        callNotPermittedMeter.mark()

        // Add MDC context for structured logging
        MDC.put("circuitBreaker", circuitBreakerName)
        MDC.put("eventType", "CALL_NOT_PERMITTED")

        // Log the event
        logger.warn("Circuit breaker '$circuitBreakerName' prevented a call")

        // Add the event to the recent events queue
        addEventToQueue(CircuitBreakerEventRecord(
            timestamp = Instant.now(),
            circuitBreakerName = circuitBreakerName,
            eventType = "CALL_NOT_PERMITTED",
            details = null
        ))

        // Clear MDC context
        MDC.remove("circuitBreaker")
        MDC.remove("eventType")
    }

    /**
     * Updates the state counters for a circuit breaker.
     * @param circuitBreakerName The name of the circuit breaker.
     * @param fromState The previous state.
     * @param toState The new state.
     */
    private fun updateStateGauges(circuitBreakerName: String, fromState: SimpleCircuitBreaker.State, toState: SimpleCircuitBreaker.State) {
        // Update the counters based on the state transition
        when (fromState) {
            SimpleCircuitBreaker.State.OPEN -> openCircuitBreakersCounter.decrementAndGet()
            SimpleCircuitBreaker.State.HALF_OPEN -> halfOpenCircuitBreakersCounter.decrementAndGet()
            SimpleCircuitBreaker.State.CLOSED -> closedCircuitBreakersCounter.decrementAndGet()
        }

        when (toState) {
            SimpleCircuitBreaker.State.OPEN -> openCircuitBreakersCounter.incrementAndGet()
            SimpleCircuitBreaker.State.HALF_OPEN -> halfOpenCircuitBreakersCounter.incrementAndGet()
            SimpleCircuitBreaker.State.CLOSED -> closedCircuitBreakersCounter.incrementAndGet()
        }
    }

    /**
     * Adds an event to the recent events queue.
     * @param event The circuit breaker event record.
     */
    private fun addEventToQueue(event: CircuitBreakerEventRecord) {
        // Add the event to the queue
        recentEvents.add(event)

        // Remove old events if the queue is too large
        while (recentEvents.size > maxRecentEvents) {
            recentEvents.poll()
        }
    }

    /**
     * Gets the recent events for a circuit breaker.
     * @param circuitBreakerName The name of the circuit breaker.
     * @return The recent events.
     */
    fun getRecentEvents(circuitBreakerName: String): List<CircuitBreakerEventRecord> {
        return recentEvents.filter { it.circuitBreakerName == circuitBreakerName }
    }

    /**
     * Gets all recent events.
     * @return All recent events.
     */
    fun getAllRecentEvents(): List<CircuitBreakerEventRecord> {
        return recentEvents.toList()
    }

    /**
     * Gets the number of success events.
     * @return The number of success events.
     */
    fun getSuccessCount(): Int {
        return successCounter.get()
    }

    /**
     * Gets the number of error events.
     * @return The number of error events.
     */
    fun getErrorCount(): Int {
        return errorCounter.get()
    }

    /**
     * Gets the number of state transition events.
     * @return The number of state transition events.
     */
    fun getStateTransitionCount(): Int {
        return stateTransitionCounter.get()
    }

    /**
     * Gets the number of call not permitted events.
     * @return The number of call not permitted events.
     */
    fun getCallNotPermittedCount(): Int {
        return callNotPermittedCounter.get()
    }

    /**
     * Record of a circuit breaker event.
     */
    data class CircuitBreakerEventRecord(
        val timestamp: Instant,
        val circuitBreakerName: String,
        val eventType: String,
        val details: String?
    )
}
