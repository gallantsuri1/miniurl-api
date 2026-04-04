#!/bin/bash
# Reset admin password for MiniURL
# Usage: ./scripts/reset-admin-password.sh [new-password]

set -e

MYSQL_CONTAINER="mysql_db_miniurl"
MYSQL_DB="miniurldb"
MYSQL_USER="root"
MYSQL_PASS=""

# If password not provided as argument, prompt for it
if [ -n "$1" ]; then
    NEW_PASSWORD="$1"
else
    echo "Enter new admin password:"
    read -s NEW_PASSWORD
    echo
fi

if [ -z "$NEW_PASSWORD" ]; then
    echo "Error: Password cannot be empty"
    exit 1
fi

# Generate BCrypt hash using Python
echo "Generating BCrypt hash..."
BCRYPT_HASH=$(python3 -c "
import bcrypt, sys
password = sys.argv[1].encode('utf-8')
hash_bytes = bcrypt.hashpw(password, bcrypt.gensalt(rounds=10))
print(hash_bytes.decode('utf-8'))
" "$NEW_PASSWORD" 2>/dev/null)

if [ $? -ne 0 ]; then
    echo "Error: Python3 with 'bcrypt' module is required."
    echo "Install with: pip3 install bcrypt"
    exit 1
fi

echo "Updating admin password in database..."

# Execute update via Docker MySQL container
docker exec -i "$MYSQL_CONTAINER" mysql -u "$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" -e "
UPDATE users SET password = '${BCRYPT_HASH}' WHERE username = 'admin';
"

echo "✅ Admin password updated successfully."
