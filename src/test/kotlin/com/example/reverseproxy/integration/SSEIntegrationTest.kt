package com.example.reverseproxy.integration

import com.example.reverseproxy.config.ReverseProxyConfig
import com.example.reverseproxy.handler.SSEHandler
import com.example.reverseproxy.loadbalancer.LoadBalancerFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SSEIntegrationTest {

    private val logger = LoggerFactory.getLogger(SSEIntegrationTest::class.java)

    // Backend server port
    private val backendPort = 8086

    // Backend server
    private lateinit var backendServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    // Event counter to verify events are received
    private val eventCounter = AtomicInteger(0)

    @Before
    fun setUp() {
        // Start SSE backend server
        backendServer = startBackendServer(backendPort)
    }

    @After
    fun tearDown() {
        // Stop backend server
        backendServer.stop(1000, 2000)
        logger.info("SSE backend server stopped")
    }

    /**
     * Starts a backend server that provides SSE events.
     */
    private fun startBackendServer(port: Int): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return embeddedServer(Netty, port = port) {
            routing {
                // SSE endpoint
                get("/events") {
                    call.response.cacheControl(CacheControl.NoCache(null))
                    call.response.header("Connection", "keep-alive")
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        // Send retry directive
                        write("retry: 3000\n\n")
                        flush()

                        // Send a series of events
                        for (i in 1..5) {
                            // Event with ID
                            write("id: $i\n")
                            // Event type
                            write("event: message\n")
                            // Event data
                            write("data: Event $i payload\n\n")
                            flush()

                            delay(500) // Delay between events
                        }

                        // Send an event with multiple data lines
                        write("id: 6\n")
                        write("event: complex\n")
                        write("data: Line 1 of complex event\n")
                        write("data: Line 2 of complex event\n")
                        write("data: Line 3 of complex event\n\n")
                        flush()

                        // Keep the connection open for a while
                        delay(2000)
                    }
                }

                // Health check endpoint
                get("/health") {
                    call.respondText("OK")
                }
            }
        }.start(wait = false).also {
            logger.info("SSE backend server started on port $port")
        }
    }

    /**
     * Minimal module function for testing that only sets up SSE support.
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

        // Create SSE handler
        val sseHandler = SSEHandler(reverseProxyConfig, loadBalancer, client)

        // Log startup information
        val logger = LoggerFactory.getLogger("TestApplication")
        logger.info("Test reverse proxy started with ${reverseProxyConfig.backends.size} backend servers")
        logger.info("Backend URLs: ${reverseProxyConfig.backends.map { it.url }}")

        // Configure routing
        routing {
            // Configure SSE routing
            sseHandler.configureRouting(this)
        }
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
                    url: "http://localhost:$backendPort"
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

    @Test
    fun `test direct sse event reception`() {
        // Create a client
        val client = HttpClient(CIO)

        try {
            runBlocking {
                // Connect directly to the backend SSE endpoint
                val response = client.get("http://localhost:$backendPort/events") {
                    headers {
                        append(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                    }
                }

                // Verify response headers
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith(ContentType.Text.EventStream.toString()) == true, 
                    "Expected Content-Type to start with ${ContentType.Text.EventStream}, but was ${response.headers[HttpHeaders.ContentType]}")
                assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
                assertEquals("keep-alive", response.headers["Connection"])

                // Process the response body
                val responseText = response.bodyAsText()
                logger.info("Received SSE response directly from backend: $responseText")

                // Verify the response contains expected events
                assertTrue(responseText.contains("id: 1"))
                assertTrue(responseText.contains("event: message"))
                assertTrue(responseText.contains("data: Event 1 payload"))

                // Verify all 5 simple events are present
                for (i in 1..5) {
                    assertTrue(responseText.contains("id: $i"), "Missing event with id $i")
                    assertTrue(responseText.contains("data: Event $i payload"), "Missing data for event $i")
                }

                // Verify the complex event
                assertTrue(responseText.contains("id: 6"))
                assertTrue(responseText.contains("event: complex"))
                assertTrue(responseText.contains("data: Line 1 of complex event"))
                assertTrue(responseText.contains("data: Line 2 of complex event"))
                assertTrue(responseText.contains("data: Line 3 of complex event"))

                // Verify the retry directive
                assertTrue(responseText.contains("retry: 3000"))
            }

            logger.info("Direct SSE test completed successfully")
        } finally {
            client.close()
        }
    }

    @Test
    fun `test sse event forwarding through reverse proxy`() = testApplication {
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

            // Send a request to the reverse proxy with Accept header for SSE
            val response = client.get("/events") {
                headers {
                    append(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                }
            }

            // Verify response headers
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith(ContentType.Text.EventStream.toString()) == true, 
                "Expected Content-Type to start with ${ContentType.Text.EventStream}, but was ${response.headers[HttpHeaders.ContentType]}")
            assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
            assertEquals("keep-alive", response.headers["Connection"])

            // Process the response body
            val responseText = response.bodyAsText()
            println("[DEBUG_LOG] Received SSE response through proxy: $responseText")

            // Verify the response contains expected events
            assertTrue(responseText.contains("id: 1"), "Missing event with id 1")
            assertTrue(responseText.contains("event: message"), "Missing event type")
            assertTrue(responseText.contains("data: Event 1 payload"), "Missing data for event 1")

            // Verify all 5 simple events are present
            for (i in 1..5) {
                assertTrue(responseText.contains("id: $i"), "Missing event with id $i")
                assertTrue(responseText.contains("data: Event $i payload"), "Missing data for event $i")
            }

            // Verify the complex event
            assertTrue(responseText.contains("id: 6"), "Missing event with id 6")
            assertTrue(responseText.contains("event: complex"), "Missing complex event type")
            assertTrue(responseText.contains("data: Line 1 of complex event"), "Missing line 1 of complex event")
            assertTrue(responseText.contains("data: Line 2 of complex event"), "Missing line 2 of complex event")
            assertTrue(responseText.contains("data: Line 3 of complex event"), "Missing line 3 of complex event")

            // Verify the retry directive
            assertTrue(responseText.contains("retry: 3000"), "Missing retry directive")

            println("[DEBUG_LOG] SSE proxy test completed successfully")
        } finally {
            // Clean up the temporary file
            configFile.delete()
        }
    }
}
