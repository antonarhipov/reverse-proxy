package com.example.reverseproxy.circuitbreaker

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * A simple implementation of the circuit breaker pattern.
 */
class SimpleCircuitBreaker(
    private val name: String? = null,
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 30000,
    private val eventLogger: CircuitBreakerEventLogger? = null
) {
    private val logger = LoggerFactory.getLogger(SimpleCircuitBreaker::class.java)

    // Circuit breaker states
    enum class State {
        CLOSED,     // Normal operation, requests are allowed
        OPEN,       // Circuit is open, requests are not allowed
        HALF_OPEN   // Testing if the service is back, allowing a single request
    }

    // Current state of the circuit breaker
    private val state = AtomicReference(State.CLOSED)

    // Counter for consecutive failures
    private val failureCount = AtomicInteger(0)

    // Time when the circuit was opened
    private var openTime: Instant? = null

    /**
     * Executes a function with circuit breaker protection.
     * @param fallback The function to execute when the circuit breaker is open.
     * @param block The function to execute with circuit breaker protection.
     * @return The result of the function execution.
     */
    fun <T> execute(fallback: (Exception) -> T, block: () -> T): T {
        // Check if the circuit is open
        when (state.get()) {
            State.OPEN -> {
                // Check if the reset timeout has elapsed
                val now = Instant.now()
                if (openTime != null && now.isAfter(openTime!!.plusMillis(resetTimeoutMs))) {
                    // Transition to half-open state
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        logger.info("Circuit breaker transitioning from OPEN to HALF_OPEN")
                        eventLogger?.logStateTransition(name ?: "unnamed", State.OPEN, State.HALF_OPEN)
                    }
                } else {
                    // Circuit is still open, execute fallback
                    logger.debug("Circuit is OPEN, executing fallback")
                    eventLogger?.logCallNotPermitted(name ?: "unnamed")
                    return fallback(CircuitOpenException("Circuit is open"))
                }
            }
            else -> {
                // Continue with normal execution
            }
        }

        return try {
            // Execute the function
            val result = block()

            // If successful and in half-open state, transition to closed
            if (state.get() == State.HALF_OPEN) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    logger.info("Circuit breaker transitioning from HALF_OPEN to CLOSED")
                    eventLogger?.logStateTransition(name ?: "unnamed", State.HALF_OPEN, State.CLOSED)
                    failureCount.set(0)
                }
            } else if (state.get() == State.CLOSED) {
                // Reset failure count on success in closed state
                failureCount.set(0)
            }

            // Log success
            eventLogger?.logSuccess(name ?: "unnamed")

            result
        } catch (e: Exception) {
            // Record the failure
            val currentFailures = failureCount.incrementAndGet()
            logger.error("Circuit breaker recorded failure: ${e.message}, count: $currentFailures")
            eventLogger?.logError(name ?: "unnamed", e.message ?: "Unknown error", currentFailures)

            // If we've reached the threshold, open the circuit
            if (state.get() == State.CLOSED && currentFailures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    logger.info("Circuit breaker transitioning from CLOSED to OPEN after $currentFailures consecutive failures")
                    eventLogger?.logStateTransition(name ?: "unnamed", State.CLOSED, State.OPEN)
                    openTime = Instant.now()
                }
            } else if (state.get() == State.HALF_OPEN) {
                // If we fail in half-open state, go back to open
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    logger.info("Circuit breaker transitioning from HALF_OPEN to OPEN after a failure")
                    eventLogger?.logStateTransition(name ?: "unnamed", State.HALF_OPEN, State.OPEN)
                    openTime = Instant.now()
                }
            }

            // Execute fallback
            fallback(e)
        }
    }

    /**
     * Executes a suspend function with circuit breaker protection.
     * @param fallback The suspend function to execute when the circuit breaker is open.
     * @param block The suspend function to execute with circuit breaker protection.
     * @return The result of the function execution.
     */
    suspend fun <T> executeSuspend(fallback: suspend (Exception) -> T, block: suspend () -> T): T {
        // Check if the circuit is open
        when (state.get()) {
            State.OPEN -> {
                // Check if the reset timeout has elapsed
                val now = Instant.now()
                if (openTime != null && now.isAfter(openTime!!.plusMillis(resetTimeoutMs))) {
                    // Transition to half-open state
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        logger.info("Circuit breaker transitioning from OPEN to HALF_OPEN")
                        eventLogger?.logStateTransition(name ?: "unnamed", State.OPEN, State.HALF_OPEN)
                    }
                } else {
                    // Circuit is still open, execute fallback
                    logger.debug("Circuit is OPEN, executing fallback")
                    eventLogger?.logCallNotPermitted(name ?: "unnamed")
                    return fallback(CircuitOpenException("Circuit is open"))
                }
            }
            else -> {
                // Continue with normal execution
            }
        }

        return try {
            // Execute the function
            val result = block()

            // If successful and in half-open state, transition to closed
            if (state.get() == State.HALF_OPEN) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    logger.info("Circuit breaker transitioning from HALF_OPEN to CLOSED")
                    eventLogger?.logStateTransition(name ?: "unnamed", State.HALF_OPEN, State.CLOSED)
                    failureCount.set(0)
                }
            } else if (state.get() == State.CLOSED) {
                // Reset failure count on success in closed state
                failureCount.set(0)
            }

            // Log success
            eventLogger?.logSuccess(name ?: "unnamed")

            result
        } catch (e: Exception) {
            // Record the failure
            val currentFailures = failureCount.incrementAndGet()
            logger.error("Circuit breaker recorded failure: ${e.message}, count: $currentFailures")
            eventLogger?.logError(name ?: "unnamed", e.message ?: "Unknown error", currentFailures)

            // If we've reached the threshold, open the circuit
            if (state.get() == State.CLOSED && currentFailures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    logger.info("Circuit breaker transitioning from CLOSED to OPEN after $currentFailures consecutive failures")
                    eventLogger?.logStateTransition(name ?: "unnamed", State.CLOSED, State.OPEN)
                    openTime = Instant.now()
                }
            } else if (state.get() == State.HALF_OPEN) {
                // If we fail in half-open state, go back to open
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    logger.info("Circuit breaker transitioning from HALF_OPEN to OPEN after a failure")
                    eventLogger?.logStateTransition(name ?: "unnamed", State.HALF_OPEN, State.OPEN)
                    openTime = Instant.now()
                }
            }

            // Execute fallback
            fallback(e)
        }
    }

    /**
     * Gets the current state of the circuit breaker.
     * @return The current state.
     */
    fun getState(): State {
        return state.get()
    }

    /**
     * Resets the circuit breaker to its closed state.
     */
    fun reset() {
        val previousState = state.get()
        state.set(State.CLOSED)
        failureCount.set(0)
        openTime = null
        logger.info("Circuit breaker has been reset to CLOSED state")
        eventLogger?.logStateTransition(name ?: "unnamed", previousState, State.CLOSED)
    }

    /**
     * Exception thrown when the circuit is open.
     */
    class CircuitOpenException(message: String) : Exception(message)

    companion object {
        // Map of circuit breakers by name
        private val circuitBreakers = ConcurrentHashMap<String, SimpleCircuitBreaker>()

        /**
         * Gets or creates a circuit breaker by name.
         * @param name The name of the circuit breaker.
         * @param failureThreshold The number of consecutive failures before opening the circuit.
         * @param resetTimeoutMs The time in milliseconds to wait before transitioning from open to half-open.
         * @return The circuit breaker.
         */
        fun getOrCreate(
            name: String,
            failureThreshold: Int = 5,
            resetTimeoutMs: Long = 30000,
            eventLogger: CircuitBreakerEventLogger? = null
        ): SimpleCircuitBreaker {
            return circuitBreakers.computeIfAbsent(name) {
                SimpleCircuitBreaker(name, failureThreshold, resetTimeoutMs, eventLogger)
            }
        }
    }
}
