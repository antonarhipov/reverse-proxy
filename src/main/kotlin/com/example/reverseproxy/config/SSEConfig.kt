package com.example.reverseproxy.config

/**
 * Configuration for Server-Sent Events (SSE).
 */
data class SSEConfig(
    val enabled: Boolean,
    val reconnectTime: Long,
    val eventBufferSize: Int,
    val heartbeatInterval: Long
)