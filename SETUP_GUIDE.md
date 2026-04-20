# MyURL API - Setup and Deployment Guide

## Table of Contents
1. [Local Development Setup](#local-development-setup)
2. [Docker Deployment (Home Server)](#docker-deployment-home-server)
3. [Kubernetes Deployment](#kubernetes-deployment)
4. [CI/CD Pipeline](#cicd-pipeline)
5. [Monitoring & Observability](#monitoring--observability)
6. [Troubleshooting](#troubleshooting)

---

## Local Development Setup

### Prerequisites

- **Java 17** or higher
- **Maven 3.8+**
- **Docker & Docker Compose** (for infrastructure services)

### Step 1: Install Prerequisites

#### macOS
```bash
# Install Java 17
brew install openjdk@17

# Install Maven
brew install maven

# Install Docker Desktop
brew install --cask docker
```

#### Linux (Ubuntu/Debian)
```bash
# Install Java 17
sudo apt update
sudo apt install openjdk-17-jdk maven -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
```

#### Windows
```bash
# Download and install from:
# Java: https://adoptium.net/temurin/releases/?version=17
# Maven: https://maven.apache.org/download.cgi
# Docker Desktop: https://www.docker.com/products/docker-desktop/
```

### Step 2: Clone Repository

```bash
git clone https://github.com/gallantsuri1/miniurl.git
cd miniurl
```

### Step 3: Start Infrastructure (Docker)

```bash
# Start all infrastructure services (Eureka, Kafka, Redis, MySQL)
docker-compose up -d

# Wait for services to be ready (60 seconds)
sleep 60

# Verify services
docker-compose ps
```

### Step 4: Build Application

```bash
# Clean and package (skip tests for faster build)
mvn clean install -DskipTests

# Or build with tests
mvn clean install
```

### Step 5: Run Services

**Order of startup (critical for service discovery):**

1. **Eureka Server** (Service Discovery)
2. **Identity Service** (Auth)
3. **URL Service** (URL Management)
4. **Feature Service** (Feature Flags)
5. **API Gateway** (Routing)
6. **Redirect Service** (Hot Path)
7. **Notification Service** (Email)
8. **Analytics Service** (Click Tracking)

```bash
# Terminal 1: Eureka Server
mvn spring-boot:run -pl eureka-server

# Terminal 2: Identity Service
mvn spring-boot:run -pl identity-service

# Terminal 3: URL Service
mvn spring-boot:run -pl url-service

# Terminal 4: Feature Service
mvn spring-boot:run -pl feature-service

# Terminal 5: API Gateway
mvn spring-boot:run -pl api-gateway

# Terminal 6: Redirect Service
mvn spring-boot:run -pl redirect-service

# Terminal 7: Notification Service
mvn spring-boot:run -pl notification-service

# Terminal 8: Analytics Service
mvn spring-boot:run -pl analytics-service
```

### Step 6: Access Application

- **API Gateway**: http://localhost:8080
- **Swagger API Docs**: http://localhost:8080/swagger-ui.html
- **Eureka Dashboard**: http://localhost:8761
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin123)

---

## Docker Deployment (Home Server)

### Prerequisites

- **Docker** and **Docker Compose** installed
- **Persistent storage** for MySQL data

### Step 1: Prepare Server

```bash
# SSH to your home server
ssh user@your-home-server

# Create application directory
mkdir -p ~/miniurl
cd ~/miniurl
```

### Step 2: Clone Repository

```bash
git clone https://github.com/gallantsuri1/miniurl.git .
```

### Step 3: Configure Environment

```bash
# Copy environment file
cp .env.example .env

# Generate secure JWT secret
echo "APP_JWT_SECRET=$(openssl rand -base64 64)" >> .env

# Edit configuration
nano .env
```

**Required `.env` settings:**

```bash
# ===========================================
# DATABASE CONFIGURATION
# ===========================================
MYSQL_ROOT_PASSWORD=YourSecureMySQLRootPassword123!

# ===========================================
# JWT CONFIGURATION (REQUIRED)
# ===========================================
APP_JWT_SECRET=YourSecureSecretKeyGeneratedWithOpensslRandBase6464

# ===========================================
# APPLICATION CONFIGURATION
# ===========================================
APP_BASE_URL=http://your-home-server-ip:8080
```

### Step 4: Start Services

```bash
# Start all services
docker compose up -d

# View startup logs
docker compose logs -f

# Check service status
docker compose ps
```

### Step 5: Verify Deployment

```bash
# Check health endpoint
curl http://localhost:8080/api/health

# Check application logs
docker compose logs api-gateway

# Check Eureka logs
docker compose logs eureka-server
```

---

## Kubernetes Deployment

### Prerequisites

- **kubectl** (v1.28.0+)
- **Helm** (optional)
- **Kubernetes Cluster** (EKS, GKE, AKS, or Minikube)
- **Nginx Ingress Controller**

### Step 1: Install Nginx Ingress Controller

```bash
# Minikube
minikube addons enable ingress

# Generic Kubernetes
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/cloud/deploy.yaml
```

### Step 2: Create Namespace

```bash
kubectl create namespace miniurl
```

### Step 3: Apply Infrastructure

```bash
# Apply global configuration
kubectl apply -f k8s/infrastructure/global-config.yaml

# Apply monitoring stack (Prometheus, Grafana)
kubectl apply -f k8s/infrastructure/monitoring.yaml

# Apply ELK stack (optional)
kubectl apply -f k8s/infrastructure/elk.yaml
```

### Step 4: Apply Services

```bash
# Apply all services
kubectl apply -f k8s/services/

# Wait for deployments
kubectl -n miniurl rollout status deployment/api-gateway --timeout=120s
kubectl -n miniurl rollout status deployment/eureka-server --timeout=120s
kubectl -n miniurl rollout status deployment/identity-service --timeout=120s
kubectl -n miniurl rollout status deployment/url-service --timeout=120s
kubectl -n miniurl rollout status deployment/redirect-service --timeout=120s
kubectl -n miniurl rollout status deployment/feature-service --timeout=120s
kubectl -n miniurl rollout status deployment/notification-service --timeout=120s
kubectl -n miniurl rollout status deployment/analytics-service --timeout=120s
```

### Step 5: Apply HPA

```bash
kubectl apply -f k8s/hpa/
```

### Step 6: Apply Ingress

```bash
kubectl apply -f k8s/ingress/
```

### Step 7: Initialize Databases

```bash
# Initialize URL database
kubectl -n miniurl exec -it mysql-url-0 -- mysql -u root -proot < scripts/init-url-db.sql

# Initialize Identity database
kubectl -n miniurl exec -it mysql-identity-0 -- mysql -u root -proot < scripts/init-identity-db.sql

# Initialize Feature database
kubectl -n miniurl exec -it mysql-feature-0 -- mysql -u root -proot < scripts/init-feature-db.sql
```

### Step 8: Verify Deployment

```bash
# Check all pods
kubectl -n miniurl get pods

# Check all services
kubectl -n miniurl get svc

# Check Eureka dashboard
kubectl -n miniurl port-forward svc/eureka-server 8761:8761
open http://localhost:8761

# Check API Gateway
curl http://api.miniurl.com/api/health
```

---

## CI/CD Pipeline

### GitHub Actions Workflows

The project includes two main workflows:

1. **PR Validation** (`.github/workflows/pr-validation.yml`)
   - Runs on pull requests
   - Builds and tests code
   - Uploads coverage reports

2. **Main Pipeline** (`.github/workflows/main-pipeline.yml`)
   - Runs on merge to main/master
   - Builds and pushes Docker images
   - Deploys to Kubernetes

### Required GitHub Secrets

| Secret | Description |
|--------|-------------|
| `DOCKER_USER` | Docker Hub username |
| `DOCKER_API_TOKEN` | Docker Hub API token |
| `KUBE_CONFIG` | Base64 encoded kubeconfig |
| `KUBE_CONTEXT` | Kubernetes context name |

### Manual Deployment

```bash
# Build and push Docker images
docker build -f Dockerfile.multi --target build-api-gateway -t miniurl/api-gateway:latest .
docker push miniurl/api-gateway:latest

# Update Kubernetes deployment
kubectl -n miniurl set image deployment/api-gateway api-gateway=miniurl/api-gateway:latest
```

---

## Monitoring & Observability

### Prometheus

- **Endpoint**: `http://prometheus.miniurl.svc.cluster.local:9090`
- **Metrics**: `/actuator/prometheus` from all services

### Grafana

- **Endpoint**: `http://grafana.miniurl.svc.cluster.local:3000`
- **Credentials**: `admin/admin123`
- **Dashboards**: Spring Boot metrics, Kubernetes metrics

### ELK Stack

- **Elasticsearch**: `http://elasticsearch.miniurl.svc.cluster.local:9200`
- **Kibana**: `http://kibana.miniurl.svc.cluster.local:5601`
- **Log Collection**: Kafka topics `notifications`, `clicks`

---

## Troubleshooting

### Common Issues

#### Pod CrashLoopBackOff

```bash
# Check logs
kubectl -n miniurl logs <pod-name>

# Check events
kubectl -n miniurl describe pod <pod-name>

# Check resource limits
kubectl -n miniurl top pods
```

#### Service Discovery Issues

```bash
# Check Eureka
kubectl -n miniurl get pods -l app=eureka-server

# Check service endpoints
kubectl -n miniurl get endpoints <service-name>

# Check DNS
kubectl -n miniurl exec -it <pod-name> -- nslookup <service-name>
```

#### Database Connection Failed

```bash
# Check MySQL pods
kubectl -n miniurl get pods -l app=mysql

# Check PVC
kubectl -n miniurl get pvc

# Check secrets
kubectl -n miniurl get secret db-secrets -o yaml
```

#### Kafka Connection Failed

```bash
# Check Kafka pods
kubectl -n miniurl get pods -l app=kafka

# Check Kafka service
kubectl -n miniurl get svc kafka-service

# Check Kafka logs
kubectl -n miniurl logs <kafka-pod-name>
```

---

## Quick Reference

### Common Commands

```bash
# Local Development
mvn clean install -DskipTests
docker-compose up -d

# Kubernetes Management
kubectl -n miniurl get pods
kubectl -n miniurl rollout status deployment/api-gateway
kubectl -n miniurl scale deployment/api-gateway --replicas=5

# Monitoring
kubectl -n miniurl port-forward svc/prometheus 9090:9090
kubectl -n miniurl port-forward svc/grafana 3000:3000
```

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `APP_JWT_SECRET` | JWT signing secret | None | ✅ Yes |
| `APP_BASE_URL` | Application base URL | http://localhost:8080 | ✅ Yes |
| `MYSQL_ROOT_PASSWORD` | MySQL root password | root | ✅ Yes |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | localhost:9092 | ✅ Yes |
| `EUREKA_SERVER_URL` | Eureka server URL | http://localhost:8761/eureka/ | ✅ Yes |

---

## Support

For issues or questions:
1. Check logs: `kubectl -n miniurl logs <pod-name>`
2. Review this guide
3. Check application health: `curl http://localhost:8080/api/health`
4. Check Eureka dashboard: `http://localhost:8761`
