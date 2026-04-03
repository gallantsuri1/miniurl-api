#!/bin/bash

# MiniURL - Seed 1000 Random URLs Script
# This script inserts 1000 random short URLs for the admin user

# Configuration
MYSQL_HOST="localhost"
MYSQL_PORT="3306"
MYSQL_USER="miniurluser"
MYSQL_PASSWORD="MiniURLUserPass2024!"
DATABASE="miniurldb"

# Get admin user ID
echo "Getting admin user ID..."
ADMIN_ID=$(mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASSWORD -N -e "SELECT id FROM $DATABASE.users WHERE username='admin' LIMIT 1;")

if [ -z "$ADMIN_ID" ]; then
    echo "ERROR: Admin user not found!"
    exit 1
fi

echo "Found admin user ID: $ADMIN_ID"
echo "Inserting 1000 random URLs..."

# Generate and insert 1000 random URLs
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASSWORD $DATABASE << EOF
INSERT INTO urls (original_url, short_code, user_id, created_at, access_count)
VALUES
EOF

# Generate random URLs
for i in $(seq 1 1000); do
    # Generate random short code (6 chars)
    SHORT_CODE=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 6 | head -n 1)
    
    # Generate random domain and path
    DOMAINS=("example.com" "test.com" "demo.org" "sample.net" "fake.io" "mock.co" "dummy.app" "trial.dev" "temp.site" "random.web")
    DOMAIN=${DOMAINS[$RANDOM % ${#DOMAINS[@]}]}
    PATH_LENGTH=$((RANDOM % 5 + 1))
    RANDOM_PATH=$(cat /dev/urandom | tr -dc 'a-z0-9' | fold -w $((PATH_LENGTH * 8)) | head -n 1 | sed 's/\(........\)/\1\//g' | sed 's/\/$//')
    
    # Create original URL
    ORIGINAL_URL="https://www.$DOMAIN/$RANDOM_PATH"
    
    # Generate random created_at timestamp (within last 30 days)
    DAYS_AGO=$((RANDOM % 30))
    HOURS_AGO=$((RANDOM % 24))
    CREATED_AT=$(date -d "$DAYS_AGO days ago $HOURS_AGO hours ago" '+%Y-%m-%d %H:%M:%S')
    
    # Generate random access count (0-1000)
    ACCESS_COUNT=$((RANDOM % 1001))
    
    # Add comma for all but last entry
    if [ $i -lt 1000 ]; then
        COMMA=","
    else
        COMMA=";"
    fi
    
    echo "('$ORIGINAL_URL', '$SHORT_CODE', $ADMIN_ID, '$CREATED_AT', $ACCESS_COUNT)$COMMA"
done >> /tmp/seed_urls_$$.sql

# Insert into database
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASSWORD $DATABASE < /tmp/seed_urls_$$.sql

# Clean up
rm -f /tmp/seed_urls_$$.sql

echo "✅ Successfully inserted 1000 random URLs for admin user!"
echo ""
echo "Sample URLs:"
mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASSWORD -e "SELECT short_code, original_url, access_count, created_at FROM $DATABASE.urls WHERE user_id=$ADMIN_ID ORDER BY created_at DESC LIMIT 5;"
