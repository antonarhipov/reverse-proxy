package com.example.reverseproxy.handler

import com.example.reverseproxy.config.BackendConfig
import io.ktor.server.application.*

/**
 * Interface for request forwarders that forward HTTP requests to backend servers.
 */
interface RequestForwarderInterface {
    /**
     * Forwards the current request to the specified backend server.
     * @param call The ApplicationCall containing the request and response.
     * @param backend The backend server to forward the request to.
     */
    suspend fun forwardRequest(call: ApplicationCall, backend: BackendConfig)
}