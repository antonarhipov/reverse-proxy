package com.example.reverseproxy.loadbalancer

import com.example.reverseproxy.config.BackendConfig

/**
 * Interface for load balancers that distribute requests among backend servers.
 */
interface LoadBalancer {
    /**
     * Selects a backend server for the next request.
     * @return The selected backend server configuration.
     */
    fun selectBackend(): BackendConfig
    
    /**
     * Marks a backend server as failed.
     * @param backendId The ID of the failed backend server.
     */
    fun markBackendAsFailed(backendId: String)
    
    /**
     * Marks a backend server as available.
     * @param backendId The ID of the available backend server.
     */
    fun markBackendAsAvailable(backendId: String)
    
    /**
     * Gets all available backend servers.
     * @return List of available backend servers.
     */
    fun getAvailableBackends(): List<BackendConfig>
}