package com.example.reverseproxy.config

import io.ktor.server.config.*

/**
 * Configuration class for the reverse proxy server.
 * Loads settings from the application.yaml file.
 */
data class ReverseProxyConfig(
    val backends: List<BackendConfig>,
    val loadBalancing: LoadBalancingConfig,
    val circuitBreaker: CircuitBreakerConfig,
    val security: SecurityConfig,
    val webSocket: WebSocketConfig,
    val sse: SSEConfig
) {
    companion object {
        /**
         * Creates a ReverseProxyConfig instance from the application configuration.
         */
        fun fromConfig(config: ApplicationConfig): ReverseProxyConfig {
            val reverseProxyConfig = config.config("reverseProxy")

            return ReverseProxyConfig(
                backends = loadBackendConfigs(reverseProxyConfig.config("backends")),
                loadBalancing = loadLoadBalancingConfig(reverseProxyConfig.config("loadBalancing")),
                circuitBreaker = loadCircuitBreakerConfig(reverseProxyConfig.config("circuitBreaker")),
                security = loadSecurityConfig(reverseProxyConfig.config("security")),
                webSocket = loadWebSocketConfig(reverseProxyConfig.config("webSocket")),
                sse = loadSSEConfig(reverseProxyConfig.config("sse"))
            )
        }

        private fun loadBackendConfigs(config: ApplicationConfig): List<BackendConfig> {
            val backendsList = mutableListOf<BackendConfig>()
            val backendsArray = config.configList("item")

            for (backendConfig in backendsArray) {
                backendsList.add(
                    BackendConfig(
                        id = backendConfig.property("id").getString(),
                        url = backendConfig.property("url").getString(),
                        weight = backendConfig.propertyOrNull("weight")?.getString()?.toInt() ?: 1,
                        healthCheckPath = backendConfig.propertyOrNull("healthCheckPath")?.getString() ?: "/health"
                    )
                )
            }

            return backendsList
        }

        private fun loadLoadBalancingConfig(config: ApplicationConfig): LoadBalancingConfig {
            return LoadBalancingConfig(
                strategy = config.property("strategy").getString(),
                sessionSticky = config.propertyOrNull("sessionSticky")?.getString()?.toBoolean() ?: false,
                sessionCookieName = config.propertyOrNull("sessionCookieName")?.getString() ?: "BACKEND_ID"
            )
        }

        private fun loadCircuitBreakerConfig(config: ApplicationConfig): CircuitBreakerConfig {
            return CircuitBreakerConfig(
                failureRateThreshold = config.propertyOrNull("failureRateThreshold")?.getString()?.toInt() ?: 50,
                waitDurationInOpenState = config.propertyOrNull("waitDurationInOpenState")?.getString()?.toLong() ?: 60000,
                ringBufferSizeInClosedState = config.propertyOrNull("ringBufferSizeInClosedState")?.getString()?.toInt() ?: 10,
                ringBufferSizeInHalfOpenState = config.propertyOrNull("ringBufferSizeInHalfOpenState")?.getString()?.toInt() ?: 5,
                automaticTransitionFromOpenToHalfOpenEnabled = config.propertyOrNull("automaticTransitionFromOpenToHalfOpenEnabled")?.getString()?.toBoolean() ?: true
            )
        }

        private fun loadSecurityConfig(config: ApplicationConfig): SecurityConfig {
            return SecurityConfig(
                ssl = SslConfig(
                    enabled = config.config("ssl").propertyOrNull("enabled")?.getString()?.toBoolean() ?: false,
                    keyStorePath = config.config("ssl").propertyOrNull("keyStorePath")?.getString() ?: "keystore.jks",
                    keyStorePassword = config.config("ssl").propertyOrNull("keyStorePassword")?.getString() ?: "password"
                ),
                ipFiltering = IpFilteringConfig(
                    enabled = config.config("ipFiltering").propertyOrNull("enabled")?.getString()?.toBoolean() ?: false,
                    whitelistOnly = config.config("ipFiltering").propertyOrNull("whitelistOnly")?.getString()?.toBoolean() ?: true,
                    whitelist = config.config("ipFiltering").propertyOrNull("whitelist")?.getList() ?: listOf("127.0.0.1", "::1"),
                    blacklist = config.config("ipFiltering").propertyOrNull("blacklist")?.getList() ?: listOf()
                ),
                rateLimit = RateLimitConfig(
                    enabled = config.config("rateLimit").propertyOrNull("enabled")?.getString()?.toBoolean() ?: true,
                    limit = config.config("rateLimit").propertyOrNull("limit")?.getString()?.toInt() ?: 100,
                    window = config.config("rateLimit").propertyOrNull("window")?.getString()?.toInt() ?: 60
                )
            )
        }

        private fun loadWebSocketConfig(config: ApplicationConfig): WebSocketConfig {
            return WebSocketConfig(
                enabled = config.propertyOrNull("enabled")?.getString()?.toBoolean() ?: true,
                pingInterval = config.propertyOrNull("pingInterval")?.getString()?.toLong() ?: 30000,
                timeout = config.propertyOrNull("timeout")?.getString()?.toLong() ?: 15000
            )
        }

        private fun loadSSEConfig(config: ApplicationConfig): SSEConfig {
            return SSEConfig(
                enabled = config.propertyOrNull("enabled")?.getString()?.toBoolean() ?: true,
                reconnectTime = config.propertyOrNull("reconnectTime")?.getString()?.toLong() ?: 3000,
                eventBufferSize = config.propertyOrNull("eventBufferSize")?.getString()?.toInt() ?: 100,
                heartbeatInterval = config.propertyOrNull("heartbeatInterval")?.getString()?.toLong() ?: 15000
            )
        }
    }
}

/**
 * Configuration for a backend server.
 */
data class BackendConfig(
    val id: String,
    val url: String,
    val weight: Int,
    val healthCheckPath: String
)

/**
 * Configuration for load balancing.
 */
data class LoadBalancingConfig(
    val strategy: String,
    val sessionSticky: Boolean,
    val sessionCookieName: String
)

/**
 * Configuration for the circuit breaker.
 */
data class CircuitBreakerConfig(
    val failureRateThreshold: Int,
    val waitDurationInOpenState: Long,
    val ringBufferSizeInClosedState: Int,
    val ringBufferSizeInHalfOpenState: Int,
    val automaticTransitionFromOpenToHalfOpenEnabled: Boolean
)

/**
 * Configuration for security settings.
 */
data class SecurityConfig(
    val ssl: SslConfig,
    val ipFiltering: IpFilteringConfig,
    val rateLimit: RateLimitConfig
)

/**
 * Configuration for SSL/TLS.
 */
data class SslConfig(
    val enabled: Boolean,
    val keyStorePath: String,
    val keyStorePassword: String
)

/**
 * Configuration for IP filtering.
 */
data class IpFilteringConfig(
    val enabled: Boolean,
    val whitelistOnly: Boolean,
    val whitelist: List<String>,
    val blacklist: List<String>
)

/**
 * Configuration for rate limiting.
 */
data class RateLimitConfig(
    val enabled: Boolean,
    val limit: Int,
    val window: Int
)

/**
 * Configuration for WebSockets.
 */
data class WebSocketConfig(
    val enabled: Boolean,
    val pingInterval: Long,
    val timeout: Long
)
