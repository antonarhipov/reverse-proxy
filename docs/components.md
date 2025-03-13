# Reverse Proxy Components

This document provides detailed information about the components of the Reverse Proxy Server.

## Table of Contents

- [Load Balancer](#load-balancer)
- [Request Forwarder](#request-forwarder)
- [Circuit Breaker](#circuit-breaker)
- [WebSocket Support](#websocket-support)
- [Server-Sent Events (SSE) Support](#server-sent-events-sse-support)
- [Security Features](#security-features)
- [Logging and Monitoring](#logging-and-monitoring)

## Load Balancer

The Load Balancer component is responsible for distributing requests among multiple backend servers. It is implemented in the `com.example.reverseproxy.loadbalancer` package.

### Interfaces and Classes

- **LoadBalancer** (Interface): Defines the contract for load balancers
- **AbstractLoadBalancer**: Base class for load balancer implementations
- **RoundRobinLoadBalancer**: Implements round-robin load balancing strategy
- **RandomLoadBalancer**: Implements random load balancing strategy
- **LoadBalancerFactory**: Creates load balancer instances based on configuration

### Load Balancing Strategies

#### Round-Robin Strategy

The Round-Robin strategy distributes requests sequentially among backend servers. It maintains a counter to keep track of the next backend to use.

```kotlin
override fun selectBackend(): BackendConfig {
    val availableBackends = getAvailableBackends()

    if (availableBackends.isEmpty()) {
        throw IllegalStateException("No available backend servers")
    }

    // Get the next index in a thread-safe way
    val index = counter.getAndIncrement() % availableBackends.size

    // Handle integer overflow
    if (counter.get() >= Integer.MAX_VALUE - 1000) {
        counter.set(0)
    }

    return availableBackends[index]
}
```

#### Random Strategy

The Random strategy selects a backend server randomly from the available backends.

```kotlin
override fun selectBackend(): BackendConfig {
    val availableBackends = getAvailableBackends()

    if (availableBackends.isEmpty()) {
        throw IllegalStateException("No available backend servers")
    }

    // Select a random backend from the available ones
    val randomIndex = Random.nextInt(availableBackends.size)
    return availableBackends[randomIndex]
}
```

### Backend Availability

The Load Balancer tracks the availability of backend servers. Backends can be marked as failed or available:

```kotlin
override fun markBackendAsFailed(backendId: String) {
    availabilityMap[backendId] = false
}

override fun markBackendAsAvailable(backendId: String) {
    availabilityMap[backendId] = true
}
```

## Request Forwarder

The Request Forwarder component is responsible for forwarding HTTP requests to backend servers. It is implemented in the `com.example.reverseproxy.handler` package.

### Interfaces and Classes

- **RequestForwarderInterface** (Interface): Defines the contract for request forwarders
- **RequestForwarder**: Implements the request forwarding logic
- **ProxyHandler**: Handles incoming requests and uses the request forwarder

### Request Forwarding Process

1. Construct a new request mirroring the client's request
2. Forward the request to the backend server
3. Return the backend response to the client

```kotlin
suspend fun forwardRequest(call: ApplicationCall, backend: BackendConfig) {
    val requestUrl = URLBuilder(backend.url).apply {
        encodedPath = call.request.path()
        parameters.appendAll(call.request.queryParameters)
    }.build()

    // Forward the request to the backend server
    val response = client.request(requestUrl) {
        method = call.request.httpMethod
        headers {
            // Copy headers from the original request
            // Add X-Forwarded headers
        }

        // Copy the body from the original request
        if (call.request.httpMethod != HttpMethod.Get && call.request.httpMethod != HttpMethod.Head) {
            val requestBody = call.receive<ByteArray>()
            setBody(requestBody)
        }
    }

    // Forward the response back to the client
    call.response.status(response.status)
    call.respondBytes(response.readBytes(), response.contentType() ?: ContentType.Application.OctetStream)
}
```

## Circuit Breaker

The Circuit Breaker component prevents cascading failures by detecting backend issues and providing fallback responses. It is implemented in the `com.example.reverseproxy.circuitbreaker` package.

### Interfaces and Classes

- **SimpleCircuitBreaker**: Implements the circuit breaker pattern
- **CircuitBreakerRequestForwarder**: Wraps a request forwarder with circuit breaker functionality
- **CircuitBreakerEventLogger**: Logs circuit breaker events

### Circuit Breaker States

The Circuit Breaker has three states:

1. **CLOSED**: Normal operation, requests are allowed
2. **OPEN**: Circuit is open, requests are not allowed
3. **HALF_OPEN**: Testing if the service is back, allowing a single request

### Circuit Breaker Operation

```kotlin
fun <T> execute(fallback: (Exception) -> T, block: () -> T): T {
    // Check if the circuit is open
    when (state.get()) {
        State.OPEN -> {
            // Check if the reset timeout has elapsed
            val now = Instant.now()
            if (openTime != null && now.isAfter(openTime!!.plusMillis(resetTimeoutMs))) {
                // Transition to half-open state
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    logger.info("Circuit breaker transitioning from OPEN to HALF_OPEN")
                    eventLogger?.logStateTransition(name ?: "unnamed", State.OPEN, State.HALF_OPEN)
                }
            } else {
                // Circuit is still open, execute fallback
                logger.debug("Circuit is OPEN, executing fallback")
                eventLogger?.logCallNotPermitted(name ?: "unnamed")
                return fallback(CircuitOpenException("Circuit is open"))
            }
        }
        else -> {
            // Continue with normal execution
        }
    }

    return try {
        // Execute the function
        val result = block()

        // If successful and in half-open state, transition to closed
        if (state.get() == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                logger.info("Circuit breaker transitioning from HALF_OPEN to CLOSED")
                eventLogger?.logStateTransition(name ?: "unnamed", State.HALF_OPEN, State.CLOSED)
                failureCount.set(0)
            }
        } else if (state.get() == State.CLOSED) {
            // Reset failure count on success in closed state
            failureCount.set(0)
        }

        // Log success
        eventLogger?.logSuccess(name ?: "unnamed")

        result
    } catch (e: Exception) {
        // Record the failure
        val currentFailures = failureCount.incrementAndGet()
        logger.error("Circuit breaker recorded failure: ${e.message}, count: $currentFailures")
        eventLogger?.logError(name ?: "unnamed", e.message ?: "Unknown error", currentFailures)

        // If we've reached the threshold, open the circuit
        if (state.get() == State.CLOSED && currentFailures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                logger.info("Circuit breaker transitioning from CLOSED to OPEN after $currentFailures consecutive failures")
                eventLogger?.logStateTransition(name ?: "unnamed", State.CLOSED, State.OPEN)
                openTime = Instant.now()
            }
        } else if (state.get() == State.HALF_OPEN) {
            // If we fail in half-open state, go back to open
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                logger.info("Circuit breaker transitioning from HALF_OPEN to OPEN after a failure")
                eventLogger?.logStateTransition(name ?: "unnamed", State.HALF_OPEN, State.OPEN)
                openTime = Instant.now()
            }
        }

        // Execute fallback
        fallback(e)
    }
}
```

## WebSocket Support

The WebSocket Support component enables forwarding WebSocket connections to backend servers. It is implemented in the `com.example.reverseproxy.handler` package.

### Classes

- **WebSocketHandler**: Handles WebSocket connections and forwards them to backend servers

### WebSocket Handling Process

1. Client initiates a WebSocket connection to the proxy
2. Proxy selects a backend server using the load balancer
3. Proxy establishes a WebSocket connection to the backend server
4. Proxy forwards frames bidirectionally between the client and the backend server

```kotlin
fun configureRouting(routing: Routing) {
    // Only set up WebSocket routes if WebSocket support is enabled
    if (!config.webSocket.enabled) {
        logger.info("WebSocket support is disabled")
        return
    }

    // Set up WebSocket route for any path
    routing.webSocket("{path...}") {
        handleWebSocketConnection(this, call)
    }

    logger.info("WebSocket routes configured with ping interval: ${config.webSocket.pingInterval}ms")
}
```

## Server-Sent Events (SSE) Support

The Server-Sent Events (SSE) Support component enables forwarding SSE connections to backend servers. It is implemented in the `com.example.reverseproxy.handler` package.

### Classes

- **SSEHandler**: Handles SSE connections and forwards them to backend servers

### SSE Handling Process

1. Client initiates an SSE connection to the proxy
2. Proxy selects a backend server using the load balancer
3. Proxy establishes an SSE connection to the backend server
4. Proxy forwards events from the backend server to the client

```kotlin
fun configureRouting(routing: Routing) {
    // Only set up SSE routes if SSE support is enabled
    if (!config.sse.enabled) {
        logger.info("SSE support is disabled")
        return
    }

    // Set up SSE route for any path
    routing.route("{path...}") {
        get {
            // Check if this is an SSE request by looking at the Accept header
            val acceptHeader = call.request.headers[HttpHeaders.Accept]
            if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
                handleSSEConnection(call)
            }
        }
    }

    logger.info("SSE routes configured with reconnect time: ${config.sse.reconnectTime}ms")
}
```

## Security Features

The Security Features component implements various security measures. It is implemented in the `com.example.reverseproxy.security` package.

### Classes

- **SecurityHandler**: Configures and applies security features

### Security Features

#### SSL/TLS Termination

SSL/TLS termination is configured in the application.yaml file and handled by Ktor's SSL support.

#### IP Filtering

IP filtering allows or blocks requests based on client IP addresses. It supports whitelist and blacklist modes.

```kotlin
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
```

#### Rate Limiting

Rate limiting prevents abuse by limiting the number of requests from a client within a time window.

```kotlin
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
```

#### Request Validation

Request validation checks for suspicious or malicious requests.

```kotlin
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

        // Validate URL path (prevent path traversal attacks)
        val path = call.request.path()
        if (path.contains("..") || path.contains("//")) {
            logger.warn("Blocked request with suspicious path: $path")
            call.respond(HttpStatusCode.BadRequest, "Invalid path")
            return@intercept finish()
        }
    }
}
```

## Logging and Monitoring

The Logging and Monitoring components provide observability for the Reverse Proxy Server. They are implemented in the `com.example.reverseproxy.logging` and `com.example.reverseproxy.metrics` packages.

### Classes

- **LoggingHandler**: Configures and applies logging features
- **MetricsHandler**: Configures and applies metrics collection
- **CircuitBreakerEventLogger**: Logs circuit breaker events

### Logging

The Logging component uses SLF4J with Logback for structured logging. It captures request details, errors, and events.

```kotlin
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
```

### Metrics Collection

The Metrics component uses Dropwizard Metrics for metrics collection. It captures performance metrics such as request rates, response times, and error rates.

```kotlin
fun configureMetrics(application: Application) {
    // Register JVM metrics
    registerJvmMetrics()

    // Install the DropwizardMetrics plugin
    application.install(DropwizardMetrics) {
        // Configure the registry
        registry = metricRegistry

        // Configure SLF4J reporter
        val slf4jReporter = Slf4jReporter.forRegistry(metricRegistry)
            .outputTo(LoggerFactory.getLogger("metrics"))
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build()
        slf4jReporter.start(1, TimeUnit.MINUTES)
    }

    // Add an interceptor for custom metrics
    application.intercept(ApplicationCallPipeline.Monitoring) {
        // Record request metrics
        when (call.request.local.method.value.uppercase()) {
            "GET" -> getRequests.mark()
            "POST" -> postRequests.mark()
            "PUT" -> putRequests.mark()
            "DELETE" -> deleteRequests.mark()
            else -> otherRequests.mark()
        }

        // Measure response time
        val timer = responseTimer.time()

        try {
            proceed()

            // Record response status code metrics
            val statusCode = call.response.status()?.value ?: 0
            when (statusCode) {
                in 200..299 -> status2xxResponses.mark()
                in 300..399 -> status3xxResponses.mark()
                in 400..499 -> status4xxResponses.mark()
                in 500..599 -> status5xxResponses.mark()
            }
        } finally {
            // Stop the timer
            timer.stop()
        }
    }

    // Add a route to expose metrics
    application.routing {
        get("/metrics") {
            // Return metrics in a simple text format
            val reporter = StringReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build()

            val metrics = reporter.getMetricsText()
            call.respondText(metrics)
        }
    }

    logger.info("Metrics collection configured")
}
```

### Circuit Breaker Event Logging

The Circuit Breaker Event Logger logs circuit breaker events such as state transitions, failures, and successes.

```kotlin
fun logStateTransition(circuitBreakerName: String, fromState: SimpleCircuitBreaker.State, toState: SimpleCircuitBreaker.State) {
    // Increment the counter
    stateTransitionCounter.incrementAndGet()

    // Mark the meter
    stateTransitionMeter.mark()

    // Update the state gauges
    updateStateGauges(circuitBreakerName, fromState, toState)

    // Add MDC context for structured logging
    MDC.put("circuitBreaker", circuitBreakerName)
    MDC.put("eventType", "STATE_TRANSITION")
    MDC.put("fromState", fromState.toString())
    MDC.put("toState", toState.toString())

    // Log the event
    logger.info("Circuit breaker '$circuitBreakerName' state changed from $fromState to $toState")

    // Add the event to the recent events queue
    addEventToQueue(CircuitBreakerEventRecord(
        timestamp = Instant.now(),
        circuitBreakerName = circuitBreakerName,
        eventType = "STATE_TRANSITION",
        details = "From $fromState to $toState"
    ))

    // Clear MDC context
    MDC.remove("circuitBreaker")
    MDC.remove("eventType")
    MDC.remove("fromState")
    MDC.remove("toState")
}
```
