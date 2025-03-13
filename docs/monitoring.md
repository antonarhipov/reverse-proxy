# Monitoring Setup for Reverse Proxy

This document describes the monitoring setup for the reverse proxy application, including how to access and use the dashboards, and troubleshooting tips.

## Overview

The monitoring setup consists of the following components:

1. **Logging Aggregation**: ELK Stack (Elasticsearch, Logstash, Kibana)
2. **Metrics Collection**: Prometheus and Grafana
3. **System Metrics**: Node Exporter

## Accessing the Dashboards

After starting the application with Docker Compose, you can access the dashboards at the following URLs:

- **Kibana**: http://localhost:5601
  - Used for log analysis and visualization
  - Default username: `elastic`
  - Default password: `changeme`

- **Grafana**: http://localhost:3000
  - Used for metrics visualization
  - Default username: `admin`
  - Default password: `admin`

- **Prometheus**: http://localhost:9090
  - Used for metrics collection and querying
  - No authentication required

## Available Dashboards

### Reverse Proxy Dashboard

The main dashboard for the reverse proxy application is available in Grafana. It includes the following panels:

1. **HTTP Request Rate**: Shows the rate of HTTP requests by method
2. **Response Time**: Shows the average response time
3. **Error Rate**: Shows the percentage of 5xx responses
4. **Logs**: Shows logs from Elasticsearch

## Metrics

The following metrics are collected by Prometheus:

1. **HTTP Requests**: Count of HTTP requests by method
2. **Response Time**: Histogram of response times
3. **Response Status**: Count of responses by status code
4. **Circuit Breaker State**: State of the circuit breaker (open, closed, half-open)
5. **System Metrics**: CPU, memory, disk, and network usage

## Logs

The following logs are collected by Elasticsearch:

1. **Application Logs**: Logs from the reverse proxy application
2. **Access Logs**: HTTP access logs
3. **Error Logs**: Error logs
4. **Circuit Breaker Logs**: Logs related to circuit breaker state changes

## Troubleshooting

### Common Issues

1. **Dashboards not loading**: Check if the services are running with `docker-compose ps`
2. **No data in Grafana**: Check if Prometheus is collecting metrics and if the datasource is configured correctly
3. **No logs in Kibana**: Check if Logstash is receiving logs and if Elasticsearch is storing them

### Checking Service Status

```bash
# Check if all services are running
docker-compose ps

# Check logs for a specific service
docker-compose logs elasticsearch
docker-compose logs logstash
docker-compose logs kibana
docker-compose logs prometheus
docker-compose logs grafana
docker-compose logs node-exporter
```

### Restarting Services

```bash
# Restart a specific service
docker-compose restart elasticsearch
docker-compose restart logstash
docker-compose restart kibana
docker-compose restart prometheus
docker-compose restart grafana
docker-compose restart node-exporter

# Restart all services
docker-compose restart
```

## Extending the Monitoring Setup

### Adding Custom Metrics

To add custom metrics, modify the `MetricsHandler.kt` file to include additional metrics.

### Adding Custom Dashboards

To add custom dashboards, create new JSON files in the `config/grafana/provisioning/dashboards` directory.

### Adding Alerts

To add alerts, use Grafana's alerting feature to create alert rules based on the collected metrics.