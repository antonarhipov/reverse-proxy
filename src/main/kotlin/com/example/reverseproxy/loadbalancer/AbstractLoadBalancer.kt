package com.example.reverseproxy.loadbalancer

import com.example.reverseproxy.config.BackendConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for load balancers that implements common functionality.
 */
abstract class AbstractLoadBalancer(
    protected val backends: List<BackendConfig>
) : LoadBalancer {
    
    // Map to track the availability of backends
    protected val availabilityMap = ConcurrentHashMap<String, Boolean>()
    
    init {
        // Initialize all backends as available
        backends.forEach { backend ->
            availabilityMap[backend.id] = true
        }
    }
    
    /**
     * Marks a backend server as failed.
     * @param backendId The ID of the failed backend server.
     */
    override fun markBackendAsFailed(backendId: String) {
        availabilityMap[backendId] = false
    }
    
    /**
     * Marks a backend server as available.
     * @param backendId The ID of the available backend server.
     */
    override fun markBackendAsAvailable(backendId: String) {
        availabilityMap[backendId] = true
    }
    
    /**
     * Gets all available backend servers.
     * @return List of available backend servers.
     */
    override fun getAvailableBackends(): List<BackendConfig> {
        return backends.filter { backend -> availabilityMap[backend.id] == true }
    }
    
    /**
     * Checks if there are any available backends.
     * @return True if there are available backends, false otherwise.
     */
    protected fun hasAvailableBackends(): Boolean {
        return getAvailableBackends().isNotEmpty()
    }
    
    /**
     * Gets a backend by ID.
     * @param backendId The ID of the backend server.
     * @return The backend server configuration, or null if not found.
     */
    protected fun getBackendById(backendId: String): BackendConfig? {
        return backends.find { it.id == backendId }
    }
}