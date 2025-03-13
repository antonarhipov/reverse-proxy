package com.example.reverseproxy.security

import com.example.reverseproxy.config.SecurityConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * Handles security features for the reverse proxy.
 */
class SecurityHandler(
    private val config: SecurityConfig
) {
    private val logger = LoggerFactory.getLogger(SecurityHandler::class.java)

    /**
     * Configures security features for the application.
     * @param application The Application to configure.
     */
    fun configureSecurity(application: Application) {
        // Configure SSL/TLS if enabled
        configureSsl(application)

        // Configure IP filtering if enabled
        configureIpFiltering(application)

        // Configure rate limiting if enabled
        configureRateLimit(application)

        // Configure CORS
        configureCors(application)

        // Configure request validation and sanitization
        configureRequestValidation(application)

        logger.info("Security features configured")
    }

    /**
     * Configures SSL/TLS for the application.
     * @param application The Application to configure.
     */
    private fun configureSsl(application: Application) {
        if (!config.ssl.enabled) {
            logger.info("SSL/TLS is disabled")
            return
        }

        // SSL/TLS is configured in the application.conf file
        // The Ktor server will automatically use these settings
        logger.info("SSL/TLS is enabled with keystore: ${config.ssl.keyStorePath}")
    }

    /**
     * Configures IP filtering for the application.
     * @param application The Application to configure.
     */
    private fun configureIpFiltering(application: Application) {
        if (!config.ipFiltering.enabled) {
            logger.info("IP filtering is disabled")
            return
        }

        // Add an interceptor to filter requests by IP
        application.intercept(ApplicationCallPipeline.Plugins) {
            val remoteHost = call.request.local.remoteHost

            if (config.ipFiltering.whitelistOnly) {
                // Whitelist mode: only allow IPs in the whitelist
                if (!config.ipFiltering.whitelist.contains(remoteHost)) {
                    logger.warn("Blocked request from non-whitelisted IP: $remoteHost")
                    call.respond(HttpStatusCode.Forbidden, "Access denied")
                    return@intercept finish()
                }
            } else {
                // Blacklist mode: block IPs in the blacklist
                if (config.ipFiltering.blacklist.contains(remoteHost)) {
                    logger.warn("Blocked request from blacklisted IP: $remoteHost")
                    call.respond(HttpStatusCode.Forbidden, "Access denied")
                    return@intercept finish()
                }
            }
        }

        logger.info("IP filtering is enabled with whitelist mode: ${config.ipFiltering.whitelistOnly}")
    }

    /**
     * Configures rate limiting for the application.
     * @param application The Application to configure.
     */
    private fun configureRateLimit(application: Application) {
        if (!config.rateLimit.enabled) {
            logger.info("Rate limiting is disabled")
            return
        }

        // Install the RateLimit plugin
        application.install(RateLimit) {
            // Configure global rate limit
            global {
                // Set the rate limit based on configuration
                rateLimiter(limit = config.rateLimit.limit, refillPeriod = config.rateLimit.window.seconds)
            }
        }

        logger.info("Rate limiting is enabled with limit: ${config.rateLimit.limit} requests per ${config.rateLimit.window} seconds")
    }

    /**
     * Configures CORS for the application.
     * @param application The Application to configure.
     */
    private fun configureCors(application: Application) {
        // Install the CORS plugin
        application.install(CORS) {
            // Allow all hosts for now (can be restricted in production)
            anyHost()

            // Allow common HTTP methods
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)

            // Allow common headers
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)

            // Allow credentials
            allowCredentials = true

            // Allow non-simple content types
            allowNonSimpleContentTypes = true
        }

        logger.info("CORS is configured")
    }

    /**
     * Configures request validation and sanitization for the application.
     * @param application The Application to configure.
     */
    private fun configureRequestValidation(application: Application) {
        // Add an interceptor to validate and sanitize requests
        application.intercept(ApplicationCallPipeline.Plugins) {
            // Validate request method
            val method = call.request.httpMethod
            if (method !in listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete, HttpMethod.Options)) {
                logger.warn("Blocked request with unsupported method: $method")
                call.respond(HttpStatusCode.MethodNotAllowed, "Method not allowed")
                return@intercept finish()
            }

            // Validate content type for requests with body
            if (method in listOf(HttpMethod.Post, HttpMethod.Put)) {
                val contentType = call.request.contentType()
                if (contentType.contentType != "application" && contentType.contentType != "multipart") {
                    logger.warn("Blocked request with unsupported content type: $contentType")
                    call.respond(HttpStatusCode.UnsupportedMediaType, "Unsupported media type")
                    return@intercept finish()
                }
            }

            // Validate URL path (prevent path traversal attacks)
            val path = call.request.path()
            if (path.contains("..") || path.contains("//")) {
                logger.warn("Blocked request with suspicious path: $path")
                call.respond(HttpStatusCode.BadRequest, "Invalid path")
                return@intercept finish()
            }

            // Validate query parameters (prevent SQL injection)
            val queryParams = call.request.queryParameters
            for ((key, values) in queryParams.entries()) {
                for (value in values) {
                    if (value.contains("'") || value.contains("\"") || value.contains(";") || value.contains("--")) {
                        logger.warn("Blocked request with suspicious query parameter: $key=$value")
                        call.respond(HttpStatusCode.BadRequest, "Invalid query parameter")
                        return@intercept finish()
                    }
                }
            }
        }

        logger.info("Request validation and sanitization is configured")
    }

}
