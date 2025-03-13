package com.example.reverseproxy.loadbalancer

import com.example.reverseproxy.config.BackendConfig
import java.util.concurrent.atomic.AtomicInteger

/**
 * Round-Robin load balancer that distributes requests sequentially among backend servers.
 */
class RoundRobinLoadBalancer(
    backends: List<BackendConfig>
) : AbstractLoadBalancer(backends) {
    
    // Counter to keep track of the next backend to use
    private val counter = AtomicInteger(0)
    
    /**
     * Selects a backend server for the next request using Round-Robin strategy.
     * @return The selected backend server configuration.
     * @throws IllegalStateException if no backends are available.
     */
    override fun selectBackend(): BackendConfig {
        val availableBackends = getAvailableBackends()
        
        if (availableBackends.isEmpty()) {
            throw IllegalStateException("No available backend servers")
        }
        
        // Get the next index in a thread-safe way
        val index = counter.getAndIncrement() % availableBackends.size
        
        // Handle integer overflow
        if (counter.get() >= Integer.MAX_VALUE - 1000) {
            counter.set(0)
        }
        
        return availableBackends[index]
    }
}