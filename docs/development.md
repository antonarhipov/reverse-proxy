# Reverse Proxy Development Guide

This document provides information for developers who want to understand, modify, or extend the Reverse Proxy Server.

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
- [Code Structure](#code-structure)
- [Extending the Application](#extending-the-application)
- [Testing](#testing)
- [Contribution Guidelines](#contribution-guidelines)

## Development Environment Setup

### Prerequisites

- JDK 17 or higher
- Gradle 7.6 or higher
- IntelliJ IDEA (recommended) or another IDE with Kotlin support

### Setting Up the Development Environment

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/reverse-proxy.git
   cd reverse-proxy
   ```

2. Open the project in your IDE:
   - For IntelliJ IDEA: File > Open > Select the project directory

3. Build the project:
   ```bash
   ./gradlew build
   ```

4. Run the application:
   ```bash
   ./gradlew run
   ```

## Code Structure

The Reverse Proxy Server is organized into several packages:

```
src/main/kotlin/
├── com.example
│   ├── Application.kt                  # Main application entry point
│   ├── Routing.kt                      # Routing configuration
│   └── reverseproxy
│       ├── circuitbreaker/             # Circuit breaker components
│       ├── config/                     # Configuration classes
│       ├── handler/                    # Request handlers
│       ├── loadbalancer/               # Load balancer components
│       ├── logging/                    # Logging components
│       ├── metrics/                    # Metrics collection
│       └── security/                   # Security features
```

### Key Components

#### Application.kt

The main entry point for the application. It configures the Ktor server and sets up the reverse proxy components.

```kotlin
fun Application.module() {
    // Configure reverse proxy
    configureReverseProxy()
}

fun Application.configureReverseProxy() {
    // Load configuration
    val config = environment.config
    val reverseProxyConfig = ReverseProxyConfig.fromConfig(config)

    // Configure components
    // ...
}
```

#### Config Package

Contains classes for loading and representing configuration:

- `ReverseProxyConfig`: Main configuration class
- `BackendConfig`: Configuration for backend servers
- `LoadBalancingConfig`: Configuration for load balancing
- `CircuitBreakerConfig`: Configuration for circuit breakers
- `SecurityConfig`: Configuration for security features
- `WebSocketConfig`: Configuration for WebSockets
- `SSEConfig`: Configuration for Server-Sent Events

#### LoadBalancer Package

Contains load balancer implementations:

- `LoadBalancer` (Interface): Defines the contract for load balancers
- `AbstractLoadBalancer`: Base class for load balancer implementations
- `RoundRobinLoadBalancer`: Implements round-robin load balancing strategy
- `RandomLoadBalancer`: Implements random load balancing strategy
- `LoadBalancerFactory`: Creates load balancer instances based on configuration

#### Handler Package

Contains request handlers:

- `RequestForwarderInterface` (Interface): Defines the contract for request forwarders
- `RequestForwarder`: Implements the request forwarding logic
- `ProxyHandler`: Handles incoming requests and uses the request forwarder
- `WebSocketHandler`: Handles WebSocket connections
- `SSEHandler`: Handles Server-Sent Events connections

#### CircuitBreaker Package

Contains circuit breaker components:

- `SimpleCircuitBreaker`: Implements the circuit breaker pattern
- `CircuitBreakerRequestForwarder`: Wraps a request forwarder with circuit breaker functionality
- `CircuitBreakerEventLogger`: Logs circuit breaker events

#### Security Package

Contains security features:

- `SecurityHandler`: Configures and applies security features

#### Logging Package

Contains logging components:

- `LoggingHandler`: Configures and applies logging features

#### Metrics Package

Contains metrics collection components:

- `MetricsHandler`: Configures and applies metrics collection

## Extending the Application

### Adding a New Load Balancing Strategy

To add a new load balancing strategy:

1. Create a new class that extends `AbstractLoadBalancer` and implements the `LoadBalancer` interface:

```kotlin
class MyCustomLoadBalancer(backends: List<BackendConfig>) : AbstractLoadBalancer(backends) {
    override fun selectBackend(): BackendConfig {
        // Implement your custom load balancing logic here
    }
}
```

2. Update the `LoadBalancerFactory` to support your new strategy:

```kotlin
fun createLoadBalancer(backends: List<BackendConfig>, config: LoadBalancingConfig): LoadBalancer {
    return when (config.strategy.lowercase()) {
        "round-robin" -> RoundRobinLoadBalancer(backends)
        "random" -> RandomLoadBalancer(backends)
        "my-custom-strategy" -> MyCustomLoadBalancer(backends)
        else -> throw IllegalArgumentException("Unsupported load balancing strategy: ${config.strategy}")
    }
}
```

### Adding a New Security Feature

To add a new security feature:

1. Update the `SecurityConfig` class to include your new feature:

```kotlin
data class SecurityConfig(
    val ssl: SslConfig,
    val ipFiltering: IpFilteringConfig,
    val rateLimit: RateLimitConfig,
    val myNewFeature: MyNewFeatureConfig
)

data class MyNewFeatureConfig(
    val enabled: Boolean,
    // Add your configuration options here
)
```

2. Update the `SecurityHandler` class to implement your new feature:

```kotlin
fun configureSecurity(application: Application) {
    // Existing security features
    configureSsl(application)
    configureIpFiltering(application)
    configureRateLimit(application)
    
    // Your new security feature
    configureMyNewFeature(application)
}

private fun configureMyNewFeature(application: Application) {
    if (!config.myNewFeature.enabled) {
        logger.info("My new feature is disabled")
        return
    }
    
    // Implement your new security feature here
    
    logger.info("My new feature is enabled")
}
```

### Adding Custom Metrics

To add custom metrics:

1. Update the `MetricsHandler` class to include your new metrics:

```kotlin
class MetricsHandler {
    // Existing metrics
    private val getRequests = metricRegistry.meter("http.requests.get")
    
    // Your new metrics
    private val myNewMetric = metricRegistry.meter("my.new.metric")
    
    fun configureMetrics(application: Application) {
        // Existing metrics configuration
        
        // Your new metrics configuration
        application.intercept(ApplicationCallPipeline.Monitoring) {
            // Record your new metrics
            myNewMetric.mark()
        }
    }
}
```

## Testing

The Reverse Proxy Server includes unit tests and integration tests.

### Running Tests

To run all tests:

```bash
./gradlew test
```

To run a specific test:

```bash
./gradlew test --tests "com.example.reverseproxy.loadbalancer.RoundRobinLoadBalancerTest"
```

### Test Structure

The tests are organized into several packages:

```
src/test/kotlin/
├── com.example.reverseproxy
│   ├── circuitbreaker/             # Circuit breaker tests
│   ├── handler/                    # Request handler tests
│   ├── integration/                # Integration tests
│   ├── loadbalancer/               # Load balancer tests
│   └── security/                   # Security feature tests
```

### Writing Tests

When writing tests, follow these guidelines:

1. Use descriptive test names that explain what the test is checking
2. Set up the test environment in a `@Before` method
3. Clean up the test environment in an `@After` method
4. Use assertions to verify the expected behavior
5. Test both success and failure cases

Example:

```kotlin
class MyCustomLoadBalancerTest {
    @Test
    fun `test custom load balancing strategy`() {
        // Arrange
        val backends = listOf(
            BackendConfig("backend1", "http://backend1.example.com", 1, "/health"),
            BackendConfig("backend2", "http://backend2.example.com", 1, "/health")
        )
        val loadBalancer = MyCustomLoadBalancer(backends)
        
        // Act
        val selectedBackend = loadBalancer.selectBackend()
        
        // Assert
        assertTrue(backends.contains(selectedBackend))
    }
}
```

## Contribution Guidelines

### Code Style

Follow the Kotlin coding conventions:

- Use 4 spaces for indentation
- Use camelCase for variables and functions
- Use PascalCase for classes and interfaces
- Use UPPER_SNAKE_CASE for constants
- Add KDoc comments for public classes, interfaces, and functions

### Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for your changes
5. Ensure all tests pass
6. Update the documentation
7. Submit a pull request

### Commit Messages

Use the following format for commit messages:

```
<type>: <subject>

<body>
```

Where `<type>` is one of:

- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

Example:

```
feat: Add weighted load balancing strategy

Implement a weighted load balancing strategy that selects backend servers based on their weights.
```