package com.example.reverseproxy.handler

import com.example.reverseproxy.config.ReverseProxyConfig
import com.example.reverseproxy.loadbalancer.LoadBalancer
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Handles WebSocket connections and forwards them to backend servers.
 */
class WebSocketHandler(
    private val config: ReverseProxyConfig,
    private val loadBalancer: LoadBalancer,
    private val client: HttpClient
) {
    private val logger = LoggerFactory.getLogger(WebSocketHandler::class.java)

    /**
     * Configures WebSocket routing for the reverse proxy.
     * @param routing The Routing to configure.
     */
    fun configureRouting(routing: Routing) {
        // Only set up WebSocket routes if WebSocket support is enabled
        if (!config.webSocket.enabled) {
            logger.info("WebSocket support is disabled")
            return
        }

        // Set up WebSocket route for any path
        routing.webSocket("{path...}") {
            handleWebSocketConnection(this, call)
        }

        logger.info("WebSocket routes configured with ping interval: ${config.webSocket.pingInterval}ms")
    }

    /**
     * Handles a WebSocket connection.
     * @param serverSession The DefaultWebSocketServerSession for the client connection.
     * @param call The ApplicationCall containing the request information.
     */
    private suspend fun handleWebSocketConnection(serverSession: DefaultWebSocketServerSession, call: ApplicationCall) {
        try {
            // Select a backend server using the load balancer
            val backend = loadBalancer.selectBackend()

            logger.info("Selected backend for WebSocket: ${backend.id} (${backend.url})")

            // Extract the path from the call parameters
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""

            // Construct the backend WebSocket URL
            val backendUrl = URLBuilder(backend.url).apply {
                // Convert http:// to ws:// and https:// to wss://
                protocol = when (protocol.name) {
                    "http" -> URLProtocol.WS
                    "https" -> URLProtocol.WSS
                    else -> protocol
                }

                // Set the path
                encodedPath = path

                // Copy query parameters
                call.request.queryParameters.forEach { key, values ->
                    values.forEach { value ->
                        parameters.append(key, value)
                    }
                }
            }.build()

            logger.info("Connecting to backend WebSocket: $backendUrl")

            // Create a coroutine scope for this connection
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            try {
                // Connect to the backend WebSocket
                client.webSocket(
                    method = HttpMethod.Get,
                    host = backendUrl.host,
                    port = backendUrl.port,
                    path = backendUrl.encodedPath,
                    request = {
                        // Copy query parameters
                        url.parameters.appendAll(backendUrl.parameters)

                        // Copy headers from the original request
                        call.request.headers.forEach { name, values ->
                            when (name.lowercase()) {
                                HttpHeaders.Host.lowercase() -> {} // Skip Host header
                                HttpHeaders.ContentLength.lowercase() -> {} // Skip Content-Length
                                HttpHeaders.TransferEncoding.lowercase() -> {} // Skip Transfer-Encoding
                                else -> values.forEach { value -> headers.append(name, value) }
                            }
                        }
                    }
                ) {
                    // Forward frames from client to backend
                    val clientToBackendJob = scope.launch {
                        try {
                            for (frame in serverSession.incoming) {
                                logger.debug("Client -> Backend: ${frame.frameType}")
                                outgoing.send(frame)
                            }
                        } catch (e: ClosedReceiveChannelException) {
                            logger.info("Client closed the connection")
                        } catch (e: Exception) {
                            logger.error("Error in client to backend communication", e)
                        }
                    }

                    // Forward frames from backend to client
                    val backendToClientJob = scope.launch {
                        try {
                            for (frame in incoming) {
                                logger.debug("Backend -> Client: ${frame.frameType}")
                                serverSession.outgoing.send(frame)
                            }
                        } catch (e: ClosedReceiveChannelException) {
                            logger.info("Backend closed the connection")
                        } catch (e: Exception) {
                            logger.error("Error in backend to client communication", e)
                        }
                    }

                    // Wait for either job to complete
                    try {
                        clientToBackendJob.join()
                        backendToClientJob.join()
                    } catch (e: Exception) {
                        logger.error("Error in WebSocket communication", e)
                    }
                }
            } finally {
                // Cancel all coroutines when done
                scope.cancel()
            }
        } catch (e: Exception) {
            logger.error("Error handling WebSocket connection", e)
            serverSession.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Error handling WebSocket connection: ${e.message}"))
        }
    }

    /**
     * Installs the WebSockets plugin in the application.
     * @param application The Application to configure.
     */
    fun installWebSockets(application: Application) {
        application.install(io.ktor.server.websocket.WebSockets) {
            pingPeriodMillis = config.webSocket.pingInterval
            timeoutMillis = config.webSocket.timeout
        }

        logger.info("WebSockets plugin installed")
    }
}
