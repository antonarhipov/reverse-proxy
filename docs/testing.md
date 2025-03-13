# Reverse Proxy Testing Guide

This document describes the testing approach for the Reverse Proxy Server, including the testing strategy, test types, and how to run tests.

## Table of Contents

- [Testing Strategy](#testing-strategy)
- [Test Types](#test-types)
- [Running Tests](#running-tests)
- [Writing Tests](#writing-tests)
- [Test Coverage](#test-coverage)
- [Continuous Integration](#continuous-integration)

## Testing Strategy

The Reverse Proxy Server follows a comprehensive testing strategy that includes:

1. **Unit Testing**: Testing individual components in isolation
2. **Integration Testing**: Testing the interaction between components
3. **Performance Testing**: Testing the performance and scalability of the system

The testing strategy aims to ensure that:

- Each component works correctly in isolation
- Components work correctly together
- The system meets performance requirements
- The system handles edge cases and error conditions gracefully

## Test Types

### Unit Tests

Unit tests focus on testing individual components in isolation. They use mocks and stubs to isolate the component being tested from its dependencies.

Key unit tests include:

- **Load Balancer Tests**: Test the load balancing strategies
- **Circuit Breaker Tests**: Test the circuit breaker behavior
- **Security Tests**: Test the security features

Example unit test:

```kotlin
class RoundRobinLoadBalancerTest {
    @Test
    fun `test round robin selection`() {
        // Create test backend configurations
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health"),
            BackendConfig("backend3", "http://backend3.example.com", 1, "/health")
        )

        // Create the load balancer
        val loadBalancer = RoundRobinLoadBalancer(backends)

        // Test that backends are selected in round-robin order
        assertEquals("backend1", loadBalancer.selectBackend().id)
        assertEquals("backend2", loadBalancer.selectBackend().id)
        assertEquals("backend3", loadBalancer.selectBackend().id)
        assertEquals("backend1", loadBalancer.selectBackend().id) // Should wrap around
    }
}
```

### Integration Tests

Integration tests focus on testing the interaction between components. They use real components instead of mocks and stubs.

Key integration tests include:

- **Request Flow Tests**: Test the complete request flow from client to backend and back
- **WebSocket Tests**: Test WebSocket connections and frame forwarding
- **SSE Tests**: Test Server-Sent Events connections and event forwarding
- **Circuit Breaker Integration Tests**: Test the circuit breaker with simulated backend failures
- **Security Integration Tests**: Test the security features in a real environment

Example integration test:

```kotlin
class RequestFlowIntegrationTest {
    @Test
    fun `test complete request flow`() = testApplication {
        // Configure the test application
        environment {
            config = ApplicationConfig("path/to/test/config.yaml")
        }

        // Install the test module
        application {
            testModule()
        }

        // Create a client
        val client = createClient {
            expectSuccess = false
        }

        // Send a request to the reverse proxy
        val response = client.get("/test")

        // Verify the response
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Response from backend"))
    }
}
```

### Performance Tests

Performance tests focus on testing the performance and scalability of the system. They measure metrics such as throughput, latency, and resource usage.

Key performance tests include:

- **Throughput Tests**: Test the maximum number of requests per second
- **Latency Tests**: Test the response time under different loads
- **Stress Tests**: Test the system under high load
- **Circuit Breaker Performance Tests**: Test the circuit breaker behavior under stress

## Running Tests

### Running All Tests

To run all tests:

```bash
./gradlew test
```

### Running Specific Tests

To run a specific test class:

```bash
./gradlew test --tests "com.example.reverseproxy.loadbalancer.RoundRobinLoadBalancerTest"
```

To run a specific test method:

```bash
./gradlew test --tests "com.example.reverseproxy.loadbalancer.RoundRobinLoadBalancerTest.test round robin selection"
```

### Running Tests with Different Configurations

To run tests with a specific configuration:

```bash
./gradlew test -Dconfig.resource=test-application.yaml
```

### Running Performance Tests

Performance tests are not run by default. To run performance tests:

```bash
./gradlew performanceTest
```

## Writing Tests

### Unit Tests

When writing unit tests:

1. Use descriptive test names that explain what the test is checking
2. Set up the test environment in a `@Before` method
3. Clean up the test environment in an `@After` method
4. Use assertions to verify the expected behavior
5. Test both success and failure cases
6. Use mocks and stubs to isolate the component being tested

Example:

```kotlin
class SimpleCircuitBreakerTest {
    private lateinit var circuitBreaker: SimpleCircuitBreaker

    @Before
    fun setUp() {
        circuitBreaker = SimpleCircuitBreaker(
            name = "test",
            failureThreshold = 3,
            resetTimeoutMs = 100
        )
    }

    @Test
    fun `test initial state is closed`() {
        assertEquals(SimpleCircuitBreaker.State.CLOSED, circuitBreaker.getState())
    }

    @Test
    fun `test transition to open after failures`() {
        // Execute with failures
        for (i in 1..3) {
            val result = circuitBreaker.execute(
                fallback = { e -> "fallback" },
                block = { throw RuntimeException("Test failure") }
            )
            assertEquals("fallback", result)
        }

        // After 3 failures, the circuit should be open
        assertEquals(SimpleCircuitBreaker.State.OPEN, circuitBreaker.getState())
    }
}
```

### Integration Tests

When writing integration tests:

1. Use the Ktor test framework to set up a test environment
2. Configure the test environment to match the production environment as closely as possible
3. Use real components instead of mocks and stubs
4. Test the complete flow from client to backend and back
5. Test edge cases and error conditions

Example:

```kotlin
class WebSocketIntegrationTest {
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

                    // Verify the response
                    assertTrue(response is Frame.Text)
                    assertEquals("Echo: Hello from client", (response as Frame.Text).readText())
                }
            }
        } finally {
            client.close()
        }
    }
}
```

### Performance Tests

When writing performance tests:

1. Define clear performance goals and metrics
2. Set up a realistic test environment
3. Use tools like JMeter or Gatling to generate load
4. Measure and record performance metrics
5. Compare results against performance goals

## Test Coverage

The Reverse Proxy Server aims for high test coverage, with a target of at least 80% code coverage.

To generate a test coverage report:

```bash
./gradlew jacocoTestReport
```

The report will be available at `build/reports/jacoco/test/html/index.html`.

## Continuous Integration

The Reverse Proxy Server uses continuous integration to ensure that tests are run automatically on every commit.

The CI pipeline includes:

1. Building the project
2. Running unit tests
3. Running integration tests
4. Generating test coverage reports
5. Running static code analysis

If any of these steps fail, the CI pipeline will fail, and the commit will not be merged.