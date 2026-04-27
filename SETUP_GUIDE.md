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

- **Java 21** or higher
- **Maven 3.8+**
- **Docker & Docker Compose** (for infrastructure services)
- **Helm 3** (for Kubernetes deployment)
- **kubectl** (for Kubernetes deployment)

### Step 1: Install Prerequisites

#### macOS
```bash
# Install Java 21
brew install openjdk@21

# Install Maven
brew install maven

# Install Docker Desktop
brew install --cask docker
```

#### Linux (Ubuntu/Debian)
```bash
# Install Java 21
sudo apt update
sudo apt install openjdk-21-jdk maven -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
```

#### Windows
```bash
# Download and install from:
# Java: https://adoptium.net/temurin/releases/?version=21
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

## Kubernetes Deployment with Helm

The project includes a Helm chart at `helm/miniurl/` that deploys all 8 microservices. Infrastructure (MySQL, Kafka, Redis) runs via Docker Compose locally or separately on the server.

### Chart Structure

```
helm/miniurl/
  Chart.yaml
  values.yaml          # defaults (ghcr.io images, prod config)
  values-dev.yaml      # local dev overrides (1 replica, local images)
  values-prod.yaml     # prod overrides (replicas, HPA)
  templates/
    configmap.yaml
    deployment.yaml    # single template, iterates all services
    service.yaml
    hpa.yaml
```

### Dev Deployment (Local — Minikube/Kind)

Start infrastructure, build images, and deploy with Helm:

```bash
# Terminal 1: Start infrastructure
docker compose up -d

# Terminal 2: Start K8s cluster and deploy
minikube start
eval $(minikube docker-env)
docker compose build
helm install miniurl ./helm/miniurl --values ./helm/miniurl/values-dev.yaml

# Verify
kubectl -n miniurl get pods
minikube service -n miniurl api-gateway
```

### Prod Deployment (Home Server)

A **self-hosted GitHub Actions runner** on the server handles automated deployment. Manual deploy:

```bash
# Prerequisites on the server
# Install tools
sudo apt install docker.io
curl -fsSL https://get.helm.sh/helm-v3.14.0-linux-amd64.tar.gz | tar xz && sudo mv linux-amd64/helm /usr/local/bin/
curl -LO "https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl" && chmod +x kubectl && sudo mv kubectl /usr/local/bin/

# Add self-hosted runner (one-time setup)
# Go to: https://github.com/gallantsuri1/miniurl-api/settings/actions/runners
# Click "New self-hosted runner", choose Linux, run the commands
# Register as a service:
sudo ./svc.sh install
sudo ./svc.sh start

# Manual deploy (if not using CI)
helm upgrade --install miniurl ./helm/miniurl \
  --values ./helm/miniurl/values-prod.yaml \
  --namespace miniurl --create-namespace \
  --atomic --timeout 5m
```

### Automated Deploy (CI/CD)

1. Merge PR to `main` → CI builds Docker images on GitHub runners
2. Images pushed to `ghcr.io/...:latest` + Docker Hub
3. Self-hosted runner on the server picks up the `deploy-to-kubernetes` job
4. Runs `helm upgrade --install --atomic` with `values-prod.yaml`
5. On failure, Helm auto-rolls back

### Verify Deployment

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

The project includes three workflows:

1. **PR Validation** (`.github/workflows/pr-validation.yml`)
   - Runs on pull requests to `main`
   - Builds, runs tests
   - Pushes Docker images to ghcr.io (`pr-<number>`) and Docker Hub (SNAPSHOT version)

2. **Main Pipeline** (`.github/workflows/main-pipeline.yml`)
   - Runs on push/merge to `main`
   - Builds, runs tests
   - Pushes Docker images to ghcr.io (`latest`, `sha-<commit>`, `main`) and Docker Hub (release version)
   - **Auto-deploys** via a self-hosted runner on the server using `helm upgrade --install`
   - Auto-bumps patch version and commits `[skip ci]`

3. **Release** (`.github/workflows/release.yml`)
   - Triggered by `v*` tags or manual dispatch
   - Pushes to ghcr.io and Docker Hub with version tags

### Deploy Flow

```
Push/merge to main → Build & test → Push Docker images
                                       ↓
                      Self-hosted runner picks up job
                                       ↓
                      helm upgrade --install --atomic
                                       ↓
                      Verify rollouts → Slack notification
```

### Required GitHub Secrets

| Secret | Description |
|--------|-------------|
| `DOCKER_USER` | Docker Hub username |
| `DOCKER_API_TOKEN` | Docker Hub API token |
| `SLACK_WEBHOOK_URL` | Slack webhook for deploy notifications |

### Self-Hosted Runner Setup

The deploy job requires a self-hosted runner on the server with these tools:

```bash
sudo apt install docker.io
sudo snap install helm --classic
sudo snap install kubectl --classic
```

To add the runner:
1. Go to repo **Settings → Actions → Runners → Add runner**
2. Follow the Linux instructions
3. Install as a service: `sudo ./svc.sh install && sudo ./svc.sh start`

### Version Management

- **Development**: `1.0.0-SNAPSHOT` — all PRs and local builds
- **Main merge**: Auto-bumps patch → `1.0.1-SNAPSHOT`
- **Release**: `mvn release:prepare` creates tag `v1.0.0` → `mvn release:perform` builds release

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
