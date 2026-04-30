# MiniURL API - Setup and Deployment Guide

## Quick Start

```bash
# 1. Install prerequisites
#    JDK 17+, Maven 3.8+, Docker, kubectl, Helm 3.14+

# 2. Start infrastructure
docker compose up -d

# 3. Build the project
mvn clean install -DskipTests

# 4. Run a service
mvn spring-boot:run -pl identity-service
```

## Deployment Options

| Environment | Guide | Values File | Scripts |
|-------------|-------|-------------|---------|
| **Local K8s (Minikube)** | [Local Minikube Guide](docs/development/local-minikube.md) | `values-local.yaml` | `scripts/local/minikube-*.sh` |
| **Home Server (K3s)** | [Home Server K3s Guide](docs/deployment/home-server-k3s.md) | `values-home.yaml` | CI/CD via self-hosted runner |
| **Development (CI)** | [GitHub Actions Reference](docs/deployment/github-actions.md) | `values-dev.yaml` | `deploy-dev.yml` |
| **Production (Canary)** | [Release Process](docs/deployment/release-process.md) | `values-prod.yaml` + `values-canary.yaml` | `deploy-prod.yml` |
| **Docker Compose** | [Local Docker Compose](docs/deployment/local-docker-compose.md) | — | `docker compose up -d` |

### Minikube (one-shot)

```bash
./scripts/local/minikube-start.sh
./scripts/local/minikube-build-images.sh
./scripts/local/minikube-deploy.sh
./scripts/local/minikube-smoke-test.sh
```

### K3s Home Server (manual)

```bash
# After K3s is installed and the self-hosted runner is configured:
helm upgrade --install miniurl ./helm/miniurl \
  --values ./helm/miniurl/values-home.yaml \
  --set globalConfig.IMAGE_TAG=sha-{hash} \
  --namespace miniurl --create-namespace --wait --atomic
```

## GitHub Environments & Secrets

### Environments

Create these in **Repo → Settings → Environments**:

| Environment | Purpose | Required Reviewers |
|-------------|---------|-------------------|
| `development` | Auto-deploy on merge to main | None |
| `production-canary-10` | Canary phase 1 (10%) | 1 |
| `production-canary-25` | Canary phase 2 (25%) | 1 |
| `production-canary-50` | Canary phase 3 (50%) | 1 |

### Secrets (per environment)

| Secret | Purpose |
|--------|---------|
| `DB_ROOT_PASSWORD` | MySQL root password |
| `JWT_SECRET` | RS256 signing key (generate with `openssl rand -base64 64`) |
| `SMTP_HOST` | SMTP server hostname |
| `SMTP_PORT` | SMTP server port |
| `SMTP_USERNAME` | SMTP username |
| `SMTP_PASSWORD` | SMTP password |
| `GHCR_PULL_TOKEN` | GitHub PAT with `read:packages` scope |

**No `KUBECONFIG` secret is needed** when using the self-hosted runner — it reads `~/.kube/config` from the home server directly.

## Self-Hosted Runner (Home Server)

The repository uses a self-hosted runner installed on the home server for deployment. The runner:

- Connects **outbound** to GitHub over HTTPS (WebSocket) — no inbound ports needed
- Reads kubeconfig from `~/.kube/config` — no SSH access or kubeconfig secret
- Runs all 4 deploy/rollback/bootstrap workflows with `[self-hosted, home-server]` labels

Setup: follow the [Home Server K3s Guide](docs/deployment/home-server-k3s.md), section 7.

## Build & Test Commands

```bash
# Full build (skip tests)
mvn clean install -DskipTests

# All tests
mvn clean test -DskipITs

# Single module
mvn test -pl url-service -am

# Single test class
mvn test -pl miniurl-monolith -am -Dtest="UrlCrudIntegrationTest"

# Single test method
mvn test -pl miniurl-monolith -am -Dtest="UrlCrudIntegrationTest#createUrlWithValidRequest_shouldReturnShortenedUrl"
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `APP_BASE_URL` | Application base URL | `http://localhost:8080` |
| `EUREKA_SERVER_URL` | Eureka server URL | `http://localhost:8761/eureka/` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:9092` |
| `MYSQL_ROOT_PASSWORD` | MySQL root password | — |

## Reference

- [CLAUDE.md](CLAUDE.md) — Build commands, architecture overview, test patterns
- [CI/CD Pipeline (README)](README.md#cicd-pipeline) — All 5 workflows and their responsibilities
- [Troubleshooting (Home Server K3s)](docs/deployment/home-server-k3s.md#12-troubleshooting) — Runner offline, image pull failures, pod issues
- [Troubleshooting (Minikube)](docs/development/local-minikube.md#6-troubleshooting) — Minikube-specific issues
