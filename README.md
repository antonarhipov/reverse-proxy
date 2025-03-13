# Reverse Proxy Server

A high-performance, feature-rich reverse proxy server built with Kotlin and Ktor. This reverse proxy acts as an intermediary for client requests, forwarding them to backend servers while providing load balancing, circuit breaking, security features, and more.

## Features

| Feature                | Description                                                                                       |
|------------------------|---------------------------------------------------------------------------------------------------|
| **Load Balancing**     | Distribute requests among multiple backend servers using various strategies                       |
| **Circuit Breaking**   | Prevent cascading failures by detecting backend issues and providing fallback responses           |
| **WebSocket Support**  | Forward WebSocket connections to backend servers with bidirectional communication                 |
| **SSE Support**        | Forward Server-Sent Events (SSE) from backend servers to clients                                  |
| **Security**           | SSL/TLS termination, IP filtering, rate limiting, and request validation                          |
| **Logging**            | Comprehensive structured logging with SLF4J and Logback                                           |
| **Monitoring**         | Metrics collection with Prometheus and visualization with Grafana                                 |
| **Docker Support**     | Easy deployment with Docker and Docker Compose                                                    |

## Quick Start

### Prerequisites

- JDK 17 or higher
- Gradle 7.6 or higher

### Running the Server

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/reverse-proxy.git
   cd reverse-proxy
   ```

2. Run the server:
   ```bash
   ./gradlew run
   ```

3. The server will start on port 8080 by default. You can access it at http://localhost:8080.

### Using Docker

1. Build and run with Docker Compose:
   ```bash
   docker-compose up --build
   ```

2. The server will be available at http://localhost:8080.

## Documentation

For detailed documentation, please refer to the following:

- [User Guide](docs/user-guide.md) - Installation, configuration, and usage
- [Architecture](docs/architecture.md) - System architecture and design
- [Components](docs/components.md) - Detailed component documentation
- [Configuration](docs/configuration.md) - Configuration options and examples
- [Deployment](docs/deployment.md) - Deployment options and instructions
- [Monitoring](docs/monitoring.md) - Monitoring setup and dashboards
- [Development](docs/development.md) - Development guide and extending the application
- [Testing](docs/testing.md) - Testing approach and running tests

## Building & Running

| Task                          | Description                                                          |
|-------------------------------|----------------------------------------------------------------------|
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew build`             | Build everything                                                     |
| `buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `buildImage`                  | Build the docker image to use with the fat JAR                       |
| `publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `run`                         | Run the server                                                       |
| `runDocker`                   | Run using the local docker image                                     |

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [Ktor](https://ktor.io/) - The web framework used
- [Resilience4j](https://github.com/resilience4j/resilience4j) - For circuit breaking functionality
- [Prometheus](https://prometheus.io/) - For metrics collection
- [Grafana](https://grafana.com/) - For metrics visualization
