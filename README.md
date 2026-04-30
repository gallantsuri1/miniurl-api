# MyURL API - High-Throughput URL Shortener (Microservices)

MyURL API is a scalable, high-performance URL shortening system migrated from a monolith to a microservices architecture. It is designed to handle high throughput (targeting ~10k redirects/sec) using a reactive redirect path, distributed caching, and an event-driven architecture.

## 🏗️ Architecture Overview

The system is split into several specialized services to ensure independent scalability and fault isolation.

### Service Map
- **API Gateway**: The single entry point. Handles routing, RS256 JWT validation (via JWKS), and Redis-backed distributed rate limiting.
- **Identity Service**: Manages users, authentication, and RSA key pairs for asymmetric JWT signing.
- **URL Service**: Handles URL creation, management, and generates collision-free short codes using Snowflake IDs.
- **Redirect Service**: A dedicated **Reactive (WebFlux)** service optimized for the "hot path" (`/r/{code}`). Uses a "Redis-first" resolution strategy.
- **Feature Service**: Manages global and user-specific feature flags with Redis caching.
- **Notification Service**: Asynchronous worker that consumes events from Kafka to send emails.
- **Analytics Service**: Asynchronous worker that consumes click events from Kafka to persist analytics data.
- **Eureka Server**: Provides service discovery for all microservices.

### Key Design Patterns
- **Asymmetric Security**: Uses **RS256**. The Identity Service signs tokens with a private key; the Gateway validates them using a public key fetched via a JWKS endpoint.
- **Event-Driven Communication**: Uses **Apache Kafka** for non-blocking operations (Notifications, Analytics).
- **Reliability**: Implements the **Outbox Pattern** in Identity and URL services to ensure atomic database updates and guaranteed event delivery.
- **High Throughput**: The Redirect Service uses non-blocking I/O (Spring WebFlux) and Redis to minimize latency.
- **Database per Service**: Physical separation of MySQL databases to prevent coupling and allow independent scaling.

---

## 🛠️ Tech Stack

- **Backend**: Java 17, Spring Boot 3.2.0, Spring Cloud 2023.0.0
- **Reactive Stack**: Spring WebFlux (Redirect Service)
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
- **Messaging**: Apache Kafka
- **Caching/Rate Limiting**: Redis
- **Databases**: MySQL (Multiple instances)
- **Observability**: OpenTelemetry, Micrometer, Prometheus, Grafana
- **Deployment**: Kubernetes, Helm, Docker, GitHub Actions

---

## 🚀 Getting Started

### Prerequisites
- **JDK 17** or higher
- **Maven 3.8+**
- **Docker & Docker Compose**
- **kubectl** + **Helm 3.14+** (for Kubernetes deployment)
- **Minikube** (optional, for local K8s testing)

### 1. Local Development (Docker Compose)

The fastest way to get a full development environment:

```bash
docker compose up -d
```

This starts all 8 services plus Kafka, Zookeeper, Redis, 3× MySQL, Prometheus, and Grafana.

See [Local Development with Docker Compose](docs/deployment/local-docker-compose.md) for details.

### 2. Build the Project

```bash
mvn clean install -DskipTests
```

### 3. Running Individual Services

**Order of startup:**
1. `eureka-server`
2. `identity-service`, `url-service`, `feature-service`
3. `api-gateway`, `redirect-service`
4. `notification-service`, `analytics-service`

```bash
mvn spring-boot:run -pl identity-service
```

### 4. Local Kubernetes (Minikube)

For testing Helm charts, canary deployments, and HPA:

```bash
minikube start --cpus=4 --memory=8192
helm upgrade --install miniurl ./helm/miniurl \
  --values ./helm/miniurl/values-local.yaml \
  --namespace miniurl --create-namespace --wait
```

See [Local Development with Minikube](docs/deployment/local-minikube.md) for details.

### 5. Kubernetes Deployment (Helm)

MiniURL is deployed via a **Helm chart** ([`helm/miniurl/`](helm/miniurl/)) — the single source of truth for all Kubernetes resources.

```bash
# Development
helm upgrade --install miniurl ./helm/miniurl \
  --values ./helm/miniurl/values-dev.yaml \
  --namespace miniurl --create-namespace --wait

# Production
helm upgrade --install miniurl ./helm/miniurl \
  --values ./helm/miniurl/values-prod.yaml \
  --set globalConfig.IMAGE_TAG=sha-{hash} \
  --namespace miniurl --create-namespace --wait
```

---

## 🔄 CI/CD Pipeline

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| [PR Validation](.github/workflows/pr-validation.yml) | Pull request | Build, test, Helm lint, Docker (`pr-N` tag) |
| [Deploy to Dev](.github/workflows/deploy-dev.yml) | Push to main | Build `sha-{hash}`, deploy to dev, smoke test |
| [Deploy to Prod](.github/workflows/deploy-prod.yml) | Manual | Canary 10%→25%→50%→100% with approval gates |
| [Rollback](.github/workflows/rollback.yml) | Manual | `helm rollback` with smoke tests |
| [Bootstrap Environment](.github/workflows/bootstrap-environment.yml) | Manual | Provision namespace, secrets, infra, monitoring, MiniURL |
| [Release](.github/workflows/release.yml) | `v*` tag | Multi-arch build, semver images |

See [GitHub Actions Reference](docs/deployment/github-actions.md) and [Release Process](docs/deployment/release-process.md) for full details.

---

## 🔐 Security Architecture

The system uses **RS256 (Asymmetric)** JWTs for secure, stateless authentication.

1. **Token Issuance**: `identity-service` generates a key pair. It signs JWTs using the **Private Key**.
2. **Public Key Distribution**: `identity-service` exposes a `/jwks.json` endpoint containing the **Public Key**.
3. **Token Validation**: `api-gateway` fetches the public key from the JWKS endpoint and validates incoming tokens without needing to call the Identity Service for every request.

Secrets are managed via Kubernetes Secrets (`db-secrets`, `jwt-rsa-keys`, `smtp-credentials`) — never stored in values files or committed to the repository.

---

## 📈 Observability

- **Distributed Tracing**: Integrated via **OpenTelemetry**. Every request is tracked across services using a unique Trace ID.
- **Metrics**: Exposed via **Prometheus** endpoints (`/actuator/prometheus`) in every service. Scraping configured via pod annotations.
- **Logging**: Centralized logging (ELK Stack) is configured for production environments.
- **Dashboards**: Grafana dashboards available in `deploy/monitoring/dashboards/`.

---

## 📖 API Reference (via Gateway)

All requests should be sent to the **API Gateway** (default port `8080`).

### Public Endpoints
- `GET /r/{code}` → Redirects to original URL (handled by Redirect Service)
- `POST /api/auth/signup` → User registration
- `POST /api/auth/login` → Authentication
- `GET /api/features/global` → Get global feature flags

### Authenticated Endpoints
- `POST /api/urls` → Create short URL
- `GET /api/urls` → List user's URLs
- `DELETE /api/urls/{id}` → Delete a URL
- `PUT /api/users/profile` → Update profile

### Admin Endpoints
- `POST /api/admin/invites` → Send email invitation
- `GET /api/admin/users` → Manage all users
- `POST /api/admin/features` → Manage feature flags

---

## 📁 Project Structure

```text
.
├── common/                     # Shared DTOs, Exceptions, and Utils
├── api-gateway/                # Spring Cloud Gateway (Routing, Security, Rate Limiting)
├── eureka-server/              # Service Discovery
├── identity-service/           # Auth, User Management, JWKS
├── url-service/                # URL Management, Snowflake ID Generation
├── redirect-service/           # Reactive Redirect Path (High Throughput)
├── feature-service/            # Feature Flag Management
├── notification-service/       # Async Email Worker (Kafka Consumer)
├── analytics-service/          # Click Tracking Worker (Kafka Consumer)
├── helm/miniurl/               # Helm Chart (single source of truth for K8s)
├── .github/workflows/          # CI/CD Pipelines (6 workflows)
├── docs/deployment/            # Deployment Documentation
├── deploy/                     # Deployment Scripts & Monitoring
├── k8s/                        # Legacy K8s Manifests (deprecated — use Helm)
├── scripts/                    # Utility Scripts
├── terraform/                  # Infrastructure as Code
└── docker-compose.yml          # Local Development Environment
```

---

## 📚 Deployment Documentation

- [Initial Environment Bootstrap](docs/deployment/initial-bootstrap.md) — Provision a new environment from scratch
- [Release Process](docs/deployment/release-process.md) — Full release flow with canary deployments
- [GitHub Actions Reference](docs/deployment/github-actions.md) — All 6 CI/CD workflows explained
- [Local Docker Compose](docs/deployment/local-docker-compose.md) — Feature development without K8s
- [Local Minikube](docs/deployment/local-minikube.md) — Test Helm charts and canary locally
