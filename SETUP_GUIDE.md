# MiniURL API - Setup and Deployment Guide

## Table of Contents
1. [Local Development Setup](#local-development-setup)
2. [Docker Deployment (Home Server)](#docker-deployment-home-server)
3. [Production Deployment](#production-deployment)
4. [Troubleshooting](#troubleshooting)

---

## Local Development Setup

### Prerequisites

- **Java 17** or higher
- **Maven 3.8+**
- **MySQL 8.0+** (running locally or in Docker)

### Step 1: Install Prerequisites

#### macOS
```bash
# Install Java 17
brew install openjdk@17

# Install Maven
brew install maven

# Install MySQL (optional - can use Docker)
brew install mysql@8.0
```

#### Linux (Ubuntu/Debian)
```bash
# Install Java 17
sudo apt update
sudo apt install openjdk-17-jdk maven -y

# Install MySQL (optional)
sudo apt install mysql-server -y
```

#### Windows
```bash
# Download and install from:
# Java: https://adoptium.net/temurin/releases/?version=17
# Maven: https://maven.apache.org/download.cgi
# MySQL: https://dev.mysql.com/downloads/mysql/
```

### Step 2: Clone Repository

```bash
git clone https://github.com/gallantsuri1/miniurl.git
cd miniurl
```

### Step 3: Configure Environment

#### Option A: Using .env file (Recommended)

```bash
# Copy example environment file
cp .env.example .env

# Edit .env file with your settings
nano .env
```

**Required settings in `.env`:**
```bash
# JWT Secret (REQUIRED - generate a new one)
APP_JWT_SECRET=YourSecureSecretKeyGeneratedWithOpensslRandBase6464

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/miniurldb?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_mysql_password

# Application Base URL
APP_BASE_URL=http://localhost:8080
```

#### Option B: Using MySQL in Docker

```bash
# Start MySQL container
docker run -d \
  --name miniurl-mysql \
  -e MYSQL_ROOT_PASSWORD=rootpass \
  -e MYSQL_DATABASE=miniurldb \
  -p 3306:3306 \
  mysql:8.0

# Wait for MySQL to be ready (30 seconds)
sleep 30
```

### Step 4: Initialize Database

```bash
# Run database initialization script
mysql -u root -p < scripts/init-db.sql
```

### Step 5: Build Application

```bash
# Clean and package (skip tests for faster build)
mvn clean package -DskipTests

# Or build with tests
mvn clean package
```

### Step 6: Run Application

#### Option A: Run JAR directly

```bash
# Set environment variables and run
export APP_JWT_SECRET="YourSecureSecretKeyGeneratedWithOpensslRandBase6464"
java -jar target/miniurl-api-1.0.0.jar --spring.profiles.active=dev
```

#### Option B: Run with Maven

```bash
# Run with development profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--app.jwt.secret=YourSecureSecretKey"
```

#### Option C: Run with Docker Compose (All-in-one)

```bash
# Start both MySQL and application
docker compose up -d

# View logs
docker compose logs -f app

# Stop services
docker compose down
```

### Step 7: Access Application

- **Web Interface**: http://localhost:8080
- **Swagger API Docs**: http://localhost:8080/swagger-ui.html (dev profile only)
- **Health Check**: http://localhost:8080/api/health

### Default Credentials

```
Username: admin
Password: admin123#
```

**⚠️ IMPORTANT:** Change the admin password immediately after first login!

---

## Docker Deployment (Home Server)

### Prerequisites

- **Docker** and **Docker Compose** installed
- **Port 8080** available (or configure custom port)
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

**Required `.env` settings for home server:**

```bash
# ===========================================
# DATABASE CONFIGURATION
# ===========================================
MYSQL_ROOT_PASSWORD=YourSecureMySQLRootPassword123!
MYSQL_DATABASE=miniurldb
MYSQL_USER=miniurluser
MYSQL_PASSWORD=YourSecureMySQLUserPassword456!

# ===========================================
# JWT CONFIGURATION (REQUIRED)
# ===========================================
APP_JWT_SECRET=YourSecureSecretKeyGeneratedWithOpensslRandBase6464

# ===========================================
# APPLICATION CONFIGURATION
# ===========================================
# Use your home server's IP or hostname
APP_BASE_URL=http://your-home-server-ip:8080
# Example: APP_BASE_URL=http://192.168.1.100:8080
# Or: APP_BASE_URL=http://homenas.local:8080

# Server port
SERVER_PORT=8080
APP_PORT=8080

# ===========================================
# SMTP CONFIGURATION (Optional - for email verification)
# ===========================================
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
```

### Step 4: Start Services

```bash
# Start all services (MySQL + Application)
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
docker compose logs app

# Check MySQL logs
docker compose logs mysql
```

### Step 6: Access Application

From any device on your network:
```
http://your-home-server-ip:8080
```

Examples:
- `http://192.168.1.100:8080`
- `http://homenas.local:8080`

### Managing Docker Services

```bash
# View logs
docker compose logs -f app
docker compose logs -f mysql

# Restart services
docker compose restart app
docker compose restart mysql

# Stop services
docker compose down

# Stop and remove volumes (WARNING: deletes data!)
docker compose down -v

# Update application
git pull
docker compose down
docker compose build --no-cache app
docker compose up -d
```

### Backup Database

```bash
# Create backup
docker exec miniurl-mysql mysqldump -u root -pMiniURLRootPass2024! miniurldb > backup-$(date +%Y%m%d).sql

# Restore backup
docker exec -i miniurl-mysql mysql -u root -pMiniURLRootPass2024! miniurldb < backup-20260329.sql
```

---

## Production Deployment

### Additional Security Measures

1. **Enable HTTPS** with reverse proxy (Nginx/Traefik)
2. **Use production profile** (`prod`)
3. **Disable Swagger/OpenAPI**
4. **Configure firewall rules**
5. **Set up monitoring**

### Docker Compose for Production

```yaml
# docker-compose.prod.yml
version: '3.8'

services:
  app:
    build: .
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - APP_JWT_SECRET=${APP_JWT_SECRET}
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/miniurldb
      - SPRING_DATASOURCE_USERNAME=${MYSQL_USER}
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_PASSWORD}
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    restart: unless-stopped

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=miniurldb
      - MYSQL_USER=${MYSQL_USER}
      - MYSQL_PASSWORD=${MYSQL_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
    restart: unless-stopped

volumes:
  mysql-data:
```

### Deploy with Production Profile

```bash
# Start with production configuration
docker compose -f docker-compose.prod.yml up -d

# Verify production mode (Swagger should be disabled)
curl http://localhost:8080/swagger-ui.html
# Should return 404 or redirect to login
```

---

## Troubleshooting

### Application Won't Start

#### Error: JWT secret must be changed from default

**Solution:**
```bash
# Generate new JWT secret
export APP_JWT_SECRET=$(openssl rand -base64 64)

# Restart application
docker compose restart app
```

#### Error: Cannot connect to MySQL

**Solution:**
```bash
# Check MySQL is running
docker compose ps mysql

# View MySQL logs
docker compose logs mysql

# Wait for MySQL to be ready (takes ~30 seconds)
sleep 30

# Restart application
docker compose restart app
```

#### Error: Port 8080 already in use

**Solution:**
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or change port in .env
SERVER_PORT=8081
APP_PORT=8081
```

### Database Issues

#### Error: Database doesn't exist

**Solution:**
```bash
# Initialize database
docker exec -i miniurl-mysql mysql -u root -pYourRootPassword < scripts/init-db.sql
```

#### Error: Access denied for user

**Solution:**
```bash
# Check credentials in .env match docker-compose.yml
# Reset MySQL user
docker exec -it miniurl-mysql mysql -u root -pYourRootPassword

# In MySQL prompt:
CREATE USER 'miniurluser'@'%' IDENTIFIED BY 'YourPassword';
GRANT ALL PRIVILEGES ON miniurldb.* TO 'miniurluser'@'%';
FLUSH PRIVILEGES;
```

### Login Issues

#### Can't login with admin/admin123#

**Solution:**
```bash
# Check if admin user exists
docker exec -it miniurl-mysql mysql -u root -pYourRootPassword miniurldb

# In MySQL:
SELECT username, email FROM users WHERE username='admin';

# If no admin user, check application logs
docker compose logs app | grep "ADMIN USER"

# Reset admin password (last resort)
docker exec -it miniurl-mysql mysql -u root -pYourRootPassword miniurldb

# In MySQL (password is BCrypt hash for 'admin123#'):
UPDATE users SET password='$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' WHERE username='admin';
```

### Docker Issues

#### Container keeps restarting

**Solution:**
```bash
# Check logs
docker compose logs app

# Check resource usage
docker stats

# Increase memory limit in docker-compose.yml
# Add under app service:
#   deploy:
#     resources:
#       limits:
#         memory: 512M
```

#### Volume permission issues

**Solution:**
```bash
# Fix MySQL volume permissions
docker volume ls
docker volume rm miniurl_mysql-data
docker compose up -d
```

### Performance Issues

#### Slow response times

**Solution:**
```bash
# Check resource usage
docker stats

# Increase JVM heap size
# Edit docker-compose.yml, add to app service:
#   environment:
#     - JAVA_OPTS=-Xms512m -Xmx1g

# Restart
docker compose restart app
```

#### High memory usage

**Solution:**
```bash
# Limit container memory in docker-compose.yml
# Add under app service:
#   deploy:
#     resources:
#       limits:
#         memory: 512M
#       reservations:
#         memory: 256M
```

---

## Quick Reference

### Common Commands

```bash
# Local Development
mvn clean package -DskipTests
java -jar target/miniurl-api-1.0.0.jar --spring.profiles.active=dev

# Docker Management
docker compose up -d           # Start services
docker compose down            # Stop services
docker compose logs -f         # View logs
docker compose ps              # Check status
docker compose restart app     # Restart app

# Database
docker exec -it miniurl-mysql mysql -u root -p  # Access MySQL
mysqldump -u root -p miniurldb > backup.sql     # Backup
mysql -u root -p miniurldb < backup.sql         # Restore

# Logs
docker compose logs app
docker compose logs mysql
tail -f /tmp/miniurl.log
```

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `APP_JWT_SECRET` | JWT signing secret | None | ✅ Yes |
| `APP_BASE_URL` | Application base URL | http://localhost:8080 | ✅ Yes |
| `MYSQL_ROOT_PASSWORD` | MySQL root password | MiniURLRootPass2024! | ✅ Yes |
| `MYSQL_PASSWORD` | MySQL user password | MiniURLUserPass2024! | ✅ Yes |
| `SMTP_HOST` | SMTP server host | smtp.gmail.com | ❌ No |
| `SMTP_PORT` | SMTP server port | 587 | ❌ No |
| `SERVER_PORT` | Server port | 8080 | ❌ No |

---

## Support

For issues or questions:
1. Check logs: `docker compose logs app`
2. Review this guide
3. Check application health: `curl http://localhost:8080/api/health`
