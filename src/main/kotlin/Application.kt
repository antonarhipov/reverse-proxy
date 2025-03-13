package com.example

import com.example.reverseproxy.circuitbreaker.CircuitBreakerEventLogger
import com.example.reverseproxy.circuitbreaker.CircuitBreakerRequestForwarder
import com.example.reverseproxy.config.ReverseProxyConfig
import com.example.reverseproxy.handler.ProxyHandler
import com.example.reverseproxy.handler.RequestForwarder
import com.example.reverseproxy.handler.SSEHandler
import com.example.reverseproxy.handler.WebSocketHandler
import com.example.reverseproxy.loadbalancer.LoadBalancerFactory
import com.example.reverseproxy.logging.LoggingHandler
import com.example.reverseproxy.metrics.MetricsHandler
import com.example.reverseproxy.security.SecurityHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Configure reverse proxy
    configureReverseProxy()
}

/**
 * Configures the reverse proxy components and routing.
 */
fun Application.configureReverseProxy() {
    // Load configuration
    val config = environment.config
    val reverseProxyConfig = ReverseProxyConfig.fromConfig(config)

    // Configure logging
    val loggingHandler = LoggingHandler()
    loggingHandler.configureLogging(this)

    // Configure metrics collection
    val metricsHandler = MetricsHandler()
    metricsHandler.configureMetrics(this)

    // Create circuit breaker event logger
    val circuitBreakerEventLogger = CircuitBreakerEventLogger(metricsHandler.getMetricRegistry())

    // Configure security features
    val securityHandler = SecurityHandler(reverseProxyConfig.security)
    securityHandler.configureSecurity(this)

    // Create HTTP client with WebSocket support
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    // Create load balancer
    val loadBalancer = LoadBalancerFactory.createLoadBalancer(
        reverseProxyConfig.backends,
        reverseProxyConfig.loadBalancing
    )

    // Create request forwarder
    val requestForwarder = RequestForwarder(client)

    // Create circuit breaker request forwarder
    val circuitBreakerRequestForwarder = CircuitBreakerRequestForwarder(
        requestForwarder,
        reverseProxyConfig.circuitBreaker,
        circuitBreakerEventLogger
    )

    // Create proxy handler
    val proxyHandler = ProxyHandler(reverseProxyConfig, loadBalancer, circuitBreakerRequestForwarder)

    // Create WebSocket handler
    val webSocketHandler = WebSocketHandler(reverseProxyConfig, loadBalancer, client)

    // Create SSE handler
    val sseHandler = SSEHandler(reverseProxyConfig, loadBalancer, client)

    // Install WebSockets plugin
    if (reverseProxyConfig.webSocket.enabled) {
        webSocketHandler.installWebSockets(this)
    }

    // Configure routing
    routing {
        // Configure HTTP routing
        proxyHandler.configureRouting(this)

        // Configure WebSocket routing if enabled
        if (reverseProxyConfig.webSocket.enabled) {
            webSocketHandler.configureRouting(this)
        }

        // Configure SSE routing if enabled
        if (reverseProxyConfig.sse.enabled) {
            sseHandler.configureRouting(this)
        }
    }

    // Log startup information
    val logger = LoggerFactory.getLogger("Application")
    logger.info("Reverse proxy started with ${reverseProxyConfig.backends.size} backend servers")
    logger.info("Load balancing strategy: ${reverseProxyConfig.loadBalancing.strategy}")
    logger.info("Circuit breaker enabled with failure threshold: ${reverseProxyConfig.circuitBreaker.failureRateThreshold}%")

    // Log security information
    logger.info("SSL/TLS is ${if (reverseProxyConfig.security.ssl.enabled) "enabled" else "disabled"}")
    logger.info("IP filtering is ${if (reverseProxyConfig.security.ipFiltering.enabled) "enabled" else "disabled"}")
    logger.info("Rate limiting is ${if (reverseProxyConfig.security.rateLimit.enabled) "enabled with limit: ${reverseProxyConfig.security.rateLimit.limit} requests per ${reverseProxyConfig.security.rateLimit.window} seconds" else "disabled"}")

    if (reverseProxyConfig.webSocket.enabled) {
        logger.info("WebSocket support is enabled")
    } else {
        logger.info("WebSocket support is disabled")
    }

    if (reverseProxyConfig.sse.enabled) {
        logger.info("Server-Sent Events (SSE) support is enabled")
    } else {
        logger.info("Server-Sent Events (SSE) support is disabled")
    }
}
