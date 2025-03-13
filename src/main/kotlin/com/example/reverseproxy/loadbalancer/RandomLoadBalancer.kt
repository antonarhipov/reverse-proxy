package com.example.reverseproxy.loadbalancer

import com.example.reverseproxy.config.BackendConfig
import kotlin.random.Random

/**
 * Random load balancer that distributes requests randomly among backend servers.
 */
class RandomLoadBalancer(
    backends: List<BackendConfig>
) : AbstractLoadBalancer(backends) {
    
    /**
     * Selects a backend server for the next request using Random strategy.
     * @return The selected backend server configuration.
     * @throws IllegalStateException if no backends are available.
     */
    override fun selectBackend(): BackendConfig {
        val availableBackends = getAvailableBackends()
        
        if (availableBackends.isEmpty()) {
            throw IllegalStateException("No available backend servers")
        }
        
        // Select a random backend from the available ones
        val randomIndex = Random.nextInt(availableBackends.size)
        return availableBackends[randomIndex]
    }
}