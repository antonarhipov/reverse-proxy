package com.example.reverseproxy.handler

import com.example.reverseproxy.config.ReverseProxyConfig
import com.example.reverseproxy.loadbalancer.LoadBalancer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Handles incoming HTTP requests and forwards them to backend servers.
 */
class ProxyHandler(
    private val config: ReverseProxyConfig,
    private val loadBalancer: LoadBalancer,
    private val requestForwarder: RequestForwarderInterface
) {
    private val logger = LoggerFactory.getLogger(ProxyHandler::class.java)

    /**
     * Configures routing for the reverse proxy.
     * @param routing The Routing to configure.
     */
    fun configureRouting(routing: Routing) {
        // Catch-all route for any path
        routing.route("{...}") {
            handle {
                handleRequest(call)
            }
        }
    }

    /**
     * Handles an incoming request.
     * @param call The ApplicationCall containing the request and response.
     */
    private suspend fun handleRequest(call: ApplicationCall) {
        try {
            // Select a backend server using the load balancer
            val backend = loadBalancer.selectBackend()

            logger.info("Selected backend: ${backend.id} (${backend.url})")

            // Forward the request to the selected backend
            requestForwarder.forwardRequest(call, backend)

        } catch (e: Exception) {
            when (e) {
                is IllegalStateException -> {
                    // No available backends
                    logger.error("No available backend servers", e)
                    call.respond(HttpStatusCode.ServiceUnavailable, "No available backend servers")
                }
                else -> {
                    // Other errors
                    logger.error("Error handling request", e)
                    call.respond(HttpStatusCode.InternalServerError, "Error handling request: ${e.message}")
                }
            }
        }
    }
}
