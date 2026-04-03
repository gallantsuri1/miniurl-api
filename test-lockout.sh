#!/bin/bash
# Test script for account lockout functionality
# Usage: ./test-lockout.sh [username] [password]
# Note: Does NOT follow redirects to properly test lockout

USERNAME=${1:-admin}
PASSWORD=${2:-wrongpass}
LOGIN_URL="http://localhost:8080/login"

echo "========================================"
echo "Testing Account Lockout"
echo "Username: $USERNAME"
echo "Password: $PASSWORD"
echo "========================================"
echo ""

# Clear any existing cookies
rm -f test_cookies.txt

for i in {1..7}; do
    echo "=== Attempt $i ==="
    
    # Make login request WITHOUT following redirects (-L would cause issues)
    HTTP_CODE=$(curl -s -c test_cookies.txt -b test_cookies.txt \
        -X POST "$LOGIN_URL" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=$USERNAME&password=$PASSWORD" \
        -w "%{http_code}" \
        -o /dev/null \
        -D headers.txt)
    
    # Extract redirect location from headers
    LOCATION=$(grep -i "location:" headers.txt 2>/dev/null | head -1 | cut -d' ' -f2 | tr -d '\r\n')
    
    # Check response
    if [[ "$LOCATION" == *"error=locked"* ]]; then
        echo "🔒 ACCOUNT LOCKED"
        echo "   Message: Your account is locked due to too many failed login attempts."
        echo "   Please try again after 5 minutes."
    elif [[ "$LOCATION" == *"error=true"* ]]; then
        echo "❌ Invalid username or password (Attempt $i/5)"
    elif [[ "$LOCATION" == *"dashboard"* ]]; then
        echo "✅ Login successful!"
        break
    else
        echo "? Response: HTTP $HTTP_CODE, Location: ${LOCATION:-none}"
    fi
    
    echo ""
    sleep 0.3
done

echo "========================================"
echo "Test Complete"
echo "========================================"

# Cleanup
rm -f test_cookies.txt headers.txt
