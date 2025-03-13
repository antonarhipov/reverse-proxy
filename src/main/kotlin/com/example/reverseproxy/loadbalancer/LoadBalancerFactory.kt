package com.example.reverseproxy.loadbalancer

import com.example.reverseproxy.config.BackendConfig
import com.example.reverseproxy.config.LoadBalancingConfig

/**
 * Factory for creating load balancers based on the configuration.
 */
object LoadBalancerFactory {
    
    /**
     * Creates a load balancer based on the specified strategy.
     * @param backends List of backend server configurations.
     * @param config Load balancing configuration.
     * @return The created load balancer.
     * @throws IllegalArgumentException if the strategy is not supported.
     */
    fun createLoadBalancer(backends: List<BackendConfig>, config: LoadBalancingConfig): LoadBalancer {
        return when (config.strategy.lowercase()) {
            "round-robin" -> RoundRobinLoadBalancer(backends)
            "random" -> RandomLoadBalancer(backends)
            // Add more strategies here as they are implemented
            else -> throw IllegalArgumentException("Unsupported load balancing strategy: ${config.strategy}")
        }
    }
}