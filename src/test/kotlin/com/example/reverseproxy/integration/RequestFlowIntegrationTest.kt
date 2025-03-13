package com.example.reverseproxy.integration

import com.example.reverseproxy.circuitbreaker.CircuitBreakerEventLogger
import com.example.reverseproxy.circuitbreaker.CircuitBreakerRequestForwarder
import com.example.reverseproxy.config.BackendConfig
import com.example.reverseproxy.config.ReverseProxyConfig
import com.example.reverseproxy.handler.ProxyHandler
import com.example.reverseproxy.handler.RequestForwarder
import com.example.reverseproxy.handler.SSEHandler
import com.example.reverseproxy.handler.WebSocketHandler
import com.example.reverseproxy.loadbalancer.LoadBalancerFactory
import com.example.reverseproxy.metrics.MetricsHandler
import com.example.reverseproxy.security.SecurityHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestFlowIntegrationTest {

    // Backend server ports
    private val backend1Port = 8081
    private val backend2Port = 8082

    // Backend servers
    private lateinit var backend1: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var backend2: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    @Before
    fun setUp() {
        // Start backend servers
        backend1 = startBackendServer(backend1Port, "Backend 1")
        backend2 = startBackendServer(backend2Port, "Backend 2")
    }

    @After
    fun tearDown() {
        // Stop backend servers
        backend1.stop(1000, 2000)
        backend2.stop(1000, 2000)
    }

    /**
     * Minimal module function for testing that only sets up the essential components.
     */
    private fun Application.testModule() {
        // Load configuration
        val config = environment.config
        val reverseProxyConfig = ReverseProxyConfig.fromConfig(config)

        // Create HTTP client
        val client = HttpClient(CIO)

        // Create load balancer
        val loadBalancer = LoadBalancerFactory.createLoadBalancer(
            reverseProxyConfig.backends,
            reverseProxyConfig.loadBalancing
        )

        // Create request forwarder
        val requestForwarder = RequestForwarder(client)

        // Log startup information
        val logger = LoggerFactory.getLogger("TestApplication")
        logger.info("Test reverse proxy started with ${reverseProxyConfig.backends.size} backend servers")
        logger.info("Backend URLs: ${reverseProxyConfig.backends.map { it.url }}")

        // Configure routing
        routing {
            // Add a specific route for the test path
            get("/test") {
                logger.info("Handling /test request")
                try {
                    // Select a backend server using the load balancer
                    val backend = loadBalancer.selectBackend()
                    logger.info("Selected backend: ${backend.id} (${backend.url})")

                    // Forward the request to the selected backend
                    requestForwarder.forwardRequest(call, backend)
                } catch (e: Exception) {
                    logger.error("Error handling request", e)
                    call.respond(HttpStatusCode.InternalServerError, "Error handling request: ${e.message}")
                }
            }
        }
    }

    @Test
    fun `test complete request flow`() = testApplication {
        // Create a temporary config file
        val configFile = createTempConfigFile()

        try {
            // Configure the test application
            environment {
                config = ApplicationConfig(configFile.absolutePath)
            }

            // Install the test module
            application {
                testModule()
            }

            // Create a client
            val client = createClient {
                expectSuccess = false
            }

            // Send a request to the reverse proxy
            val response = client.get("/test")

            // Print the response for debugging
            val responseBody = response.bodyAsText()
            println("[DEBUG_LOG] Response status: ${response.status}")
            println("[DEBUG_LOG] Response body: $responseBody")

            // Verify the response
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(responseBody.contains("Response from Backend 1") || responseBody.contains("Response from Backend 2"), "Response body does not contain expected content: $responseBody")
        } finally {
            // Clean up the temporary file
            configFile.delete()
        }
    }

    /**
     * Starts a backend server on the specified port.
     */
    private fun startBackendServer(port: Int, name: String): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return embeddedServer(Netty, port = port) {
            routing {
                get("/test") {
                    call.respondText("Response from $name")
                }
                get("/health") {
                    call.respondText("OK")
                }
            }
        }.start(wait = false)
    }

    /**
     * Creates a temporary YAML configuration file for testing.
     */
    private fun createTempConfigFile(): File {
        val configContent = """
            ktor:
              application:
                modules:
                  - com.example.ApplicationKt.module
              deployment:
                port: 8080

            reverseProxy:
              backends:
                item:
                  - id: "backend1"
                    url: "http://localhost:$backend1Port"
                    weight: 1
                    healthCheckPath: "/health"
                  - id: "backend2"
                    url: "http://localhost:$backend2Port"
                    weight: 1
                    healthCheckPath: "/health"

              loadBalancing:
                strategy: "round-robin"
                sessionSticky: false
                sessionCookieName: "BACKEND_ID"

              circuitBreaker:
                failureRateThreshold: 50
                waitDurationInOpenState: 60000
                ringBufferSizeInClosedState: 10
                ringBufferSizeInHalfOpenState: 5
                automaticTransitionFromOpenToHalfOpenEnabled: true

              security:
                ssl:
                  enabled: false
                  keyStorePath: "keystore.jks"
                  keyStorePassword: "password"
                ipFiltering:
                  enabled: false
                  whitelistOnly: true
                  whitelist: ["127.0.0.1", "::1"]
                  blacklist: []
                rateLimit:
                  enabled: false
                  limit: 100
                  window: 60

              webSocket:
                enabled: true
                pingInterval: 30000
                timeout: 15000

              sse:
                enabled: true
                reconnectTime: 3000
                eventBufferSize: 100
                heartbeatInterval: 15000
        """.trimIndent()

        val tempFile = File.createTempFile("test-config", ".yaml")
        tempFile.writeText(configContent)
        return tempFile
    }
}
