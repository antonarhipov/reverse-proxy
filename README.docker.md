# Docker Setup for Reverse Proxy

This document describes how to use Docker to run the reverse proxy application and its backend services.

## Prerequisites

- Docker installed on your machine
- Docker Compose installed on your machine

## Files

- `Dockerfile`: Defines how to build the reverse proxy application image
- `docker-compose.yml`: Defines the services (reverse proxy and backends) and their relationships
- `src/main/resources/docker-application.yaml`: Configuration file for the reverse proxy when running in Docker

## Building and Running

### Build and Run with Docker Compose

To build and run the entire setup (reverse proxy and backend services):

```bash
docker-compose up --build
```

This will:
1. Build the reverse proxy image using the Dockerfile
2. Start three backend services using the jmalloc/echo-server image
3. Start the reverse proxy service
4. Set up networking between the services

### Access the Services

- Reverse Proxy: http://localhost:8080
- Backend 1: http://localhost:8081
- Backend 2: http://localhost:8082
- Backend 3: http://localhost:8083

## Configuration

The reverse proxy is configured to use environment variables for backend URLs:

- `BACKEND1_URL`: URL for the first backend service (default: http://backend1:8080)
- `BACKEND2_URL`: URL for the second backend service (default: http://backend2:8080)
- `BACKEND3_URL`: URL for the third backend service (default: http://backend3:8080)

These environment variables are set in the `docker-compose.yml` file.

## Volumes

The logs directory is mounted as a volume to persist logs between container restarts:

```yaml
volumes:
  - ./logs:/app/logs
```

## Customization

### Adding More Backend Services

To add more backend services:

1. Add a new service definition in `docker-compose.yml`
2. Add a new backend configuration in `src/main/resources/docker-application.yaml`
3. Add a new environment variable in the reverse-proxy service in `docker-compose.yml`

### Changing Backend Images

The default backend image is `jmalloc/echo-server`, which is a simple HTTP server that echoes back the request. You can replace this with any other HTTP server image by changing the `image` field in the backend service definitions.

## Troubleshooting

### Logs

Logs are available in the `logs` directory, which is mounted as a volume.

### Container Logs

To view the logs of a specific container:

```bash
docker-compose logs reverse-proxy
docker-compose logs backend1
```

### Accessing Containers

To access a container's shell:

```bash
docker-compose exec reverse-proxy sh
docker-compose exec backend1 sh
```