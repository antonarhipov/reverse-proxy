# Reverse Proxy Deployment Guide

This document provides instructions for deploying the Reverse Proxy Server in various environments.

## Table of Contents

- [Docker Deployment](#docker-deployment)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Traditional Deployment](#traditional-deployment)
- [Scaling Considerations](#scaling-considerations)
- [Monitoring and Logging](#monitoring-and-logging)

## Docker Deployment

The Reverse Proxy Server can be deployed using Docker and Docker Compose. This is the recommended deployment method for most use cases.

### Prerequisites

- Docker installed on your machine
- Docker Compose installed on your machine

### Deployment Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/reverse-proxy.git
   cd reverse-proxy
   ```

2. Build and run with Docker Compose:
   ```bash
   docker-compose up --build
   ```

3. The server will be available at http://localhost:8080.

### Docker Compose Configuration

The Docker Compose configuration is defined in the `docker-compose.yml` file:

```yaml
version: '3.8'

services:
  # Reverse proxy service
  reverse-proxy:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - BACKEND1_URL=http://backend1:8080
      - BACKEND2_URL=http://backend2:8080
      - BACKEND3_URL=http://backend3:8080
    volumes:
      - ./logs:/app/logs
    depends_on:
      - backend1
      - backend2
      - backend3
      - logstash
    networks:
      - reverse-proxy-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # Backend services
  backend1:
    image: jmalloc/echo-server
    ports:
      - "8081:8080"
    environment:
      - PORT=8080
    networks:
      - reverse-proxy-network

  backend2:
    image: jmalloc/echo-server
    ports:
      - "8082:8080"
    environment:
      - PORT=8080
    networks:
      - reverse-proxy-network

  backend3:
    image: jmalloc/echo-server
    ports:
      - "8083:8080"
    environment:
      - PORT=8080
    networks:
      - reverse-proxy-network

  # Monitoring services
  # ... (monitoring services configuration)

networks:
  reverse-proxy-network:
    driver: bridge

volumes:
  elasticsearch-data:
  prometheus-data:
  grafana-data:
```

### Environment Variables

The Docker deployment supports the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `BACKEND1_URL` | URL of the first backend server | `http://localhost:8081` |
| `BACKEND2_URL` | URL of the second backend server | `http://localhost:8082` |
| `BACKEND3_URL` | URL of the third backend server | `http://localhost:8083` |

### Volumes

The Docker deployment uses the following volumes:

| Volume | Description |
|--------|-------------|
| `./logs:/app/logs` | Maps the logs directory to persist logs between container restarts |
| `elasticsearch-data` | Stores Elasticsearch data |
| `prometheus-data` | Stores Prometheus data |
| `grafana-data` | Stores Grafana data |

### Customization

To customize the Docker deployment:

1. Modify the `docker-compose.yml` file to add or remove services
2. Modify the `src/main/resources/docker-application.yaml` file to change the configuration
3. Modify the `Dockerfile` to change the build process

For more information about the Docker deployment, see [README.docker.md](../README.docker.md).

## Kubernetes Deployment

The Reverse Proxy Server can be deployed on Kubernetes for production environments.

### Prerequisites

- Kubernetes cluster
- kubectl installed and configured
- Helm (optional, for deploying with Helm charts)

### Deployment Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/reverse-proxy.git
   cd reverse-proxy
   ```

2. Create a Kubernetes namespace:
   ```bash
   kubectl create namespace reverse-proxy
   ```

3. Apply the Kubernetes manifests:
   ```bash
   kubectl apply -f kubernetes/
   ```

4. Wait for the deployment to complete:
   ```bash
   kubectl -n reverse-proxy get pods
   ```

5. Access the service:
   ```bash
   kubectl -n reverse-proxy get svc reverse-proxy
   ```

### Kubernetes Manifests

The Kubernetes deployment uses the following manifests:

- `kubernetes/deployment.yaml`: Defines the Reverse Proxy deployment
- `kubernetes/service.yaml`: Defines the Reverse Proxy service
- `kubernetes/configmap.yaml`: Defines the configuration
- `kubernetes/ingress.yaml`: Defines the ingress rules (if needed)

Example deployment manifest:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: reverse-proxy
  namespace: reverse-proxy
spec:
  replicas: 3
  selector:
    matchLabels:
      app: reverse-proxy
  template:
    metadata:
      labels:
        app: reverse-proxy
    spec:
      containers:
      - name: reverse-proxy
        image: yourusername/reverse-proxy:latest
        ports:
        - containerPort: 8080
        env:
        - name: BACKEND1_URL
          value: "http://backend1-service:8080"
        - name: BACKEND2_URL
          value: "http://backend2-service:8080"
        - name: BACKEND3_URL
          value: "http://backend3-service:8080"
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
        - name: logs-volume
          mountPath: /app/logs
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
      volumes:
      - name: config-volume
        configMap:
          name: reverse-proxy-config
      - name: logs-volume
        emptyDir: {}
```

### Scaling

To scale the deployment:

```bash
kubectl -n reverse-proxy scale deployment reverse-proxy --replicas=5
```

### Helm Chart (Optional)

If you prefer to use Helm, you can create a Helm chart for the Reverse Proxy Server:

```bash
helm create reverse-proxy
```

Then customize the chart and deploy it:

```bash
helm install reverse-proxy ./reverse-proxy -n reverse-proxy
```

## Traditional Deployment

The Reverse Proxy Server can also be deployed on traditional servers without containers.

### Prerequisites

- JDK 17 or higher
- Gradle 7.6 or higher

### Deployment Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/reverse-proxy.git
   cd reverse-proxy
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```

3. Create a fat JAR:
   ```bash
   ./gradlew shadowJar
   ```

4. Run the server:
   ```bash
   java -jar build/libs/reverse-proxy-all.jar
   ```

5. The server will be available at http://localhost:8080.

### Systemd Service (Linux)

To run the Reverse Proxy Server as a systemd service on Linux:

1. Create a systemd service file:
   ```bash
   sudo nano /etc/systemd/system/reverse-proxy.service
   ```

2. Add the following content:
   ```
   [Unit]
   Description=Reverse Proxy Server
   After=network.target

   [Service]
   User=youruser
   WorkingDirectory=/path/to/reverse-proxy
   ExecStart=/usr/bin/java -jar /path/to/reverse-proxy/build/libs/reverse-proxy-all.jar
   Restart=always

   [Install]
   WantedBy=multi-user.target
   ```

3. Reload systemd:
   ```bash
   sudo systemctl daemon-reload
   ```

4. Enable and start the service:
   ```bash
   sudo systemctl enable reverse-proxy
   sudo systemctl start reverse-proxy
   ```

5. Check the status:
   ```bash
   sudo systemctl status reverse-proxy
   ```

## Scaling Considerations

### Horizontal Scaling

The Reverse Proxy Server can be horizontally scaled by running multiple instances behind a load balancer.

For Docker Compose, you can scale the service:

```bash
docker-compose up --scale reverse-proxy=3
```

For Kubernetes, you can scale the deployment:

```bash
kubectl -n reverse-proxy scale deployment reverse-proxy --replicas=5
```

### Vertical Scaling

The Reverse Proxy Server can be vertically scaled by allocating more resources to the container or VM.

For Docker Compose, you can set resource limits:

```yaml
services:
  reverse-proxy:
    # ...
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

For Kubernetes, you can set resource requests and limits:

```yaml
containers:
- name: reverse-proxy
  # ...
  resources:
    requests:
      memory: "1Gi"
      cpu: "500m"
    limits:
      memory: "2Gi"
      cpu: "2"
```

### Session Affinity

If you're using sticky sessions, ensure that your load balancer supports session affinity.

For Kubernetes, you can set session affinity on the service:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: reverse-proxy
spec:
  selector:
    app: reverse-proxy
  ports:
  - port: 80
    targetPort: 8080
  sessionAffinity: ClientIP
```

## Monitoring and Logging

### Monitoring

The Reverse Proxy Server exposes metrics at the `/metrics` endpoint. You can use Prometheus to scrape these metrics and Grafana to visualize them.

For Docker Compose, monitoring is already set up with Prometheus and Grafana. You can access the dashboards at:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (default username: admin, default password: admin)

For Kubernetes, you can use the Prometheus Operator to set up monitoring:

```bash
kubectl apply -f https://raw.githubusercontent.com/prometheus-operator/prometheus-operator/main/bundle.yaml
```

Then create a ServiceMonitor for the Reverse Proxy:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: reverse-proxy
  namespace: monitoring
spec:
  selector:
    matchLabels:
      app: reverse-proxy
  endpoints:
  - port: http
    path: /metrics
    interval: 15s
```

### Logging

The Reverse Proxy Server logs to the `logs` directory. You can use the ELK Stack (Elasticsearch, Logstash, Kibana) to aggregate and visualize logs.

For Docker Compose, logging is already set up with the ELK Stack. You can access Kibana at http://localhost:5601.

For Kubernetes, you can use the Elastic Cloud on Kubernetes (ECK) operator to set up logging:

```bash
kubectl apply -f https://download.elastic.co/downloads/eck/2.8.0/crds.yaml
kubectl apply -f https://download.elastic.co/downloads/eck/2.8.0/operator.yaml
```

Then create Elasticsearch and Kibana resources.

For more information about monitoring and logging, see [Monitoring](monitoring.md).