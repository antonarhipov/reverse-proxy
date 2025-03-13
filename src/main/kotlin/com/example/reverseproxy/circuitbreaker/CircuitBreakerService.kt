package com.example.reverseproxy.circuitbreaker

import com.example.reverseproxy.config.BackendConfig
import com.example.reverseproxy.config.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig as R4JCircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * Service for managing circuit breakers for backend servers.
 */
class CircuitBreakerService(
    private val config: CircuitBreakerConfig
) {
    private val logger = LoggerFactory.getLogger(CircuitBreakerService::class.java)
    
    // Registry to manage all circuit breakers
    private val circuitBreakerRegistry: CircuitBreakerRegistry
    
    // Map of backend ID to circuit breaker
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
    
    init {
        // Configure the circuit breaker registry
        val circuitBreakerConfig = R4JCircuitBreakerConfig.custom()
            .failureRateThreshold(config.failureRateThreshold.toFloat())
            .waitDurationInOpenState(Duration.ofMillis(config.waitDurationInOpenState))
            .slidingWindowSize(config.ringBufferSizeInClosedState)
            .minimumNumberOfCalls(config.ringBufferSizeInHalfOpenState)
            .automaticTransitionFromOpenToHalfOpenEnabled(config.automaticTransitionFromOpenToHalfOpenEnabled)
            .build()
        
        circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig)
        
        logger.info("Circuit breaker service initialized with failure rate threshold: ${config.failureRateThreshold}%")
    }
    
    /**
     * Gets or creates a circuit breaker for a backend server.
     * @param backend The backend server configuration.
     * @return The circuit breaker for the backend server.
     */
    fun getCircuitBreaker(backend: BackendConfig): CircuitBreaker {
        return circuitBreakers.computeIfAbsent(backend.id) { backendId ->
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker(backendId)
            
            // Register event listeners for monitoring
            circuitBreaker.eventPublisher.onStateTransition { event ->
                logger.info("Circuit breaker '${event.circuitBreakerName}' state changed from ${event.stateTransition.fromState} to ${event.stateTransition.toState}")
            }
            
            circuitBreaker
        }
    }
    
    /**
     * Executes a function with circuit breaker protection.
     * @param backend The backend server configuration.
     * @param fallback The function to execute when the circuit breaker is open.
     * @param block The function to execute with circuit breaker protection.
     * @return The result of the function execution.
     */
    fun <T> executeWithCircuitBreaker(
        backend: BackendConfig,
        fallback: (Exception) -> T,
        block: () -> T
    ): T {
        val circuitBreaker = getCircuitBreaker(backend)
        
        val decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, Supplier { block() })
        
        return try {
            decoratedSupplier.get()
        } catch (e: Exception) {
            logger.error("Circuit breaker '${backend.id}' prevented execution due to: ${e.message}")
            fallback(e)
        }
    }
    
    /**
     * Gets the state of a circuit breaker.
     * @param backendId The ID of the backend server.
     * @return The state of the circuit breaker, or null if the circuit breaker does not exist.
     */
    fun getCircuitBreakerState(backendId: String): CircuitBreaker.State? {
        return circuitBreakers[backendId]?.state
    }
    
    /**
     * Checks if a circuit breaker is closed (allowing requests).
     * @param backendId The ID of the backend server.
     * @return True if the circuit breaker is closed, false otherwise.
     */
    fun isCircuitBreakerClosed(backendId: String): Boolean {
        return circuitBreakers[backendId]?.state == CircuitBreaker.State.CLOSED
    }
    
    /**
     * Resets a circuit breaker to its closed state.
     * @param backendId The ID of the backend server.
     */
    fun resetCircuitBreaker(backendId: String) {
        circuitBreakers[backendId]?.reset()
        logger.info("Circuit breaker '$backendId' has been reset")
    }
}