# Reverse Proxy Configuration

This document describes the configuration options for the Reverse Proxy Server.

## Configuration File

The Reverse Proxy Server is configured using a YAML file located at `src/main/resources/application.yaml`. For Docker deployments, a separate configuration file is used: `src/main/resources/docker-application.yaml`.

## Configuration Structure

The configuration is organized into several sections:

```yaml
ktor:
  # Ktor-specific configuration
  
reverseProxy:
  # Reverse proxy configuration
  backends:
    # Backend server configuration
  loadBalancing:
    # Load balancing configuration
  circuitBreaker:
    # Circuit breaker configuration
  security:
    # Security settings
  webSocket:
    # WebSocket configuration
  sse:
    # Server-Sent Events configuration
```

## Ktor Configuration

The `ktor` section contains Ktor-specific configuration:

```yaml
ktor:
  application:
    modules:
      - com.example.ApplicationKt.module
  deployment:
    port: 8080
```

| Option | Description | Default |
|--------|-------------|---------|
| `application.modules` | The Kotlin modules to load | `com.example.ApplicationKt.module` |
| `deployment.port` | The port to listen on | `8080` |

## Backend Configuration

The `reverseProxy.backends` section contains a list of backend servers:

```yaml
reverseProxy:
  backends:
    - id: "backend1"
      url: "http://localhost:8081"
      weight: 3
      healthCheckPath: "/health"
    - id: "backend2"
      url: "http://localhost:8082"
      weight: 2
      healthCheckPath: "/health"
```

Each backend server has the following options:

| Option | Description | Default |
|--------|-------------|---------|
| `id` | Unique identifier for the backend server | Required |
| `url` | URL of the backend server | Required |
| `weight` | Weight for weighted load balancing | `1` |
| `healthCheckPath` | Path for health checks | `/health` |

## Load Balancing Configuration

The `reverseProxy.loadBalancing` section contains load balancing configuration:

```yaml
reverseProxy:
  loadBalancing:
    strategy: "round-robin"  # Options: round-robin, random, least-connections, weighted, consistent-hash
    sessionSticky: false
    sessionCookieName: "BACKEND_ID"
```

| Option | Description | Default |
|--------|-------------|---------|
| `strategy` | Load balancing strategy | `round-robin` |
| `sessionSticky` | Whether to use sticky sessions | `false` |
| `sessionCookieName` | Cookie name for sticky sessions | `BACKEND_ID` |

### Load Balancing Strategies

The following load balancing strategies are available:

- `round-robin`: Distributes requests sequentially among backend servers
- `random`: Randomly selects a backend server
- `least-connections`: Selects the backend server with the fewest active connections (not implemented yet)
- `weighted`: Selects backend servers based on their weights (not implemented yet)
- `consistent-hash`: Uses consistent hashing to select backend servers (not implemented yet)

## Circuit Breaker Configuration

The `reverseProxy.circuitBreaker` section contains circuit breaker configuration:

```yaml
reverseProxy:
  circuitBreaker:
    failureRateThreshold: 50  # Percentage
    waitDurationInOpenState: 60000  # Milliseconds
    ringBufferSizeInClosedState: 10
    ringBufferSizeInHalfOpenState: 5
    automaticTransitionFromOpenToHalfOpenEnabled: true
```

| Option | Description | Default |
|--------|-------------|---------|
| `failureRateThreshold` | Percentage of failures required to open the circuit | `50` |
| `waitDurationInOpenState` | Time in milliseconds to wait before transitioning from open to half-open | `60000` |
| `ringBufferSizeInClosedState` | Number of calls to record when the circuit is closed | `10` |
| `ringBufferSizeInHalfOpenState` | Number of calls to record when the circuit is half-open | `5` |
| `automaticTransitionFromOpenToHalfOpenEnabled` | Whether to automatically transition from open to half-open | `true` |

## Security Configuration

The `reverseProxy.security` section contains security configuration:

```yaml
reverseProxy:
  security:
    ssl:
      enabled: false
      keyStorePath: "keystore.jks"
      keyStorePassword: "password"
    ipFiltering:
      enabled: false
      whitelistOnly: true
      whitelist: ["127.0.0.1", "::1"]
      blacklist: []
    rateLimit:
      enabled: true
      limit: 100  # Requests per window
      window: 60  # Window in seconds
```

### SSL Configuration

| Option | Description | Default |
|--------|-------------|---------|
| `ssl.enabled` | Whether to enable SSL/TLS | `false` |
| `ssl.keyStorePath` | Path to the keystore file | `keystore.jks` |
| `ssl.keyStorePassword` | Password for the keystore | `password` |

### IP Filtering Configuration

| Option | Description | Default |
|--------|-------------|---------|
| `ipFiltering.enabled` | Whether to enable IP filtering | `false` |
| `ipFiltering.whitelistOnly` | Whether to use whitelist-only mode | `true` |
| `ipFiltering.whitelist` | List of whitelisted IP addresses | `["127.0.0.1", "::1"]` |
| `ipFiltering.blacklist` | List of blacklisted IP addresses | `[]` |

### Rate Limiting Configuration

| Option | Description | Default |
|--------|-------------|---------|
| `rateLimit.enabled` | Whether to enable rate limiting | `true` |
| `rateLimit.limit` | Number of requests allowed per window | `100` |
| `rateLimit.window` | Window size in seconds | `60` |

## WebSocket Configuration

The `reverseProxy.webSocket` section contains WebSocket configuration:

```yaml
reverseProxy:
  webSocket:
    enabled: true
    pingInterval: 30000  # Milliseconds
    timeout: 15000  # Milliseconds
```

| Option | Description | Default |
|--------|-------------|---------|
| `enabled` | Whether to enable WebSocket support | `true` |
| `pingInterval` | Interval in milliseconds for sending ping frames | `30000` |
| `timeout` | Timeout in milliseconds for WebSocket connections | `15000` |

## Server-Sent Events Configuration

The `reverseProxy.sse` section contains Server-Sent Events configuration:

```yaml
reverseProxy:
  sse:
    enabled: true
    reconnectTime: 3000  # Milliseconds - suggested reconnect time for clients
    eventBufferSize: 100  # Number of events to buffer
    heartbeatInterval: 15000  # Milliseconds - interval for sending heartbeat events
```

| Option | Description | Default |
|--------|-------------|---------|
| `enabled` | Whether to enable SSE support | `true` |
| `reconnectTime` | Suggested reconnect time in milliseconds for clients | `3000` |
| `eventBufferSize` | Number of events to buffer | `100` |
| `heartbeatInterval` | Interval in milliseconds for sending heartbeat events | `15000` |

## Environment Variables

The Docker configuration file (`docker-application.yaml`) supports environment variables for backend URLs:

```yaml
reverseProxy:
  backends:
    - id: "backend1"
      url: ${BACKEND1_URL:-http://localhost:8081}
      weight: 3
      healthCheckPath: "/health"
```

The following environment variables are supported:

| Variable | Description | Default |
|----------|-------------|---------|
| `BACKEND1_URL` | URL of the first backend server | `http://localhost:8081` |
| `BACKEND2_URL` | URL of the second backend server | `http://localhost:8082` |
| `BACKEND3_URL` | URL of the third backend server | `http://localhost:8083` |

## Configuration Loading

The configuration is loaded by the `ReverseProxyConfig` class, which parses the YAML file and creates a configuration object. The configuration object is then used by the various components of the Reverse Proxy Server.

See [Components](components.md) for details on how the configuration is used by the components.