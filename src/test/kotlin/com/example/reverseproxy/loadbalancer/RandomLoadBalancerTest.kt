package com.example.reverseproxy.loadbalancer

import com.example.reverseproxy.config.BackendConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RandomLoadBalancerTest {

    @Test
    fun `test random selection distribution`() {
        // Create test backend configurations
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health"),
            BackendConfig("backend3", "http://backend3.example.com", 1, "/health")
        )

        // Create the load balancer
        val loadBalancer = RandomLoadBalancer(backends)

        // Count selections for each backend
        val selectionCounts = mutableMapOf(
            "backend1" to 0,
            "backend2" to 0,
            "backend3" to 0
        )

        // Make a large number of selections
        val totalSelections = 10000
        for (i in 1..totalSelections) {
            val selectedBackend = loadBalancer.selectBackend()
            selectionCounts[selectedBackend.id] = selectionCounts[selectedBackend.id]!! + 1
        }

        // Check that all backends were selected
        assertTrue(selectionCounts.all { it.value > 0 })

        // Check that the distribution is roughly even (within 10% of expected)
        val expectedCount = totalSelections / backends.size
        val tolerance = expectedCount * 0.1 // 10% tolerance
        assertTrue(selectionCounts.all { 
            Math.abs(it.value - expectedCount) <= tolerance 
        }, "Selection distribution is not even: $selectionCounts")
    }

    @Test
    fun `test no available backends`() {
        // Create test backend configurations
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health")
        )

        // Create the load balancer
        val loadBalancer = RandomLoadBalancer(backends)

        // Mark all backends as failed
        loadBalancer.markBackendAsFailed("backend1")
        loadBalancer.markBackendAsFailed("backend2")

        // Test that an exception is thrown when no backends are available
        assertFailsWith<IllegalStateException> {
            loadBalancer.selectBackend()
        }
    }

    @Test
    fun `test selection from available backends only`() {
        // Create test backend configurations
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health"),
            BackendConfig("backend3", "http://backend3.example.com", 1, "/health")
        )

        // Create the load balancer
        val loadBalancer = RandomLoadBalancer(backends)

        // Mark backend1 as failed
        loadBalancer.markBackendAsFailed("backend1")

        // Make a large number of selections
        val selectionCounts = mutableMapOf(
            "backend1" to 0,
            "backend2" to 0,
            "backend3" to 0
        )

        val totalSelections = 1000
        for (i in 1..totalSelections) {
            val selectedBackend = loadBalancer.selectBackend()
            selectionCounts[selectedBackend.id] = selectionCounts[selectedBackend.id]!! + 1
        }

        // Check that backend1 was never selected
        assertEquals(0, selectionCounts["backend1"])

        // Check that backend2 and backend3 were selected
        assertTrue(selectionCounts["backend2"]!! > 0)
        assertTrue(selectionCounts["backend3"]!! > 0)
    }
}