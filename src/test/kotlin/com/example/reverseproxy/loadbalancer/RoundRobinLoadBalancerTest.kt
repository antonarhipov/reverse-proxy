package com.example.reverseproxy.loadbalancer

import com.example.reverseproxy.config.BackendConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoundRobinLoadBalancerTest {

    @Test
    fun `test round robin selection`() {
        // Create test backend configurations
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health"),
            BackendConfig("backend3", "http://backend3.example.com", 1, "/health")
        )

        // Create the load balancer
        val loadBalancer = RoundRobinLoadBalancer(backends)

        // Test that backends are selected in round-robin order
        assertEquals("backend1", loadBalancer.selectBackend().id)
        assertEquals("backend2", loadBalancer.selectBackend().id)
        assertEquals("backend3", loadBalancer.selectBackend().id)
        assertEquals("backend1", loadBalancer.selectBackend().id) // Should wrap around
    }

    @Test
    fun `test no available backends`() {
        // Create test backend configurations
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health")
        )

        // Create the load balancer
        val loadBalancer = RoundRobinLoadBalancer(backends)

        // Mark all backends as failed
        loadBalancer.markBackendAsFailed("backend1")
        loadBalancer.markBackendAsFailed("backend2")

        // Test that an exception is thrown when no backends are available
        assertFailsWith<IllegalStateException> {
            loadBalancer.selectBackend()
        }
    }

    @Test
    fun `test marking backends as failed and available`() {
        // Create test backend configurations
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health")
        )

        // Create the load balancer
        val loadBalancer = RoundRobinLoadBalancer(backends)

        // Mark backend1 as failed
        loadBalancer.markBackendAsFailed("backend1")

        // Test that only backend2 is selected
        assertEquals("backend2", loadBalancer.selectBackend().id)
        assertEquals("backend2", loadBalancer.selectBackend().id) // Still backend2 since backend1 is failed

        // Mark backend1 as available again
        loadBalancer.markBackendAsAvailable("backend1")

        // Test that backends are selected in round-robin order again
        assertEquals("backend1", loadBalancer.selectBackend().id) // Now backend1 is available again
        assertEquals("backend2", loadBalancer.selectBackend().id)
    }

    @Test
    fun `test counter overflow handling`() {
        // Create test backend configurations
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health")
        )

        // Create the load balancer with a counter that's about to overflow
        val loadBalancer = RoundRobinLoadBalancer(backends)

        // Use reflection to set the counter to a value close to Integer.MAX_VALUE
        val counterField = RoundRobinLoadBalancer::class.java.getDeclaredField("counter")
        counterField.isAccessible = true
        val counter = counterField.get(loadBalancer) as java.util.concurrent.atomic.AtomicInteger
        counter.set(Integer.MAX_VALUE - 500)

        // Test that the load balancer continues to work after the counter is reset
        for (i in 1..1000) {
            val backend = loadBalancer.selectBackend()
            // Just make sure no exception is thrown
        }
    }
}