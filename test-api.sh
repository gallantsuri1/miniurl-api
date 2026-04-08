#!/bin/bash

BASE_URL="http://localhost:8080"

echo "========================================"
echo "MyURL API Endpoint Testing"
echo "========================================"
echo ""

# Test 1: Health Check
echo "1. Health Check (GET /api/health)"
curl -s "$BASE_URL/api/health" | jq .
echo ""

# Test 2: Login and get token
echo "2. Login (POST /auth/login)"
# TODO: Replace with your admin credentials
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"<YOUR_ADMIN_USERNAME>","password":"<YOUR_ADMIN_PASSWORD>"}')
echo "$LOGIN_RESPONSE" | jq .
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.token')
echo ""

# Test 3: Create URL
echo "3. Create URL (POST /api/urls)"
CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"url":"https://www.example.com/api-test"}')
echo "$CREATE_RESPONSE" | jq .
URL_ID=$(echo "$CREATE_RESPONSE" | jq -r '.data.id')
SHORT_CODE=$(echo "$CREATE_RESPONSE" | jq -r '.data.shortCode')
echo ""

# Test 4: Get URLs
echo "4. Get User URLs (GET /api/urls)"
curl -s -X GET "$BASE_URL/api/urls" \
  -H "Authorization: Bearer $TOKEN" | jq .
echo ""

# Test 5: Get Usage Stats
echo "5. Get Usage Stats (GET /api/urls/usage-stats)"
curl -s -X GET "$BASE_URL/api/urls/usage-stats" \
  -H "Authorization: Bearer $TOKEN" | jq .
echo ""

# Test 6: Redirect (no auth required)
echo "6. Redirect Test (GET /r/$SHORT_CODE)"
curl -s -o /dev/null -w "HTTP Status: %{http_code}, Redirect Location: %{redirect_url}\n" "$BASE_URL/r/$SHORT_CODE"
echo ""

# Test 7: Get Profile
echo "7. Get Profile (GET /api/profile)"
curl -s -X GET "$BASE_URL/api/profile" \
  -H "Authorization: Bearer $TOKEN" | jq .
echo ""

# Test 8: Delete URL
echo "8. Delete URL (DELETE /api/urls/$URL_ID)"
curl -s -X DELETE "$BASE_URL/api/urls/$URL_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .
echo ""

# Test 9: Admin - Get Users (requires ADMIN role)
echo "9. Admin - Get Users (GET /api/admin/users)"
curl -s -X GET "$BASE_URL/api/admin/users" \
  -H "Authorization: Bearer $TOKEN" | jq .
echo ""

# Test 10: Unauthorized Access
echo "10. Unauthorized Access Test (GET /api/urls without token)"
curl -s -X GET "$BASE_URL/api/urls" | jq .
echo ""

# Test 11: Swagger/OpenAPI
echo "11. Swagger UI Check (GET /swagger-ui.html)"
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" "$BASE_URL/swagger-ui.html"
echo ""

echo "========================================"
echo "All API Endpoint Tests Completed!"
echo "========================================"
