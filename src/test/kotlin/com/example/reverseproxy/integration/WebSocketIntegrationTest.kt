package com.example.reverseproxy.integration

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketIntegrationTest {

    private val logger = LoggerFactory.getLogger(WebSocketIntegrationTest::class.java)

    // Server port
    private val serverPort = 8085

    // Server
    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    @Before
    fun setUp() {
        // Start WebSocket server
        server = embeddedServer(Netty, port = serverPort) {
            install(io.ktor.server.websocket.WebSockets)

            routing {
                webSocket("/echo") {
                    logger.info("WebSocket connection established")

                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                logger.info("Server received: $text")
                                send(Frame.Text("Echo: $text"))
                            }
                            else -> {
                                logger.info("Server received unsupported frame type: $frame")
                            }
                        }
                    }
                }
            }
        }.start(wait = false)

        logger.info("WebSocket server started on port $serverPort")
    }

    @After
    fun tearDown() {
        // Stop server
        server.stop(1000, 2000)
        logger.info("WebSocket server stopped")
    }

    @Test
    fun `test websocket bidirectional communication`() {
        // Create a client with WebSocket support
        val client = HttpClient(CIO) {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        try {
            runBlocking {
                // Connect to the WebSocket endpoint
                client.webSocket("ws://localhost:$serverPort/echo") {
                    // Send a message to the server
                    send(Frame.Text("Hello from client"))

                    // Receive the echoed message
                    val response = incoming.receive()

                    // Print the response for debugging
                    logger.info("Client received: $response")

                    // Verify the response
                    assertTrue(response is Frame.Text)
                    assertEquals("Echo: Hello from client", (response as Frame.Text).readText())
                }
            }

            logger.info("WebSocket test completed successfully")
        } finally {
            client.close()
        }
    }
}
