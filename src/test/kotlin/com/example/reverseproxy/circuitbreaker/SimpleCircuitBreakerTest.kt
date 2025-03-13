package com.example.reverseproxy.circuitbreaker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class SimpleCircuitBreakerTest {

    @Test
    fun `test initial state is closed`() {
        val circuitBreaker = SimpleCircuitBreaker(
            name = "test",
            failureThreshold = 3,
            resetTimeoutMs = 100
        )

        assertEquals(SimpleCircuitBreaker.State.CLOSED, circuitBreaker.getState())
    }

    @Test
    fun `test transition to open after failures`() {
        val circuitBreaker = SimpleCircuitBreaker(
            name = "test",
            failureThreshold = 3,
            resetTimeoutMs = 100
        )

        // Execute with failures
        for (i in 1..3) {
            val result = circuitBreaker.execute(
                fallback = { e -> "fallback" },
                block = { throw RuntimeException("Test failure") }
            )
            assertEquals("fallback", result)
        }

        // After 3 failures, the circuit should be open
        assertEquals(SimpleCircuitBreaker.State.OPEN, circuitBreaker.getState())
    }

    @Test
    fun `test fallback is executed when circuit is open`() {
        val circuitBreaker = SimpleCircuitBreaker(
            name = "test",
            failureThreshold = 3,
            resetTimeoutMs = 100
        )

        // Execute with failures to open the circuit
        for (i in 1..3) {
            val result = circuitBreaker.execute(
                fallback = { e -> "fallback" },
                block = { throw RuntimeException("Test failure") }
            )
            assertEquals("fallback", result)
        }

        // Now the circuit is open, fallback should be executed
        val result = circuitBreaker.execute(
            fallback = { e -> "fallback" },
            block = { "success" }
        )

        assertEquals("fallback", result)
    }

    @Test
    fun `test transition to half-open after timeout`() {
        val circuitBreaker = SimpleCircuitBreaker(
            name = "test",
            failureThreshold = 3,
            resetTimeoutMs = 100
        )

        // Execute with failures to open the circuit
        for (i in 1..3) {
            val result = circuitBreaker.execute(
                fallback = { e -> "fallback" },
                block = { throw RuntimeException("Test failure") }
            )
            assertEquals("fallback", result)
        }

        // Wait for the reset timeout
        Thread.sleep(150)

        // Execute again, this should transition to half-open
        val result = circuitBreaker.execute(
            fallback = { e -> "fallback" },
            block = { "success" }
        )

        assertEquals("success", result)
        assertEquals(SimpleCircuitBreaker.State.CLOSED, circuitBreaker.getState())
    }

    @Test
    fun `test transition back to open on failure in half-open state`() {
        val circuitBreaker = SimpleCircuitBreaker(
            name = "test",
            failureThreshold = 3,
            resetTimeoutMs = 100
        )

        // Execute with failures to open the circuit
        for (i in 1..3) {
            val result = circuitBreaker.execute(
                fallback = { e -> "fallback" },
                block = { throw RuntimeException("Test failure") }
            )
            assertEquals("fallback", result)
        }

        // Wait for the reset timeout
        Thread.sleep(150)

        // Set the state to half-open using reflection
        val stateField = SimpleCircuitBreaker::class.java.getDeclaredField("state")
        stateField.isAccessible = true
        val stateRef = stateField.get(circuitBreaker) as java.util.concurrent.atomic.AtomicReference<SimpleCircuitBreaker.State>
        stateRef.set(SimpleCircuitBreaker.State.HALF_OPEN)

        // Execute with failure, this should transition back to open
        val result = circuitBreaker.execute(
            fallback = { e -> "fallback" },
            block = { throw RuntimeException("Test failure") }
        )
        assertEquals("fallback", result)

        assertEquals(SimpleCircuitBreaker.State.OPEN, circuitBreaker.getState())
    }

    @Test
    fun `test reset method`() {
        val circuitBreaker = SimpleCircuitBreaker(
            name = "test",
            failureThreshold = 3,
            resetTimeoutMs = 100
        )

        // Execute with failures to open the circuit
        for (i in 1..3) {
            val result = circuitBreaker.execute(
                fallback = { e -> "fallback" },
                block = { throw RuntimeException("Test failure") }
            )
            assertEquals("fallback", result)
        }

        // Reset the circuit breaker
        circuitBreaker.reset()

        // The state should be closed
        assertEquals(SimpleCircuitBreaker.State.CLOSED, circuitBreaker.getState())

        // Execute should work normally
        val result = circuitBreaker.execute(
            fallback = { e -> "fallback" },
            block = { "success" }
        )

        assertEquals("success", result)
    }
}
