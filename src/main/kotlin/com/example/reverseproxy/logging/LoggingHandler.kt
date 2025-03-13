package com.example.reverseproxy.logging

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID

/**
 * Handles logging for the reverse proxy.
 */
class LoggingHandler {
    private val logger = LoggerFactory.getLogger(LoggingHandler::class.java)

    /**
     * Configures logging for the application.
     * @param application The Application to configure.
     */
    fun configureLogging(application: Application) {
        // Install the CallLogging plugin
        application.install(CallLogging) {
            level = org.slf4j.event.Level.INFO

            // Add custom MDC values
            mdc("correlationId") { call -> call.request.header("X-Correlation-ID") ?: UUID.randomUUID().toString() }
            mdc("clientIp") { call -> call.request.local.remoteHost }
            mdc("method") { call -> call.request.httpMethod.value }
            mdc("path") { call -> call.request.path() }
            mdc("userAgent") { call -> call.request.userAgent() ?: "Unknown" }
        }

        // Add an interceptor for request/response logging
        application.intercept(ApplicationCallPipeline.Monitoring) {
            // Log request details
            val requestId = UUID.randomUUID().toString()
            MDC.put("requestId", requestId)

            val startTime = System.currentTimeMillis()

            logger.info("Request received: ${call.request.httpMethod.value} ${call.request.path()} from ${call.request.local.remoteHost}")

            try {
                // Process the request
                proceed()

                // Log response details
                val responseTime = System.currentTimeMillis() - startTime
                MDC.put("responseTime", responseTime.toString())
                MDC.put("statusCode", call.response.status()?.value?.toString() ?: "Unknown")

                logger.info("Response sent: ${call.response.status()?.value} in ${responseTime}ms")
            } catch (e: Exception) {
                // Log error details
                logger.error("Error processing request: ${e.message}", e)
                throw e
            } finally {
                // Clean up MDC
                MDC.remove("requestId")
                MDC.remove("responseTime")
                MDC.remove("statusCode")
            }
        }

        logger.info("Logging configured")
    }

    /**
     * Logs a message with the specified level.
     * @param level The log level.
     * @param message The message to log.
     * @param throwable The throwable to log (optional).
     */
    fun log(level: org.slf4j.event.Level, message: String, throwable: Throwable? = null) {
        when (level) {
            org.slf4j.event.Level.ERROR -> logger.error(message, throwable)
            org.slf4j.event.Level.WARN -> logger.warn(message, throwable)
            org.slf4j.event.Level.INFO -> logger.info(message, throwable)
            org.slf4j.event.Level.DEBUG -> logger.debug(message, throwable)
            org.slf4j.event.Level.TRACE -> logger.trace(message, throwable)
        }
    }
}
