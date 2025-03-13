package com.example.reverseproxy.handler

import com.example.reverseproxy.config.BackendConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

/**
 * Handles forwarding of HTTP requests to backend servers.
 */
class RequestForwarder(
    private val client: HttpClient
) : RequestForwarderInterface {
    private val logger = LoggerFactory.getLogger(RequestForwarder::class.java)

    /**
     * Forwards the current request to the specified backend server.
     * @param call The ApplicationCall containing the request and response.
     * @param backend The backend server to forward the request to.
     */
    override suspend fun forwardRequest(call: ApplicationCall, backend: BackendConfig) {
        val requestUrl = URLBuilder(backend.url).apply {
            encodedPath = call.request.path()
            parameters.appendAll(call.request.queryParameters)
        }.build()

        logger.info("Forwarding request to: $requestUrl")

        try {
            // Forward the request to the backend server
            val response = client.request(requestUrl) {
                method = call.request.httpMethod
                headers {
                    // Copy all headers from the original request except those that need special handling
                    call.request.headers.forEach { name, values ->
                        when (name.lowercase()) {
                            HttpHeaders.Host.lowercase() -> {} // Skip Host header as it will be set by the client
                            HttpHeaders.ContentLength.lowercase() -> {} // Skip Content-Length as it will be set by the client
                            HttpHeaders.TransferEncoding.lowercase() -> {} // Skip Transfer-Encoding as it will be set by the client
                            else -> values.forEach { value -> append(name, value) }
                        }
                    }

                    // Add X-Forwarded headers
                    val clientIp = call.request.header(HttpHeaders.XForwardedFor) ?: call.request.local.remoteHost
                    val scheme = call.request.header(HttpHeaders.XForwardedProto) ?: call.request.local.scheme

                    append(HttpHeaders.XForwardedFor, clientIp)
                    append(HttpHeaders.XForwardedProto, scheme)
                    append(HttpHeaders.XForwardedHost, call.request.host())
                    append(HttpHeaders.XForwardedPort, call.request.port().toString())

                    // Add custom header to identify the proxy
                    append("X-Proxy-ID", "Ktor-Reverse-Proxy")
                }

                // Copy the body from the original request
                if (call.request.httpMethod != HttpMethod.Get && call.request.httpMethod != HttpMethod.Head) {
                    val requestBody = call.receive<ByteArray>()
                    setBody(requestBody)
                }
            }

            // Forward the response back to the client
            call.response.status(response.status)

            // Copy response headers
            response.headers.forEach { name, values ->
                values.forEach { value ->
                    call.response.header(name, value)
                }
            }

            // Send the response body
            call.respondBytes(response.readBytes(), response.contentType() ?: ContentType.Application.OctetStream)

            logger.info("Request forwarded successfully to: $requestUrl")
        } catch (e: Exception) {
            logger.error("Error forwarding request to: $requestUrl", e)
            call.respond(HttpStatusCode.BadGateway, "Error forwarding request: ${e.message}")
        }
    }
}
