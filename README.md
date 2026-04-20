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
- **Deployment**: Kubernetes, Docker

---

## 🚀 Getting Started

### Prerequisites
- **JDK 17** or higher
- **Maven 3.8+**
- **Docker & Docker Compose**
- **kubectl** (for Kubernetes deployment)

### 1. Local Infrastructure Setup
The project includes a `docker-compose.yml` that spins up all required infrastructure:
- Eureka Server
- Kafka & Zookeeper
- Redis
- MySQL (Identity, URL, and Feature databases)

```bash
docker-compose up -d
```

### 2. Build the Project
Build all modules from the root directory:
```bash
mvn clean install -DskipTests
```

### 3. Running the Services
You can run the services via your IDE or using Maven:

**Order of startup:**
1. `eureka-server`
2. `identity-service`, `url-service`, `feature-service`
3. `api-gateway`, `redirect-service`
4. `notification-service`, `analytics-service`

Example to run a service:
```bash
mvn spring-boot:run -pl identity-service
```

### 4. Kubernetes Deployment
The Kubernetes manifests are located in the `k8s/` directory.

1. **Apply Global Config**:
   ```bash
   kubectl apply -f k8s/infrastructure/global-config.yaml
   ```
2. **Deploy Services**:
   ```bash
   kubectl apply -f k8s/services/
   ```

---

## 🔐 Security Architecture

The system uses **RS256 (Asymmetric)** JWTs for secure, stateless authentication.

1. **Token Issuance**: `identity-service` generates a key pair. It signs JWTs using the **Private Key**.
2. **Public Key Distribution**: `identity-service` exposes a `/jwks.json` endpoint containing the **Public Key**.
3. **Token Validation**: `api-gateway` fetches the public key from the JWKS endpoint and validates incoming tokens without needing to call the Identity Service for every request.

---

## 📈 Observability

- **Distributed Tracing**: Integrated via **OpenTelemetry**. Every request is tracked across services using a unique Trace ID.
- **Metrics**: Exposed via **Prometheus** endpoints in every service.
- **Logging**: Centralized logging (ELK Stack) is configured for production environments.

---

## 📖 API Reference (via Gateway)

All requests should be sent to the **API Gateway** (default port `8080`).

### Public Endpoints
- `GET /r/{code}` $\rightarrow$ Redirects to original URL (handled by Redirect Service)
- `POST /api/auth/signup` $\rightarrow$ User registration
- `POST /api/auth/login` $\rightarrow$ Authentication
- `GET /api/features/global` $\rightarrow$ Get global feature flags

### Authenticated Endpoints
- `POST /api/urls` $\rightarrow$ Create short URL
- `GET /api/urls` $\rightarrow$ List user's URLs
- `DELETE /api/urls/{id}` $\rightarrow$ Delete a URL
- `PUT /api/users/profile` $\rightarrow$ Update profile

### Admin Endpoints
- `POST /api/admin/invites` $\rightarrow$ Send email invitation
- `GET /api/admin/users` $\rightarrow$ Manage all users
- `POST /api/admin/features` $\rightarrow$ Manage feature flags

---

## 📁 Project Structure

```text
.
├── common/                 # Shared DTOs, Exceptions, and Utils
├── api-gateway/            # Spring Cloud Gateway (Routing, Security, Rate Limiting)
├── eureka-server/          # Service Discovery
├── identity-service/       # Auth, User Management, JWKS
├── url-service/            # URL Management, Snowflake ID Generation
├── redirect-service/       # Reactive Redirect Path (High Throughput)
├── feature-service/        # Feature Flag Management
├── notification-service/   # Async Email Worker (Kafka Consumer)
├── analytics-service/      # Click Tracking Worker (Kafka Consumer)
├── k8s/                    # Kubernetes Manifests
└── docker-compose.yml      # Local Infrastructure
```
