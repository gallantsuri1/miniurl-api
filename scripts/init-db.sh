#!/bin/bash
# MiniURL API Database Initialization Script
# This script initializes the MySQL database for MiniURL

# Database configuration
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-rootpass}"
DB_NAME="miniurldb"

echo "==================================="
echo "MiniURL API Database Initialization"
echo "==================================="
echo ""
echo "Database Configuration:"
echo "  Host: $DB_HOST"
echo "  Port: $DB_PORT"
echo "  User: $DB_USER"
echo "  Database: $DB_NAME"
echo ""

# Check if MySQL is running
echo "Checking MySQL connection..."
if ! mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -e "SELECT 1" 2>&1 | grep -q "1"; then
    echo "Error: Cannot connect to MySQL server"
    echo "Please ensure MySQL is running on $DB_HOST:$DB_PORT"
    echo ""
    echo "For Docker setup, use:"
    echo "  docker compose up -d mysql"
    echo "  docker compose exec mysql mysql -u root -p"
    exit 1
fi

echo "✓ MySQL connection successful!"
echo ""

# Run the initialization script
echo "Creating database and tables..."
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" < "$(dirname "$0")/init-db.sql"

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Database initialization completed successfully!"
    echo ""
    echo "Database: $DB_NAME"
    echo "Tables created:"
    echo "  - roles"
    echo "  - users"
    echo "  - urls"
    echo "  - otp_tokens"
    echo "  - verification_tokens"
    echo "  - audit_logs"
    echo ""
    echo "Default admin user:"
    echo "  Username: admin"
    echo "  Password: admin123#"
    echo ""
else
    echo ""
    echo "Error: Database initialization failed"
    exit 1
fi

echo "==================================="
