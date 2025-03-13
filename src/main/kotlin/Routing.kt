package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.*

/**
 * This function is no longer used as routing is now configured in the configureReverseProxy function.
 * It's kept for backward compatibility.
 */
fun Application.configureRouting() {
    // Routing is now configured in the configureReverseProxy function
}
