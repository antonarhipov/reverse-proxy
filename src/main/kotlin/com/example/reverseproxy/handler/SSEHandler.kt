package com.example.reverseproxy.handler

import com.example.reverseproxy.config.ReverseProxyConfig
import com.example.reverseproxy.loadbalancer.LoadBalancer
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles Server-Sent Events (SSE) connections and forwards them to backend servers.
 */
class SSEHandler(
    private val config: ReverseProxyConfig,
    private val loadBalancer: LoadBalancer,
    private val client: HttpClient
) {
    private val logger = LoggerFactory.getLogger(SSEHandler::class.java)

    /**
     * Configures SSE routing for the reverse proxy.
     * @param routing The Routing to configure.
     */
    fun configureRouting(routing: Routing) {
        // Only set up SSE routes if SSE support is enabled
        if (!config.sse.enabled) {
            logger.info("SSE support is disabled")
            return
        }

        // Set up SSE route for any path
        routing.route("{path...}") {
            get {
                // Check if this is an SSE request by looking at the Accept header
                val acceptHeader = call.request.headers[HttpHeaders.Accept]
                if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
                    handleSSEConnection(call)
                }
            }
        }

        logger.info("SSE routes configured with reconnect time: ${config.sse.reconnectTime}ms")
    }

    /**
     * Handles an SSE connection.
     * @param call The ApplicationCall containing the request information.
     */
    private suspend fun handleSSEConnection(call: ApplicationCall) {
        try {
            // Select a backend server using the load balancer
            val backend = loadBalancer.selectBackend()

            logger.info("Selected backend for SSE: ${backend.id} (${backend.url})")

            // Extract the path from the call parameters
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""

            // Construct the backend URL
            val backendUrl = URLBuilder(backend.url).apply {
                encodedPath = path

                // Copy query parameters
                call.request.queryParameters.forEach { key, values ->
                    values.forEach { value ->
                        parameters.append(key, value)
                    }
                }
            }.build()

            logger.info("Connecting to backend SSE: $backendUrl")

            // Make the request to the backend
            val response = client.get(backendUrl) {
                headers {
                    // Set Accept header for SSE
                    append(HttpHeaders.Accept, ContentType.Text.EventStream.toString())

                    // Copy headers from the original request
                    call.request.headers.forEach { name, values ->
                        when (name.lowercase()) {
                            HttpHeaders.Host.lowercase() -> {} // Skip Host header
                            HttpHeaders.ContentLength.lowercase() -> {} // Skip Content-Length
                            HttpHeaders.TransferEncoding.lowercase() -> {} // Skip Transfer-Encoding
                            else -> values.forEach { value -> append(name, value) }
                        }
                    }

                    // Add X-Forwarded headers
                    val clientIp = call.request.headers[HttpHeaders.XForwardedFor] ?: call.request.local.remoteHost
                    val scheme = call.request.headers[HttpHeaders.XForwardedProto] ?: call.request.local.scheme

                    append(HttpHeaders.XForwardedFor, clientIp)
                    append(HttpHeaders.XForwardedProto, scheme)
                    append(HttpHeaders.XForwardedHost, call.request.headers[HttpHeaders.Host] ?: "localhost")
                    append(HttpHeaders.XForwardedPort, call.request.local.localPort.toString())

                    // Add custom header to identify the proxy
                    append("X-Proxy-ID", "Ktor-Reverse-Proxy")
                }
            }

            // Check if the response is successful
            if (response.status.isSuccess()) {
                // Set up SSE response headers
                call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                call.response.header(HttpHeaders.CacheControl, "no-cache")
                call.response.header("Connection", "keep-alive")

                // Create a writer for the response
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    // Flag to track if the connection is active
                    val isActive = AtomicBoolean(true)

                    // Create a coroutine scope for this connection
                    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

                    try {
                        // Send the initial retry directive
                        write("retry: ${config.sse.reconnectTime}\n\n")
                        flush()

                        // Start a heartbeat job
                        val heartbeatJob = scope.launch {
                            while (isActive.get()) {
                                delay(config.sse.heartbeatInterval)
                                if (isActive.get()) {
                                    try {
                                        // Send a comment as a heartbeat
                                        write(": heartbeat\n\n")
                                        flush()
                                    } catch (e: Exception) {
                                        logger.error("Error sending heartbeat", e)
                                        isActive.set(false)
                                        break
                                    }
                                }
                            }
                        }

                        // Read the response body and forward it to the client
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(8192)

                        while (isActive.get()) {
                            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                            if (bytesRead < 0) {
                                // End of stream
                                break
                            } else if (bytesRead > 0) {
                                // Forward the data to the client
                                write(String(buffer, 0, bytesRead))
                                flush()
                            }
                        }

                        // Cancel the heartbeat job
                        heartbeatJob.cancel()
                    } finally {
                        // Clean up
                        isActive.set(false)
                        scope.cancel()
                    }
                }
            } else {
                // Not a successful response, return an error
                logger.error("Backend returned an error: ${response.status}")
                call.respond(HttpStatusCode.BadGateway, "Backend server returned an error: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Error handling SSE connection", e)
            call.respond(HttpStatusCode.InternalServerError, "Error handling SSE connection: ${e.message}")
        }
    }
}
