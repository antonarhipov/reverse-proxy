# Reverse Proxy User Guide

This document provides instructions for installing, configuring, and using the Reverse Proxy Server.

## Table of Contents

- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [Troubleshooting](#troubleshooting)

## Installation

### Prerequisites

- JDK 17 or higher
- Gradle 7.6 or higher

### Installation Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/reverse-proxy.git
   cd reverse-proxy
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```

### Docker Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/reverse-proxy.git
   cd reverse-proxy
   ```

2. Build and run with Docker Compose:
   ```bash
   docker-compose up --build
   ```

## Configuration

The Reverse Proxy Server is configured using a YAML file located at `src/main/resources/application.yaml`. For Docker deployments, a separate configuration file is used: `src/main/resources/docker-application.yaml`.

See [Configuration](configuration.md) for detailed information about the configuration options.

### Basic Configuration

Here's a basic configuration example:

```yaml
ktor:
  application:
    modules:
      - com.example.ApplicationKt.module
  deployment:
    port: 8080

reverseProxy:
  backends:
    - id: "backend1"
      url: "http://localhost:8081"
      weight: 1
      healthCheckPath: "/health"
    - id: "backend2"
      url: "http://localhost:8082"
      weight: 1
      healthCheckPath: "/health"
  
  loadBalancing:
    strategy: "round-robin"
    sessionSticky: false
    sessionCookieName: "BACKEND_ID"
  
  security:
    ssl:
      enabled: false
    ipFiltering:
      enabled: false
    rateLimit:
      enabled: true
      limit: 100
      window: 60
  
  webSocket:
    enabled: true
  
  sse:
    enabled: true
```

### Environment Variables

For Docker deployments, you can configure the backend URLs using environment variables:

```bash
BACKEND1_URL=http://backend1:8080 \
BACKEND2_URL=http://backend2:8080 \
docker-compose up
```

## Usage

### Starting the Server

To start the server, run:

```bash
./gradlew run
```

The server will start on port 8080 by default. You can access it at http://localhost:8080.

### Using Docker

To start the server with Docker, run:

```bash
docker-compose up
```

The server will be available at http://localhost:8080.

### Making Requests

Once the server is running, you can make requests to it as you would to any HTTP server. The requests will be forwarded to the backend servers according to the configured load balancing strategy.

#### HTTP Requests

```bash
# Make a GET request
curl http://localhost:8080/api/resource

# Make a POST request
curl -X POST -H "Content-Type: application/json" -d '{"key": "value"}' http://localhost:8080/api/resource
```

#### WebSocket Connections

To establish a WebSocket connection, use a WebSocket client and connect to:

```
ws://localhost:8080/path/to/websocket
```

The WebSocket connection will be forwarded to a backend server, and frames will be forwarded bidirectionally.

#### Server-Sent Events (SSE)

To establish an SSE connection, make a GET request with the `Accept: text/event-stream` header:

```bash
curl -H "Accept: text/event-stream" http://localhost:8080/path/to/sse
```

The SSE connection will be forwarded to a backend server, and events will be forwarded to the client.

### Monitoring

The Reverse Proxy Server exposes metrics at the `/metrics` endpoint:

```bash
curl http://localhost:8080/metrics
```

For Docker deployments, monitoring is set up with Prometheus and Grafana. You can access the dashboards at:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (default username: admin, default password: admin)

See [Monitoring](monitoring.md) for more information.

## Troubleshooting

### Common Issues

#### Server Won't Start

If the server won't start, check the following:

1. Make sure you have JDK 17 or higher installed:
   ```bash
   java -version
   ```

2. Check the logs for error messages:
   ```bash
   ./gradlew run --debug
   ```

3. Make sure the configuration file is valid:
   ```bash
   cat src/main/resources/application.yaml
   ```

#### Connection Refused

If you get a "Connection refused" error when making requests to the proxy, check the following:

1. Make sure the server is running:
   ```bash
   ps aux | grep java
   ```

2. Check if the server is listening on the expected port:
   ```bash
   netstat -tuln | grep 8080
   ```

3. Check the logs for error messages:
   ```bash
   tail -f logs/reverse-proxy.log
   ```

#### Backend Server Errors

If you get errors when the proxy tries to connect to backend servers, check the following:

1. Make sure the backend servers are running and accessible:
   ```bash
   curl http://localhost:8081/health
   curl http://localhost:8082/health
   ```

2. Check the backend URLs in the configuration:
   ```bash
   cat src/main/resources/application.yaml
   ```

3. Check the logs for error messages:
   ```bash
   tail -f logs/reverse-proxy.log
   ```

#### Circuit Breaker Open

If the circuit breaker is open and requests are being rejected, check the following:

1. Check the logs for circuit breaker events:
   ```bash
   grep "Circuit breaker" logs/reverse-proxy.log
   ```

2. Check if the backend servers are healthy:
   ```bash
   curl http://localhost:8081/health
   curl http://localhost:8082/health
   ```

3. Wait for the circuit breaker to transition to half-open state (default: 60 seconds) and try again.

### Getting Help

If you're still having issues, you can:

1. Check the [GitHub Issues](https://github.com/yourusername/reverse-proxy/issues) for similar problems and solutions.
2. Create a new issue with details about your problem.
3. Contact the maintainers for support.