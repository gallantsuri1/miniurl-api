# MiniURL API - URL Shortener API

A RESTful URL shortener API built with Spring Boot, featuring user management, role-based access control, JWT authentication, rate limiting, feature flags, email invitations, and usage tracking.

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![MySQL](https://img.shields.io/badge/Database-MySQL-orange)
![Docker](https://img.shields.io/badge/Docker-Hub-blue)
![Swagger](https://img.shields.io/badge/OpenAPI-3.0-green)
![License](https://img.shields.io/badge/License-MIT-green)
![Tests](https://img.shields.io/badge/tests-94%20passing-brightgreen)

## ⚡ Quick Start

### 📖 Full Setup Guide
For detailed setup instructions, see [SETUP_GUIDE.md](SETUP_GUIDE.md)

### Option 1: Docker Compose (Recommended)

```bash
# 1. Clone repository
git clone https://github.com/gallantsuri1/miniurl.git
cd miniurl

# 2. Copy environment file
cp .env.example .env

# 3. Generate secure JWT secret (REQUIRED)
echo "APP_JWT_SECRET=$(openssl rand -base64 64)" >> .env

# 4. Configure database (MySQL runs in its own container)
# Edit .env with: SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD

# 5. Initialize database (run once, outside Docker)
mysql -h <mysql-host> -u <user> -p < scripts/init-db.sql

# 6. Start the application
docker compose up -d

# 7. Check status
docker compose ps
docker compose logs -f miniurl-api
```

**Access:** http://localhost:8080
**Default login:** admin / admin123# (⚠️ CHANGE IMMEDIATELY!)

### Option 2: Local Development

```bash
# Prerequisites: Java 17, Maven, MySQL 8.0

# 1. Start MySQL
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=rootpass \
  -e MYSQL_DATABASE=miniurldb -p 3306:3306 mysql:8.0

# 2. Initialize database
mysql -h localhost -u root -prootpass miniurldb < scripts/init-db.sql

# 3. Clone and configure
git clone https://github.com/gallantsuri1/miniurl.git
cd miniurl
cp .env.example .env
echo "APP_JWT_SECRET=$(openssl rand -base64 64)" >> .env

# 4. Configure CORS and base URL (REQUIRED for UI access)
export APP_BASE_URL=http://localhost:8080
export APP_UI_BASE_URL=http://localhost:3000
export APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080

# 5. Build and run
mvn clean package -DskipTests
java -jar target/miniurl-api-1.0.0.jar --spring.profiles.active=dev
```

**Access:** http://localhost:8080
**Swagger UI:** http://localhost:8080/swagger-ui.html (dev mode only)

### Option 3: Development Mode with Hot Reload

```bash
git clone https://github.com/gallantsuri1/miniurl.git
cd miniurl
cp .env.example .env
echo "APP_JWT_SECRET=$(openssl rand -base64 64)" >> .env
echo "APP_BASE_URL=http://localhost:8080" >> .env
echo "APP_UI_BASE_URL=http://localhost:3000" >> .env
echo "APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080" >> .env

# Ensure MySQL is running locally and database is initialized:
mysql -h localhost -u root -prootpass miniurldb < scripts/init-db.sql

# Start with hot reload (connects to MySQL on host via host.docker.internal)
docker compose -f docker-compose.dev.yml up
```

---

## 🔐 Security Notice

**Before deploying to production:**

1. **Generate JWT Secret**: `APP_JWT_SECRET=$(openssl rand -base64 64)`
2. **Change Default Passwords**: Admin password resets to `admin123#` on first creation only
3. **Enable HTTPS**: Always use HTTPS in production
4. **Use Production Profile**: Deploy with `prod` profile to disable Swagger/OpenAPI
5. **Configure CORS**: Set `APP_CORS_ALLOWED_ORIGINS` to your frontend domain(s)
6. **Set Base URL**: Set `APP_BASE_URL` to your frontend URL
7. **Review Security Checklist** in `.env.example`

## 🔄 Application Modes

| Feature | Development (`dev`) | Production (`prod`) |
|---------|---------------------|---------------------|
| **Swagger/OpenAPI** | ✅ Enabled | ❌ Disabled |
| **SQL Logging** | ✅ Verbose | ❌ Disabled |
| **Logging Level** | DEBUG | INFO/WARN |

**Switch Profiles:**
```bash
# Maven
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Docker
docker run -e SPRING_PROFILES_ACTIVE=prod miniurl/miniurl-api:latest
```

---

## 🚀 Features

### Core Functionality
- **URL Shortening** - Auto-generated 6-character codes or custom aliases
- **Click Tracking** - Track access count for each URL
- **User Ownership** - URLs belong to the creator
- **Public Redirects** - No auth required for `/r/{shortCode}`
- **URL Creation Limits** - Fair usage policy:
  - **10 URLs per minute** - Auto-clears after 60 seconds
  - **100 URLs per day** - Resets at midnight
  - **1000 URLs per month** - Resets on 1st of month
  - **API Endpoint** - `/api/urls/usage-stats` to view usage

### User Management
- **Invitation-Only Registration** - Admin sends email invites to users
- **Role-Based Access Control** - ADMIN and USER roles
- **Profile Management** - Update name, email, password
- **Account Settings** - Export data (JSON), change password, delete account
- **Admin Dashboard** - User management (activate, deactivate, suspend, change roles) via API
- **Email Invitations** - Admin-only feature to invite users via email

### Feature Management
- **Feature Flags** - Toggle features without code changes
- **Role-Based Features**: Features are organized by target role:
  - **USER Role**: Features that apply to regular users (Dashboard, Profile, Settings, Export)
  - **ADMIN Role**: Admin-only management features (Email Invites, User Management, Feature Management)
- **Admin Control**: ADMIN users can toggle features for either role via API
- **Real-Time Enforcement** - API level checks

### Authentication & Security
- **JWT Authentication** - 60-minute tokens
- **Email Invitation** - Admin-sent invites prove email ownership (no verification needed)
- **Password Reset** - Token-based reset flow
- **BCrypt Password Hashing** - Secure storage
- **Account Lockout** - 5-minute lockout after 5 failed attempts
- **CORS Protection** - Configurable allowed origins via environment variable
- **Rate Limiting** - Dual-layer rate limiting (per-IP + per-username) for login endpoints, per-IP for all other sensitive endpoints

---

## 📋 Prerequisites

- Java 17 or higher
- Maven 3.8+
- MySQL 8.0+ (running externally)
- Docker & Docker Compose (optional)

## 🛠️ Running Locally

### 1. Database Setup

```bash
# Initialize MySQL database
bash scripts/init-db.sql
# Or
bash scripts/init-db.sh
```

### 2. Configure Environment

```bash
cp .env.example .env
```

Edit `.env`:
```bash
# Database (macOS/Windows)
SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/miniurldb
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=rootpass

# Linux users: Use host IP (e.g., 172.17.0.1)
# SMTP (optional)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
```

### 3. Run Application

```bash
mvn spring-boot:run
# Or
mvn clean package -DskipTests
java -jar target/miniurl-api-1.0.0.jar
```

---

## 🐳 Docker Deployment

MySQL runs in its own container — the `docker-compose.yml` only manages the API container.

### Prerequisites

- Docker & Docker Compose installed
- MySQL instance accessible from the Docker network
- Database initialized with `scripts/init-db.sql` (manual step)

### Initialize Database

Run the init script against your MySQL container:

```bash
# Method 1: Interactive (prompts for password — recommended)
docker exec -i mysql_db_miniurl mysql -u root -p miniurldb < scripts/init-db.sql

# Method 2: Direct (quote password if it contains special characters like !@#$)
docker exec -i mysql_db_miniurl mysql -u root -p'<password>' miniurldb < scripts/init-db.sql
```

**Verify initialization:**

```bash
docker exec -i mysql_db_miniurl mysql -u root -p'<password>' miniurldb -e "
  SELECT COUNT(*) AS roles FROM roles;
  SELECT COUNT(*) AS features FROM features;
  SELECT COUNT(*) AS global_flags FROM global_flags;
"
```

### Using Docker Compose

MySQL and the API run as separate compose stacks on the same host.

```bash
# 1. Start MySQL
docker compose -f docker-compose-mysql.yml up -d

# 2. Initialize database (run once)
docker exec -i mysql_db_miniurl mysql -u root -p miniurldb < scripts/init-db.sql

# 3. Configure environment
cp .env.example .env
# Edit .env: set SPRING_DATASOURCE_URL to jdbc:mysql://mysql_db_miniurl:3306/miniurldb...
# Set SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, APP_JWT_SECRET

# 4. Start the API
docker compose pull
docker compose up -d

# View logs
docker compose logs -f miniurl-api

# Stop
docker compose down
docker compose -f docker-compose-mysql.yml down
```

### Nginx Reverse Proxy (Optional)

Route all traffic through a single port (8080), splitting between API and UI:

```bash
# Start Nginx proxy
docker compose -f docker-compose-nginx.yml up -d
```

**Routing:**
| Path | Backend | Port |
|------|---------|------|
| `/api/*` | MiniURL API | 8090 |
| `/r/*` | Short URL redirects | 8090 |
| `/*` | Frontend UI | 3000 |

**Update your `.env`:**
```bash
APP_BASE_URL=http://localhost:8080
APP_UI_BASE_URL=http://localhost:8080
APP_CORS_ALLOWED_ORIGINS=http://localhost:8080
```

**Test routing:**
```bash
curl http://localhost:8080/api/health   # → API (8090)
curl http://localhost:8080/r/ABC123     # → API redirect (8090)
curl http://localhost:8080/             # → UI (3000)
```

### Production Deployment (Docker Hub Image)

Images are published to `gallantsuri1/miniurl-api`:

| Tag | Description |
|-----|-------------|
| `main` | Latest from main branch |
| `v1.0.0` | Specific release version |
| `sha-<hash>` | Specific commit SHA |

**Pull and deploy:**

```bash
docker pull gallantsuri1/miniurl-api:v1.0.0

docker run -d --name miniurl \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://<mysql-host>:3306/miniurldb?useSSL=false&serverTimezone=UTC" \
  -e SPRING_DATASOURCE_USERNAME="<db-user>" \
  -e SPRING_DATASOURCE_PASSWORD="<db-password>" \
  -e APP_JWT_SECRET="your-secure-jwt-secret-min-32-chars" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e APP_BASE_URL="https://api.example.com" \
  -e APP_UI_BASE_URL="https://example.com" \
  -e APP_CORS_ALLOWED_ORIGINS="https://example.com" \
  -p 8080:8080 \
  gallantsuri1/miniurl-api:v1.0.0
```

**Build and push your own image:**

```bash
# Build
docker build -t gallantsuri1/miniurl-api:v1.0.0 .

# Push to Docker Hub
docker push gallantsuri1/miniurl-api:v1.0.0
```

### Custom Image Reference

Use a different image/tag by setting `DOCKER_IMAGE` in `.env`:

```bash
# .env
DOCKER_IMAGE=gallantsuri1/miniurl-api:v1.0.0
```

### Build Locally

```bash
docker build -t miniurl-api:local .

docker run -d --name miniurl-app \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://<mysql-host>:3306/miniurldb" \
  -e SPRING_DATASOURCE_USERNAME="<db-user>" \
  -e SPRING_DATASOURCE_PASSWORD="<db-password>" \
  -e APP_JWT_SECRET="your-secure-jwt-secret" \
  -e APP_BASE_URL="http://localhost:8080" \
  -e APP_UI_BASE_URL="http://localhost:3000" \
  -e SPRING_PROFILES_ACTIVE=dev \
  -p 8080:8080 \
  miniurl-api:local
```

### Trigger a Release

Pushing a version tag triggers the CI workflow: **build → publish Docker image → notify via GitHub Issue**.

```bash
# Ensure you're on main/master
git checkout main
git pull origin main

# Tag the release (semantic versioning)
git tag v1.0.0

# Push tag to trigger release
git push origin v1.0.0
```

**What happens:**
1. **Docker image** built and pushed to Docker Hub with tags: `v1.0.0`, `latest`
2. **GitHub Issue** created and assigned to you — GitHub sends you an email notification with build details

**Manual trigger** (re-run or build a specific tag):
- Go to **Actions → Build and Publish Docker Image → Run workflow**
- Enter the tag (e.g., `v1.0.0`) and click **Run workflow**

**Required secrets** (GitHub → Settings → Secrets and variables → Actions):
| Secret | Description |
|--------|-------------|
| `DOCKER_USER` | Your Docker Hub username |
| `DOCKER_API_TOKEN` | Docker Hub access token ([generate](https://hub.docker.com/settings/security)) |

**Notes:**
- Only works on `main`/`master` branches
- Tag must be on the latest commit of `main`/`master`
- On failure, a GitHub Issue is created with the build error and link to logs
- Workflow logs: `Actions → Build and Publish Docker Image`

---

## 📡 Endpoints

### REST API
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/login` | Login and get JWT token | No |
| POST | `/api/auth/signup` | Register new user (invitation token required) | No |
| GET | `/api/auth/verify-email-invite?token={token}` | Validate email invitation token | No |
| GET | `/api/auth/verify-email?token={token}` | Validate password reset token | No |
| POST | `/api/auth/forgot-password` | Request password reset | No |
| POST | `/api/auth/reset-password` | Reset password | No |
| GET | `/api/profile` | Get user profile | Yes |
| PUT | `/api/profile` | Update profile | Yes |
| POST | `/api/urls` | Create short URL | Yes |
| GET | `/api/urls` | Get user's URLs (paginated) | Yes |
| DELETE | `/api/urls/{id}` | Delete URL | Yes |
| GET | `/api/admin/users` | List all users | ADMIN |
| PUT | `/api/admin/users/{id}/role` | Update user role | ADMIN |
| POST | `/api/admin/email-invites/send` | Send email invitation | ADMIN |
| GET | `/api/features` | Get features for your role | Yes (all users) |
| GET | `/api/admin/features` | Get ADMIN role features | ADMIN |
| GET | `/api/admin/features/all` | Get all features (USER + ADMIN) | ADMIN |
| GET | `/api/admin/features/role/{role}` | Get features by role | ADMIN |
| PUT | `/api/admin/features/{key}/toggle` | Toggle feature (set role optional) | ADMIN |
| PUT | `/api/admin/features/{key}/enable` | Enable feature flag | ADMIN |
| PUT | `/api/admin/features/{key}/disable` | Disable feature flag | ADMIN |
| GET | `/api/health` | Health check | No |
| GET | `/r/{shortCode}` | Redirect to original URL | No |

---

## 🚩 Feature Flags - Role-Based Management

### Overview

Feature flags are organized into a **normalized schema** with separate tables:
- **Features Table**: Master feature definitions (10 features)
- **Feature Flags Table**: Role-based enabled status (16 flags - 8 per role)
- **Global Flags Table**: Features not tied to roles (2 flags - GLOBAL_USER_SIGNUP, GLOBAL_APP_NAME)

ADMIN users can manage features for both USER and ADMIN roles independently.

### Quick Reference

**Features (10 total):**
1. `GLOBAL_USER_SIGNUP` - User Sign Up (GLOBAL)
2. `GLOBAL_APP_NAME` - App Name (GLOBAL)
3. `PROFILE_PAGE` - Profile Page
4. `EXPORT_JSON` - Export to JSON
5. `URL_SHORTENING` - URL Shortening
6. `DASHBOARD` - Dashboard
7. `SETTINGS_PAGE` - Settings Page
8. `EMAIL_INVITE` - Email Invitations
9. `USER_MANAGEMENT` - User Management
10. `FEATURE_MANAGEMENT` - Feature Management

**Total Records:**
- Features: 10 rows
- Feature Flags: 16 rows (8 features × 2 roles)
- Global Flags: 2 rows (GLOBAL_USER_SIGNUP, GLOBAL_APP_NAME)

### API Endpoints

#### Public (No Authentication)

**GET `/api/features/global`**
- Returns all global flags (e.g., GLOBAL_USER_SIGNUP status)
- No authentication required

#### Authenticated Users

**GET `/api/features`**
- Returns feature flags for the authenticated user's role
- USER role → Returns 8 USER features
- ADMIN role → Returns 8 ADMIN features

#### ADMIN Only

**Feature Flags Management:**

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/features` | GET | Get ALL feature flags (both roles) |
| `/api/admin/features/{id}` | GET | Get feature flag by ID |
| `/api/admin/features` | POST | Create new feature with role flags |
| `/api/admin/features/{id}/toggle` | PUT | Toggle feature flag on/off |
| `/api/admin/features/{id}` | DELETE | Delete feature flag |

**Global Flags Management:**

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/features/global` | GET | Get all global flags |
| `/api/admin/features/global/{id}` | GET | Get global flag by ID |
| `/api/admin/features/global` | POST | Create new global flag |
| `/api/admin/features/global/{id}/toggle` | PUT | Toggle global flag on/off |
| `/api/admin/features/global/{id}` | DELETE | Delete global flag |

**Self-Invitation:**

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/self-invite/send?email={email}&baseUrl={url}` | POST | Send self-invitation (requires GLOBAL_USER_SIGNUP enabled) |

### Example Requests

#### Get Your Role's Features
```bash
curl -X GET 'http://localhost:8080/api/features' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN'
```

**Response (USER role):**
```json
{
  "success": true,
  "message": "Features for USER role retrieved successfully",
  "data": {
    "features": [...],
    "count": 8,
    "role": "USER"
  }
}
```

#### Get Global Flags (Public - No Auth)
```bash
curl -X GET 'http://localhost:8080/api/features/global'
```

**Response:**
```json
{
  "success": true,
  "message": "Global flags retrieved successfully",
  "data": {
    "flags": [
      {
        "id": 1,
        "featureKey": "GLOBAL_USER_SIGNUP",
        "featureName": "User Sign Up",
        "enabled": true
      },
      {
        "id": 2,
        "featureKey": "GLOBAL_APP_NAME",
        "featureName": "MiniURL",
        "enabled": true
      }
    ],
    "count": 2
  }
}
```

#### Create Feature Flag (ADMIN)
```bash
curl -X POST 'http://localhost:8080/api/admin/features' \
  -H 'Authorization: Bearer ADMIN_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"featureKey":"NEW_FEATURE","featureName":"New Feature","description":"Feature description","adminEnabled":true,"userEnabled":true}'
```

**Request Body:**
- `featureKey` (required): Unique key for the feature (e.g., `DASHBOARD`)
- `featureName` (required): Display name for the feature
- `description` (required): Description of what the feature does
- `adminEnabled` (required): Whether the feature is enabled for ADMIN role
- `userEnabled` (required): Whether the feature is enabled for USER role

#### Create Global Flag (ADMIN)
```bash
curl -X POST 'http://localhost:8080/api/admin/features/global' \
  -H 'Authorization: Bearer ADMIN_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"featureKey":"APP_NAME","featureName":"App Name","description":"Application display name","enabled":true}'
```

**Request Body:**
- `featureKey` (required): Unique key for the feature
- `featureName` (required): Display name for the feature
- `description` (required): Description of the feature
- `enabled` (required): Whether the global flag is enabled

#### Toggle Feature Flag (ADMIN)
```bash
# Format 1: Object with enabled field
curl -X PUT 'http://localhost:8080/api/admin/features/1/toggle' \
  -H 'Authorization: Bearer ADMIN_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"enabled": false}'

# Format 2: Simple boolean
curl -X PUT 'http://localhost:8080/api/admin/features/1/toggle' \
  -H 'Authorization: Bearer ADMIN_TOKEN' \
  -H 'Content-Type: application/json' \
  -d 'true'
```

#### Delete Feature Flag (ADMIN)
```bash
curl -X DELETE 'http://localhost:8080/api/admin/features/1' \
  -H 'Authorization: Bearer ADMIN_TOKEN'
```

### Access Control

| Endpoint | USER Role | ADMIN Role |
|----------|-----------|------------|
| GET `/api/features/global` | ✅ Public (no auth) | ✅ Public (no auth) |
| GET `/api/features` | ✅ See USER features | ✅ See ADMIN features |
| GET `/api/admin/features` | ❌ | ✅ Get ALL flags |
| POST `/api/admin/features` | ❌ | ✅ Create flag |
| PUT `/api/admin/features/{id}/toggle` | ❌ | ✅ Toggle flag |
| DELETE `/api/admin/features/{id}` | ❌ | ✅ Delete flag |
| GET/POST/PUT/DELETE `/api/admin/features/global/**` | ❌ | ✅ Manage global flags |
```
Returns features for the specified role.

#### Toggle Feature (ADMIN Only)
```bash
PUT /api/admin/features/{key}/toggle
Authorization: Bearer <ADMIN_JWT_TOKEN>
Content-Type: application/json

{
  "enabled": true,
  "targetRole": "USER"
}
```

**Request Body:**
- `enabled` (required): Boolean to enable/disable the feature
- `targetRole` (optional): "USER" or "ADMIN" - changes which role the feature applies to

### Access Control

| Endpoint | USER Role | ADMIN Role |
|----------|-----------|------------|
| `GET /api/features` | ✅ See USER features | ✅ See ADMIN features |
| `GET /api/admin/features/all` | ❌ | ✅ See all features |
| `GET /api/admin/features` | ❌ | ✅ See ADMIN features |
| `GET /api/admin/features/role/{role}` | ❌ | ✅ Get specific role |
| `PUT /api/admin/features/{key}/toggle` | ❌ | ✅ Toggle any role's features |

## 📧 Email Invitation Signup Flow

### Overview

The application supports an email invitation system where admins can invite users to register. The flow works as follows:

1. **Admin sends invitation** via `POST /api/admin/email-invites/send`
2. **User receives email** with signup link containing invitation token
3. **User clicks link** and is redirected to signup page with token
4. **Frontend validates token** using `/api/auth/verify-email` (optional, for better UX)
5. **User fills signup form** (token included in request)
6. **Backend validates token** and creates account
7. **Email marked as verified** (no verification needed - admin invite proves ownership)
8. **Invitation marked as accepted**
9. **Congratulations email sent**
10. **User can login immediately**

### Step-by-Step Flow

#### 1. Admin Sends Invitation

```bash
curl -X POST 'http://localhost:8080/api/admin/email-invites/send?email=user@example.com&baseUrl=http://localhost:8080' \
  -H 'Authorization: Bearer ADMIN_TOKEN'
```

**Response:**
```json
{
  "success": true,
  "message": "Invitation sent to: user@example.com"
}
```

#### 2. User Receives Email

The email contains a signup link:
```
http://localhost:8080/signup?invite=aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1vW3xY5zA7bC9dE1fG3hI5jK7lM9nO1pQ
```

#### 3. Frontend Validates Token (Optional)

Before showing the signup form, validate the token:

```bash
curl -X GET 'http://localhost:8080/api/auth/verify-email-invite?token=aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1vW3xY5zA7bC9dE1fG3hI5jK7lM9nO1pQ'
```

**Response (Valid Token):**
```json
{
  "success": true,
  "message": "Invitation token is valid"
}
```

**Response (Invalid Token):**
```json
{
  "success": false,
  "message": "Invalid or expired invitation token: This invite has expired"
}
```

#### 4. User Signs Up with Token

```bash
curl -X POST 'http://localhost:8080/api/auth/signup' \
  -H 'Content-Type: application/json' \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "username": "johndoe",
    "password": "MySecure@Pass123",
    "invitationToken": "aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1vW3xY5zA7bC9dE1fG3hI5jK7lM9nO1pQ"
  }'
```

**Note:** Email is NOT required in the request - it's extracted from the invitation token.

**Password Requirements:**
- Minimum 8 characters

**Response (Success):**
```json
{
  "success": true,
  "message": "Successfully registered!"
}
```

**What Happens After Signup:**
1. ✅ User account created with provided password
2. ✅ Email marked as verified (no verification email needed)
3. 🎉 Congratulations email sent: "Congratulations {firstName} 🎊, you are successfully registered"
4. 📝 Invitation marked as ACCEPTED
5. 🔑 User can login immediately (no email verification required)

**Response (Invalid Token):**
```json
{
  "success": false,
  "message": "Invalid or expired invitation token: This invite has expired"
}
```

#### 5. User Logs In

Since email is already verified via the admin invite, the user can login immediately:

```bash
curl -X POST 'http://localhost:8080/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "johndoe",
    "password": "MySecure@Pass123"
  }'
```

#### 6. Invitation Status Updated

After successful registration, the invitation status is automatically changed from `PENDING` to `ACCEPTED`.

### Token Validation Rules

| Condition | Error Message |
|-----------|--------------|
| Token doesn't exist | `Invalid invite token` |
| Token expired (>7 days) | `This invite has expired` |
| Token revoked by admin | `This invite has been revoked` |
| Token already used | `This invite has already been used` |

### Key Differences from Regular Signup

| Feature | Regular Signup | Invitation Signup |
|---------|---------------|-------------------|
| Email verification | Required | ❌ Not required (already verified via invite) |
| Verification email | Sent | ❌ Not sent |
| Congratulations email | Sent | ✅ Sent |
| Can login immediately | ❌ No (must verify first) | ✅ Yes |
| Email in request | Required | ❌ Not required (from token) |
| Invitation token | Not required | ✅ Required |

---

### Pagination

Multiple endpoints support pagination to improve performance:

#### 1. GET /api/urls - User's URLs

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 10 | Number of items per page |
| `sortBy` | string | createdAt | Sort field: `id`, `originalUrl`, `shortCode`, `accessCount`, `createdAt` |
| `sortDirection` | string | desc | Sort direction: `asc` or `desc` |

**Example Requests:**
```bash
# Get first page (10 items, sorted by createdAt desc)
curl -X GET 'http://localhost:8080/api/urls' \
  -H 'Authorization: Bearer YOUR_TOKEN'

# Get second page with 20 items
curl -X GET 'http://localhost:8080/api/urls?page=1&size=20' \
  -H 'Authorization: Bearer YOUR_TOKEN'

# Sort by most accessed URLs
curl -X GET 'http://localhost:8080/api/urls?sortBy=accessCount&sortDirection=desc' \
  -H 'Authorization: Bearer YOUR_TOKEN'
```

**Response Format:**
```json
{
  "success": true,
  "message": "URLs retrieved successfully",
  "data": {
    "pagination": {
      "content": [...],
      "page": 0,
      "size": 10,
      "totalElements": 150,
      "totalPages": 15,
      "first": true,
      "last": false,
      "sortBy": "createdAt",
      "sortDirection": "desc"
    },
    "summary": {
      "totalUrls": 150
    }
  }
}
```

#### 2. GET /api/admin/users - All Users (Admin)

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 20 | Number of users per page |
| `status` | string | - | Filter by status: `ACTIVE`, `SUSPENDED`, `DELETED` |
| `search` | string | - | Search by username, email, firstName, or lastName (partial match) |
| `sortBy` | string | createdAt | Sort field: `id`, `firstName`, `lastName`, `email`, `username`, `createdAt`, `lastLogin`, `status` |
| `sortDirection` | string | desc | Sort direction: `asc` or `desc` |

**Example Requests:**
```bash
# Get all users with default pagination
curl -X GET 'http://localhost:8080/api/admin/users' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Get active users, page 1, sorted by email
curl -X GET 'http://localhost:8080/api/admin/users?status=ACTIVE&page=1&size=10&sortBy=email&sortDirection=asc' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Sort users by last login (most recent first)
curl -X GET 'http://localhost:8080/api/admin/users?sortBy=lastLogin&sortDirection=desc' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Search users by email (contains "gmail")
curl -X GET 'http://localhost:8080/api/admin/users?search=gmail' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Search for users named "john" (matches firstName, lastName, username, or email)
curl -X GET 'http://localhost:8080/api/admin/users?search=john' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Search active users by email domain
curl -X GET 'http://localhost:8080/api/admin/users?status=ACTIVE&search=@company.com' \
  -H 'Authorization: Bearer ADMIN_TOKEN'
```

**Response Format:**
```json
{
  "success": true,
  "message": "Users retrieved",
  "data": {
    "pagination": {
      "content": [...],
      "page": 0,
      "size": 20,
      "totalElements": 100,
      "totalPages": 5,
      "first": true,
      "last": false,
      "sortBy": "createdAt",
      "sortDirection": "desc"
    },
    "summary": {
      "totalUsers": 100,
      "activeUsers": 80,
      "suspendedUsers": 10,
      "deletedUsers": 10
    }
  }
}
```

#### 3. GET /api/admin/email-invites - Email Invitations (Admin)

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 20 | Number of invites per page |
| `search` | string | - | Search by email address (partial match, case-insensitive) |
| `sortBy` | string | createdAt | Sort field: `id`, `email`, `status`, `createdAt`, `expiresAt`, `invitedByUsername` |
| `sortDirection` | string | desc | Sort direction: `asc` or `desc` |

**Example Requests:**
```bash
# Get all invites with default pagination
curl -X GET 'http://localhost:8080/api/admin/email-invites' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Get invites sorted by email
curl -X GET 'http://localhost:8080/api/admin/email-invites?sortBy=email&sortDirection=asc' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Get second page with 50 invites per page
curl -X GET 'http://localhost:8080/api/admin/email-invites?page=1&size=50' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Search invites by email domain
curl -X GET 'http://localhost:8080/api/admin/email-invites?search=@gmail.com' \
  -H 'Authorization: Bearer ADMIN_TOKEN'

# Search invites by partial email
curl -X GET 'http://localhost:8080/api/admin/email-invites?search=john' \
  -H 'Authorization: Bearer ADMIN_TOKEN'
```

**Response Format:**
```json
{
  "success": true,
  "message": "Invites retrieved",
  "data": {
    "pagination": {
      "content": [...],
      "page": 0,
      "size": 20,
      "totalElements": 50,
      "totalPages": 3,
      "first": true,
      "last": false,
      "sortBy": "createdAt",
      "sortDirection": "desc"
    },
    "summary": {
      "totalInvites": 50,
      "pendingInvites": 30,
      "acceptedInvites": 15,
      "revokedInvites": 5
    }
  }
}
```

### Common Pagination Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `content` | array | List of items for the current page |
| `page` | int | Current page number (0-indexed) |
| `size` | int | Number of items per page |
| `totalElements` | long | Total number of items across all pages |
| `totalPages` | int | Total number of pages |
| `first` | boolean | Whether this is the first page |
| `last` | boolean | Whether this is the last page |
| `sortBy` | string | Field used for sorting |
| `sortDirection` | string | Sort direction (`asc` or `desc`) |

### Swagger/OpenAPI Documentation

**⚠️ Development mode only** (disabled in production):
- `/swagger-ui.html` - Interactive Swagger UI (for API testing)
- `/v3/api-docs` - OpenAPI 3.0 JSON specification

**Using Swagger UI:**
1. Navigate to `http://localhost:8080/swagger-ui.html`
2. Click **Authorize**
3. Enter credentials (e.g., `admin` / `admin123#`)
4. Use **Try it out** to test API endpoints

---

## 👥 Users

### Default Admin

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123#` | ADMIN |

**⚠️ SECURITY:** Change admin password immediately after first login!

### Reset Admin Password

Use the provided script to generate a BCrypt hash and update the database:

```bash
# Install dependency (one-time)
pip3 install bcrypt

# Reset password (interactive)
./scripts/reset-admin-password.sh

# Or pass password directly
./scripts/reset-admin-password.sh "MyNewSecure@Pass123"
```

**Requirements:**
- Docker MySQL container (`mysql_db_miniurl`)
- Python3 with `bcrypt` module

### User Roles

| Role | Permissions |
|------|-------------|
| **ADMIN** | Full access, user management, all features |
| **USER** | Create/manage own URLs, profile, settings |

### User Status

| Status | Description |
|--------|-------------|
| **ACTIVE** | User can log in |
| **SUSPENDED** | Account temporarily disabled |
| **DELETED** | Soft-deleted (can be reactivated) |

---

## 🗃️ Database Schema

### Users Table
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PRIMARY KEY |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | UNIQUE |
| username | VARCHAR(255) | UNIQUE |
| password | VARCHAR(255) | BCrypt hash |
| role_id | BIGINT | FK → roles |
| status | VARCHAR(20) | ACTIVE/SUSPENDED/DELETED |
| created_at | DATETIME | NOT NULL |

### URLs Table
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PRIMARY KEY |
| original_url | VARCHAR(2048) | NOT NULL |
| short_code | VARCHAR(10) | UNIQUE |
| user_id | BIGINT | FK → users |
| access_count | BIGINT | DEFAULT 0 |
| created_at | DATETIME | NOT NULL |

### Other Tables
- **roles** - User roles (ADMIN, USER)
- **verification_tokens** - Email verification and password reset tokens
- **audit_logs** - System audit trail
- **email_invites** - Email invitation tracking
- **feature_flags** - Feature toggle configuration
- **url_usage_limits** - Per-user URL usage tracking

---

## 🏗️ Architecture

### Resilience Patterns (Resilience4j)

**Circuit Breakers:**
- `database` - 60% failure rate, 20s wait
- `emailService` - 50% failure rate, 60s wait
- `urlValidation` - 70% failure rate, 15s wait

**Bulkheads (Thread Pool Isolation):**
| Bulkhead | Max Concurrent | Purpose |
|----------|---------------|---------|
| `urlCreation` | 50 | URL shortening |
| `email` | 20 | Email sending |
| `admin` | 30 | Admin operations |
| `redirect` | 200 | High-throughput redirects |

**Retry with Exponential Backoff:**
- Database: 3 attempts, 500ms base
- Email: 3 attempts, 2s base
- URL validation: 2 attempts, 500ms base

### Rate Limiting (Bucket4j + Caffeine)

Login endpoints use **dual-layer** rate limiting (per-IP + per-username) to protect against brute-force attacks while allowing users behind shared NATs/corporate networks to login normally.

| Endpoint | Per-IP Limit | Per-Username Limit | Purpose |
|----------|-------------|-------------------|---------|
| Login | 100 req / 15 min | 5 req / 5 min | Brute force protection (works for non-existent users) |
| Password Reset | 60 req / 1 hr | — | Email bombing prevention |
| OTP | 30 req / 15 min | — | OTP abuse prevention |
| Signup | 20 req / 1 hr | — | Spam prevention |
| Email Verification | 50 req / 1 hr | — | Token validation abuse prevention |
| URL Creation | 500 req / 1 hr | — | Fair usage |
| General API | 1000 req / 1 hr | — | API abuse prevention |
| Redirects | 5000 req / 1 hr | — | DDoS protection |

**How dual-layer login protection works:**
```
POST /api/auth/login {"username":"xyz","password":"wrong"}
  ├─ Per-IP bucket:    100 requests per 15 minutes  (shared NAT friendly)
  └─ Per-username bucket: 5 requests per 5 minutes  (brute-force protection, works for non-existent users)
```

**Environment variable overrides (production):**
```bash
APP_RATE_LIMIT_LOGIN_REQUESTS=100          # Per-IP login limit
APP_RATE_LIMIT_LOGIN_SECONDS=900
APP_RATE_LIMIT_LOGIN_BY_USERNAME_REQUESTS=5  # Per-username login limit
APP_RATE_LIMIT_LOGIN_BY_USERNAME_SECONDS=300
```

### Security Architecture

```
Request → Rate Limit → CORS Validation → JWT Filter → Circuit Breaker → Bulkhead → Retry → Business Logic
```

**Password Requirements:**
- Minimum 12 characters
- Uppercase, lowercase, number, special character
- No common passwords
- No sequential characters (123, abc)

---

## 📁 Project Structure

```
miniurl/
├── docker-compose.yml           # API container only
├── docker-compose-mysql.yml     # MySQL container (separate stack)
├── docker-compose-nginx.yml     # Nginx reverse proxy (optional)
├── nginx.conf                   # Nginx routing config
├── scripts/
│   ├── init-db.sql              # Database initialization (single source of truth)
│   ├── init-db.sh               # Database setup script
│   └── reset-admin-password.sh  # Reset admin password
├── src/main/java/com/miniurl/
│   ├── config/                  # Security, JWT, CORS, Rate limiting
│   ├── controller/              # REST controllers (API only)
│   ├── service/                 # Business logic services
│   ├── entity/                  # JPA entities
│   ├── repository/              # Spring Data JPA repositories
│   ├── dto/                     # Data Transfer Objects
│   ├── exception/               # Custom exceptions
│   └── util/                    # Utilities (JwtUtil, etc.)
├── src/main/resources/
│   ├── application.yml          # Application configuration
│   ├── logback-spring.xml       # Logging configuration
│   └── templates/
│       └── email/               # Thymeleaf email templates (minimal UI)
│           ├── otp-email.html
│           ├── email-verification.html
│           ├── password-reset.html
│           ├── welcome-email.html
│           ├── welcome-back-email.html
│           ├── account-deletion.html
│           ├── password-reset-confirmation.html
│           ├── password-change-notification.html
│           ├── invite-email.html
│           └── registration-congratulations.html
├── src/test/java/com/miniurl/
│   ├── integration/             # API integration tests
│   ├── service/                 # Service unit tests
│   └── entity/                  # Entity unit tests
├── .github/workflows/
│   ├── ci.yml                   # CI pipeline
│   ├── docker-publish.yml       # Docker build on release tags
│   └── version-bump.yml         # Auto version bump on PR merge
├── .qwen/skill.md               # Qwen Code skill guide
├── pom.xml
├── Dockerfile
└── docker-compose.yml
```

---

## 🔧 Configuration

### Environment Variables

**Required:**
```bash
APP_JWT_SECRET=<min-32-characters>
SPRING_DATASOURCE_URL=jdbc:mysql://host:3306/miniurldb
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=<password>
APP_CORS_ALLOWED_ORIGINS=http://localhost:8080
```

**Optional:**
```bash
APP_NAME=MiniURL
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=<email>
SMTP_PASSWORD=<password>
APP_BASE_URL=http://localhost:8080
```

See `.env.example` for full list and descriptions.

### HikariCP Connection Pool

| Property | Dev | Prod |
|----------|-----|------|
| `minimum-idle` | 5 | 10 |
| `maximum-pool-size` | 20 | 50 |
| `connection-timeout` | 30000ms | 30000ms |
| `leak-detection-threshold` | 60000ms | 30000ms |

---

## 🧪 Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -Pintegration-tests

# With coverage
mvn test jacoco:report

# View coverage
open target/site/jacoco/index.html
```

**Test Coverage:** 80% minimum (enforced by JaCoCo)

---

## 🔄 CI/CD

### GitHub Actions

**Workflows:**
1. **CI Pipeline** (`ci.yml`) - Build, test, health check on PRs
2. **Docker Publish** (`docker-publish.yml`) - Build and push image on push to `main` or release tags
3. **Version Bump** (`version-bump.yml`) - Auto minor version bump on PR merge

### Docker Publishing

- **Push to `main`** — publishes `gallantsuri1/miniurl-api:v1.0.0`
- **Release tag** (e.g., `v1.0.0`) — publishes versioned tags: `v1.0.0`, `1.0`, `sha-<hash>`

**Trigger a release:**
```bash
git tag v1.0.0 && git push origin v1.0.0
```

**Required secrets** (GitHub repo → Settings → Secrets → Actions):
- `DOCKERHUB_USERNAME` — your Docker Hub username
- `DOCKERHUB_TOKEN` — a Docker Hub access token

**Manage images:**
- View: [Docker Hub](https://hub.docker.com/r/gallantsuri1/miniurl-api)
- Use: Set `DOCKER_IMAGE` in `.env` to pick a specific tag

---

## 🛡️ Git Workflow

### Branch Protection

- **main/master**: Protected branches
- **All changes require PR** with code owner review

### Version Bumping

- **Automatic** on PR merge to main/master
- **Minor version bump**: `1.0.0` → `1.1.0`
- Creates draft GitHub release with version tag

---

## 📧 Email Invitations

**Admin-only feature** to invite users via email API:

**Features:**
- Search & filter by status
- Resend revoked/expired invites
- Smart validation (no duplicate active invites)
- Status tracking: PENDING, ACCEPTED, EXPIRED, REVOKED

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|------------|
| **Backend** | Java 17, Spring Boot 3.2 |
| **Security** | Spring Security, JWT, BCrypt |
| **Database** | MySQL 8.0, H2 (testing) |
| **ORM** | Spring Data JPA, Hibernate |
| **Rate Limiting** | Bucket4j, Caffeine |
| **Resilience** | Resilience4j |
| **Testing** | JUnit 5, Mockito |
| **Coverage** | JaCoCo (80% threshold) |
| **CI/CD** | GitHub Actions |
| **Container** | Docker, Docker Compose |

---

## 📝 Logging

**Configuration:** `src/main/resources/logback-spring.xml`

| Property | Default |
|----------|---------|
| **Log Directory** | `./logs` |
| **Max File Size** | 10MB |
| **Retention** | 30 days |
| **Total Size Cap** | 1GB |

**View logs:**
```bash
ls -la logs/
less logs/miniurl.*.log
tail -f logs/miniurl.log
```

---

## 📄 License

MIT License

## 🔌 Frontend Integration Guide

### Email Invitation Signup Flow Implementation

This guide explains how to integrate the email invitation signup flow with your frontend application.

#### 1. Extract Token from URL

When a user clicks the invitation link (`/signup?invite={token}`), extract the token:

```javascript
// React example using react-router-dom
import { useSearchParams } from 'react-router-dom';

function SignupPage() {
  const [searchParams] = useSearchParams();
  const invitationToken = searchParams.get('invite');
  
  // Store token in state for form submission
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    username: '',
    password: '',
    invitationToken: invitationToken || ''
  });
```

#### 2. Display Signup Form

Show the signup form with the token as a hidden field:

```jsx
<form onSubmit={handleSubmit}>
  <input
    type="text"
    name="firstName"
    value={formData.firstName}
    onChange={handleChange}
    placeholder="First Name"
    required
  />
  
  <input
    type="text"
    name="lastName"
    value={formData.lastName}
    onChange={handleChange}
    placeholder="Last Name"
    required
  />
  
  <input
    type="email"
    name="email"
    value={formData.email}
    onChange={handleChange}
    placeholder="Email"
    required
  />
  
  <input
    type="text"
    name="username"
    value={formData.username}
    onChange={handleChange}
    placeholder="Username"
    required
  />
  
  <input
    type="password"
    name="password"
    value={formData.password}
    onChange={handleChange}
    placeholder="Password (min 12 characters)"
    required
    minLength="12"
  />
  
  {/* Hidden invitation token field */}
  {formData.invitationToken && (
    <input type="hidden" name="invitationToken" value={formData.invitationToken} />
  )}
  
  <button type="submit">Sign Up</button>
</form>
```

#### 3. Submit Signup Request

**Important:** Your frontend must be served from a domain included in `APP_CORS_ALLOWED_ORIGINS`.

```javascript
async function handleSignup(formData) {
  try {
    const response = await fetch('http://localhost:8080/api/auth/signup', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        firstName: formData.firstName,
        lastName: formData.lastName,
        username: formData.username,
        password: formData.password,
        invitationToken: formData.invitationToken // Include token
      })
    });
    
    const data = await response.json();
    
    if (response.ok) {
      // Success - show message
      alert('Successfully registered! Please check your email to verify your account.');
    } else {
      // Handle errors
      if (data.message.includes('invitation token')) {
        alert('Invalid or expired invitation. Please contact the administrator.');
      } else {
        alert(data.message);
      }
    }
  } catch (error) {
    console.error('Signup error:', error);
    alert('An error occurred. Please try again.');
  }
}
```

#### 4. Handle Token Validation Errors

Display appropriate error messages based on the response:

```javascript
function displayError(message) {
  const errorMessages = {
    'Invalid invite token': 'This invitation link is invalid. Please check the URL or contact the administrator.',
    'This invite has expired': 'This invitation has expired (valid for 7 days). Please request a new invitation.',
    'This invite has been revoked': 'This invitation has been revoked. Please contact the administrator.',
    'This invite has already been used': 'This invitation has already been used. Please check if you already have an account.',
    'Email already registered': 'This email is already registered. Please login instead.'
  };
  
  const userMessage = Object.keys(errorMessages).find(key => message.includes(key))
    ? errorMessages[Object.keys(errorMessages).find(key => message.includes(key))]
    : message;
  
  // Display userMessage to the user
  setError(userMessage);
}
```

#### 5. Complete Flow Example (React)

```jsx
import React, { useState } from 'react';
import { useSearchParams, Navigate } from 'react-router-dom';

function InvitationSignup() {
  const [searchParams] = useSearchParams();
  const invitationToken = searchParams.get('invite');
  
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    username: '',
    password: '',
    invitationToken: invitationToken || ''
  });
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  
  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };
  
  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    
    try {
      const response = await fetch('http://localhost:8080/api/auth/signup', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          firstName: formData.firstName,
          lastName: formData.lastName,
          username: formData.username,
          password: formData.password,
          invitationToken: formData.invitationToken
        })
      });
      
      const data = await response.json();

      if (response.ok) {
        setSuccess(true);
      } else {
        displayError(data.message);
      }
    } catch (err) {
      if (err.message.includes('Failed to fetch')) {
        setError('CORS error: Ensure your frontend domain is in APP_CORS_ALLOWED_ORIGINS');
      } else {
        setError('An error occurred. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };
  
  const displayError = (message) => {
    const errorMessages = {
      'Invalid invite token': 'This invitation link is invalid.',
      'This invite has expired': 'This invitation has expired (7 days).',
      'This invite has been revoked': 'This invitation has been revoked.',
      'This invite has already been used': 'This invitation was already used.'
    };
    
    const userMessage = Object.keys(errorMessages).find(key => message.includes(key))
      ? errorMessages[Object.keys(errorMessages).find(key => message.includes(key))]
      : message;
    
    setError(userMessage);
  };
  
  if (success) {
    return (
      <div className="success-message">
        <h2>🎉 Successfully Registered!</h2>
        <p>Please check your email to verify your account.</p>
      </div>
    );
  }
  
  return (
    <div className="signup-container">
      <h2>Complete Your Registration</h2>
      {invitationToken && (
        <p className="invite-notice">You're signing up with an invitation!</p>
      )}
      
      {error && <div className="error-message">{error}</div>}
      
      <form onSubmit={handleSubmit}>
        {/* Form fields... */}
        <button type="submit" disabled={loading}>
          {loading ? 'Signing up...' : 'Sign Up'}
        </button>
      </form>
    </div>
  );
}

export default InvitationSignup;
```

#### 6. CSS Styling Suggestions

```css
.invite-notice {
  background-color: #e3f2fd;
  border-left: 4px solid #2196f3;
  padding: 12px 16px;
  margin-bottom: 20px;
  border-radius: 4px;
}

.error-message {
  background-color: #ffebee;
  border-left: 4px solid #f44336;
  padding: 12px 16px;
  margin-bottom: 20px;
  border-radius: 4px;
  color: #c62828;
}

.success-message {
  text-align: center;
  padding: 40px;
  background-color: #e8f5e9;
  border-radius: 8px;
}
```

### Testing the Flow

1. **Login as admin** and navigate to Email Invites
2. **Send an invitation** to a test email
3. **Copy the invite link** from the email (or database)
4. **Open the link** in your frontend application
5. **Fill the signup form** and submit
6. **Verify the invitation** status changes to ACCEPTED in the admin panel

---

## 🚀 Production Deployment

### Prerequisites

- Docker & Docker Compose installed
- Domain name (e.g., `api.example.com`)
- SSL/TLS certificate (via reverse proxy like Nginx)
- SMTP credentials for email service

### Step 1: Clone and Configure

```bash
# Clone repository
git clone https://github.com/gallantsuri1/miniurl.git
cd miniurl

# Copy environment file
cp .env.example .env
```

### Step 2: Configure Environment Variables

Edit `.env` file with your production values:

```bash
# ===========================================
# DATABASE CONFIGURATION
# ===========================================
# Generate secure passwords: openssl rand -base64 32
MYSQL_ROOT_PASSWORD=<generate-secure-password>
MYSQL_DATABASE=miniurldb
MYSQL_USER=miniurl_user
MYSQL_PASSWORD=<generate-secure-password>

# ===========================================
# JWT CONFIGURATION - REQUIRED
# ===========================================
# Generate: openssl rand -base64 64
APP_JWT_SECRET=<generate-secure-jwt-secret>
APP_JWT_EXPIRATION_MS=3600000

# ===========================================
# SMTP CONFIGURATION
# ===========================================
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password

# ===========================================
# APPLICATION CONFIGURATION
# ===========================================
# Your API domain (where backend is hosted)
APP_BASE_URL=https://api.example.com

# CORS - Your frontend domain (where UI is hosted)
# If frontend and backend are on same domain:
APP_CORS_ALLOWED_ORIGINS=https://api.example.com

# If frontend is on different domain:
# APP_CORS_ALLOWED_ORIGINS=https://ui.example.com

# ===========================================
# SERVER CONFIGURATION
# ===========================================
APP_PORT=8080
```

### Step 3: Start Services

```bash
# Start MySQL and MiniURL
docker compose up -d

# Check status
docker compose ps

# Check API service specifically
docker compose ps miniurl-api

# View logs
docker compose logs -f miniurl-api
```

### Step 4: Configure Reverse Proxy (Nginx)

Create `/etc/nginx/sites-available/miniurl-api`:

```nginx
server {
    listen 80;
    server_name api.example.com;

    # Redirect HTTP to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.example.com;

    # SSL Configuration
    ssl_certificate /etc/letsencrypt/live/api.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Security Headers
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # Proxy to MiniURL API
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket support (if needed)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Health check endpoint (optional - for monitoring)
    location /api/health {
        proxy_pass http://localhost:8080/api/health;
        access_log off;
    }
}
```

Enable the site:

```bash
sudo ln -s /etc/nginx/sites-available/miniurl-api /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Step 5: Obtain SSL Certificate

```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d api.example.com

# Auto-renewal is configured automatically
```

### Step 6: Verify Deployment

```bash
# Check health endpoint
curl https://api.example.com/api/health

# Expected response:
# {"success":true,"message":"Service is running","data":null}
```

### Step 7: Initial Setup

1. **Login as admin:**
   - Username: `admin`
   - Password: `admin123#`
   - **⚠️ CHANGE IMMEDIATELY after first login!**

2. **Configure SMTP:**
   - Ensure email service is working
   - Test by sending an admin invite

3. **Verify CORS:**
   - Frontend on `https://api.example.com` should be able to make API calls
   - Check browser console for CORS errors

### Frontend Integration

Your frontend (running on `api.example.com`) should make API calls to the same domain:

```javascript
// Example API call from frontend
const response = await fetch('https://api.example.com/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ username, password }),
});
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/login` | POST | Login |
| `/api/auth/signup` | POST | Register (with invite token) |
| `/api/auth/verify-email-invite` | GET | Validate invite token |
| `/api/auth/verify-email` | GET | Validate reset token |
| `/api/auth/forgot-password` | POST | Request password reset |
| `/api/auth/reset-password` | POST | Reset password |
| `/api/features` | GET | Get user's role features |
| `/api/features/global` | GET | Get global flags (public) |
| `/api/admin/features` | GET | Get all features (ADMIN) |
| `/api/self-invite/send` | POST | Send self-invite |

### Limit MySQL Volume Size

Docker doesn't natively limit volume size. Here are the recommended approaches:

**Option 1: File-backed ext4 filesystem (hard limit)**

```bash
# Create a 100GB sparse file
truncate -s 100G /opt/mysql-data.img

# Format as ext4
mkfs.ext4 /opt/mysql-data.img

# Mount it to the Docker volume directory
mkdir -p /var/lib/docker/volumes/miniurl-api_mysql-data/_data
mount -o loop /opt/mysql-data.img /var/lib/docker/volumes/miniurl-api_mysql-data/_data

# Make persistent (add to /etc/fstab)
echo '/opt/mysql-data.img /var/lib/docker/volumes/miniurl-api_mysql-data/_data ext4 loop 0 0' >> /etc/fstab
```

**Option 2: MySQL-level limits (via `docker-compose-mysql.yml`)**

```yaml
services:
  mysql:
    command: >
      --max-binlog-size=524288000
      --binlog-expire-logs-seconds=604800
      --innodb-data-file-path=ibdata1:12M:autoextend:max:80G
      --innodb-log-file-size=256M
      --innodb-buffer-pool-size=1G
```

This caps: InnoDB data to 80GB, binlog retention to 7 days, buffer pool to 1GB.

### Backup and Restore

Since MySQL runs in its own container, use standard MySQL backup tools:

**Backup:**
```bash
mysqldump -h <mysql-host> -u <user> -p miniurldb > backup-$(date +%Y%m%d).sql
```

**Restore:**
```bash
mysql -h <mysql-host> -u <user> -p miniurldb < backup-20260402.sql
```

### Troubleshooting

**CORS Errors:**
```bash
# Check CORS configuration
docker compose logs miniurl-api | grep -i cors

# Verify APP_CORS_ALLOWED_ORIGINS in .env
grep APP_CORS_ALLOWED_ORIGINS .env
```

**Database Connection Issues:**
```bash
# Check API service status
docker compose ps miniurl-api

# View application logs
docker compose logs miniurl-api

# Verify MySQL connectivity from container
docker compose exec miniurl-api bash -c 'mysqladmin ping -h <mysql-host> -u <user> -p'
```

**Application Won't Start:**
```bash
# Check application logs
docker compose logs miniurl-api

# Common issues:
# - APP_JWT_SECRET not set or too short
# - Database connection details incorrect
# - Database not initialized (run scripts/init-db.sql)
# - Port 8080 already in use
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Create a Pull Request

**Note:** Direct pushes to `main` are blocked. All changes require a PR.

## 📞 Support

For issues or questions, create an issue in the repository.
