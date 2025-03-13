package com.example.reverseproxy.circuitbreaker

import com.example.reverseproxy.config.BackendConfig
import com.example.reverseproxy.config.CircuitBreakerConfig
import com.example.reverseproxy.handler.RequestForwarder
import com.example.reverseproxy.handler.RequestForwarderInterface
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

/**
 * Wraps a RequestForwarder with circuit breaker functionality.
 */
class CircuitBreakerRequestForwarder(
    private val requestForwarder: RequestForwarder,
    private val circuitBreakerConfig: CircuitBreakerConfig,
    private val eventLogger: CircuitBreakerEventLogger? = null
) : RequestForwarderInterface {
    private val logger = LoggerFactory.getLogger(CircuitBreakerRequestForwarder::class.java)

    /**
     * Forwards the current request to the specified backend server with circuit breaker protection.
     * @param call The ApplicationCall containing the request and response.
     * @param backend The backend server to forward the request to.
     */
    override suspend fun forwardRequest(call: ApplicationCall, backend: BackendConfig) {
        // Get or create a circuit breaker for this backend
        val circuitBreaker = SimpleCircuitBreaker.getOrCreate(
            name = backend.id,
            failureThreshold = circuitBreakerConfig.failureRateThreshold,
            resetTimeoutMs = circuitBreakerConfig.waitDurationInOpenState,
            eventLogger = eventLogger
        )

        // Check the circuit breaker state
        when (circuitBreaker.getState()) {
            SimpleCircuitBreaker.State.OPEN -> {
                logger.warn("Circuit breaker for backend '${backend.id}' is OPEN, returning service unavailable")
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    "Service temporarily unavailable. Please try again later."
                )
                return
            }
            SimpleCircuitBreaker.State.HALF_OPEN -> {
                logger.info("Circuit breaker for backend '${backend.id}' is HALF_OPEN, testing the connection")
            }
            SimpleCircuitBreaker.State.CLOSED -> {
                logger.debug("Circuit breaker for backend '${backend.id}' is CLOSED, proceeding normally")
            }
        }

        // Execute the request with circuit breaker protection
        circuitBreaker.executeSuspend(
            fallback = { e ->
                logger.error("Circuit breaker prevented request to backend '${backend.id}': ${e.message}")
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    "Service temporarily unavailable. Please try again later."
                )
            },
            block = {
                // Forward the request to the backend
                requestForwarder.forwardRequest(call, backend)
                // This is just a placeholder return value since the forwardRequest method doesn't return anything
                Unit
            }
        )
    }
}
