#!/bin/bash
# Reset admin account lockout for testing

echo "Resetting admin account lockout..."

# Connect to MySQL and reset failed attempts
docker exec miniurl-api-mysql mysql -uroot -pMiniURLRootPass2024! miniurldb -e "
UPDATE users 
SET failed_login_attempts = 0, lockout_time = NULL 
WHERE username = 'admin';
"

echo "Admin account unlocked. You can now test fresh."
echo ""
echo "Run: ./test-lockout.sh admin wrongpass"
