# Feature Flags Implementation Guide

## Overview

The Feature Flags system provides granular control over application features with role-based access control and global feature management.

## Architecture

### Normalized Schema

The feature flags system uses a normalized database schema to prevent data duplication:

```
features (9 rows)
├── id (PK)
├── feature_key (UNIQUE)
├── feature_name
└── description

feature_flags (16 rows = 8 features × 2 roles)
├── id (PK)
├── feature_id (FK → features.id, UNIQUE per role)
├── role_id (FK → roles.id)
└── enabled

global_flags (1 row)
├── id (PK)
├── feature_id (FK → features.id, UNIQUE)
└── enabled
```

### Key Design Decisions

1. **Features Table**: Master definitions (9 features total)
2. **Feature Flags Table**: Role-based enabled status (16 flags)
3. **Global Flags Table**: Features not tied to roles (1 flag: USER_SIGNUP)
4. **UNIQUE Constraints**: Prevent duplicate entries in global_flags and feature_flags

## Features List

### Global Features (1)
| Feature Key | Description | Default |
|-------------|-------------|---------|
| USER_SIGNUP | Allow new user registration | Enabled |

### Role-Based Features (8 per role)
| Feature Key | Description | Default |
|-------------|-------------|---------|
| PROFILE_PAGE | User profile management | Enabled |
| EXPORT_JSON | Export user data as JSON | Enabled |
| URL_SHORTENING | Create short URLs | Enabled |
| DASHBOARD | User dashboard access | Enabled |
| SETTINGS_PAGE | Account settings and password change | Enabled |
| EMAIL_INVITE | Email invitations | Enabled |
| USER_MANAGEMENT | User management | Enabled |
| FEATURE_MANAGEMENT | Feature management | Enabled |

## API Endpoints

### Public (No Authentication)

#### GET `/api/features/global`
Returns all global flags.

**Request:**
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
        "featureId": 1,
        "featureKey": "USER_SIGNUP",
        "featureName": "User Sign Up",
        "description": "Allow new user registration",
        "enabled": true,
        "createdAt": "2026-04-02T15:10:04",
        "updatedAt": "2026-04-02T15:21:55"
      }
    ],
    "count": 1
  }
}
```

### Authenticated Users

#### GET `/api/features`
Returns feature flags for the authenticated user's role.

**Request:**
```bash
curl -X GET 'http://localhost:8080/api/features' \
  -H 'Authorization: Bearer <JWT_TOKEN>'
```

**Response (USER role):**
```json
{
  "success": true,
  "message": "Features for USER role retrieved successfully",
  "data": {
    "features": [
      {
        "id": 1,
        "featureId": 2,
        "featureKey": "PROFILE_PAGE",
        "featureName": "Profile Page",
        "enabled": true,
        "roleId": 2,
        "roleName": "USER"
      }
    ],
    "count": 8,
    "role": "USER"
  }
}
```

### ADMIN Only

#### Feature Flags Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/features` | GET | Get ALL feature flags (both roles) |
| `/api/admin/features/{id}` | GET | Get feature flag by ID |
| `/api/admin/features` | POST | Create new feature flag |
| `/api/admin/features/{id}/toggle` | PUT | Toggle feature flag on/off |
| `/api/admin/features/{id}` | DELETE | Delete feature flag |

#### Global Flags Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/features/global` | GET | Get all global flags |
| `/api/admin/features/global/{id}` | GET | Get global flag by ID |
| `/api/admin/features/global` | POST | Create new global flag |
| `/api/admin/features/global/{id}/toggle` | PUT | Toggle global flag on/off |
| `/api/admin/features/global/{id}` | DELETE | Delete global flag |

#### Self-Invitation

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/self-invite/send?email={email}&baseUrl={url}` | POST | Send self-invitation (requires USER_SIGNUP enabled) |

## Example Requests

### Create Feature Flag (ADMIN)
```bash
curl -X POST 'http://localhost:8080/api/admin/features' \
  -H 'Authorization: Bearer ADMIN_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"featureId": 5, "roleId": 2, "enabled": true}'
```

### Toggle Feature Flag (ADMIN)

**Format 1 - Object:**
```bash
curl -X PUT 'http://localhost:8080/api/admin/features/1/toggle' \
  -H 'Authorization: Bearer ADMIN_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"enabled": false}'
```

**Format 2 - Boolean:**
```bash
curl -X PUT 'http://localhost:8080/api/admin/features/1/toggle' \
  -H 'Authorization: Bearer ADMIN_TOKEN' \
  -H 'Content-Type: application/json' \
  -d 'true'
```

### Self-Invite (Authenticated User)
```bash
curl -X POST 'http://localhost:8080/api/self-invite/send?email=user@example.com&baseUrl=http://localhost:8080' \
  -H 'Authorization: Bearer USER_TOKEN'
```

**Response:**
```json
{
  "success": true,
  "message": "Invitation sent to: user@example.com"
}
```

**Error (Feature Disabled):**
```json
{
  "success": false,
  "message": "Self-signup is currently disabled"
}
```

**Error (Email Exists):**
```json
{
  "success": false,
  "message": "Email already registered: user@example.com"
}
```

## Access Control Matrix

| Endpoint | Public | USER | ADMIN |
|----------|--------|------|-------|
| GET `/api/features/global` | ✅ | ✅ | ✅ |
| GET `/api/features` | ❌ | ✅ (USER features) | ✅ (ADMIN features) |
| GET `/api/admin/features` | ❌ | ❌ | ✅ |
| POST `/api/admin/features` | ❌ | ❌ | ✅ |
| PUT `/api/admin/features/{id}/toggle` | ❌ | ❌ | ✅ |
| DELETE `/api/admin/features/{id}` | ❌ | ❌ | ✅ |
| GET/POST/PUT/DELETE `/api/admin/features/global/**` | ❌ | ❌ | ✅ |
| POST `/api/self-invite/send` | ❌ | ✅ | ✅ |

## Database Initialization

### Run Init Script
```bash
# Copy script to container
docker cp scripts/init-db.sql mysql-db:/tmp/init-db.sql

# Execute (safe to run multiple times)
docker exec mysql-db bash -c "mysql -u root -prootpass -e 'CREATE DATABASE IF NOT EXISTS miniurldb; SOURCE /tmp/init-db.sql'"
```

### Idempotency Features

The init script uses several techniques to ensure safe re-execution:

1. **CREATE TABLE IF NOT EXISTS** - Tables only created if missing
2. **INSERT IGNORE** - Prevents duplicate global_flags entries
3. **ON DUPLICATE KEY UPDATE** - Updates existing feature_flags
4. **WHERE NOT EXISTS** - Admin user only created if missing
5. **UNIQUE Constraints** - Database-level duplicate prevention

### Verification Queries

```sql
-- Count all records
SELECT 'Roles' AS table_name, COUNT(*) AS count FROM roles
UNION ALL SELECT 'Users', COUNT(*) FROM users
UNION ALL SELECT 'Features', COUNT(*) FROM features
UNION ALL SELECT 'Feature Flags', COUNT(*) FROM feature_flags
UNION ALL SELECT 'Global Flags', COUNT(*) FROM global_flags;

-- Verify global flags (should be 1)
SELECT gf.id, f.feature_key, gf.enabled 
FROM global_flags gf 
JOIN features f ON gf.feature_id = f.id;

-- Verify feature flags by role (should be 8 per role)
SELECT r.name AS role, COUNT(*) AS count 
FROM feature_flags ff 
JOIN roles r ON ff.role_id = r.id 
GROUP BY r.name;
```

## Security Considerations

1. **ADMIN-Only Management**: All feature flag management endpoints require ADMIN role
2. **Public Global Status**: Anyone can view global flag status (no sensitive data)
3. **Role-Based Features**: Users can only see their role's features
4. **Self-Invite Protection**: Requires authentication AND USER_SIGNUP enabled
5. **Email Validation**: Self-invite checks for existing email registration

## Troubleshooting

### Global Feature Not Found
```sql
-- Check if feature exists
SELECT * FROM features WHERE feature_key = 'USER_SIGNUP';

-- Check global flag status
SELECT gf.*, f.feature_key FROM global_flags gf 
JOIN features f ON gf.feature_id = f.id;
```

### Duplicate Entry Error
```sql
-- Check for duplicates
SELECT feature_id, COUNT(*) as count 
FROM global_flags 
GROUP BY feature_id 
HAVING COUNT(*) > 1;

-- Remove duplicates (keep lowest id)
DELETE gf1 FROM global_flags gf1 
INNER JOIN global_flags gf2 
WHERE gf1.id > gf2.id AND gf1.feature_id = gf2.feature_id;
```

### Feature Flag Missing for Role
```sql
-- Check existing flags
SELECT ff.id, f.feature_key, r.name as role, ff.enabled 
FROM feature_flags ff 
JOIN features f ON ff.feature_id = f.id 
JOIN roles r ON ff.role_id = r.id 
WHERE f.feature_key = 'DASHBOARD';
```

## Performance

### Indexes
- `features.feature_key` (UNIQUE)
- `feature_flags.feature_id` + `role_id` (UNIQUE composite)
- `global_flags.feature_id` (UNIQUE)
- Foreign key indexes on all relationships

### Query Optimization
- JOIN queries fetch feature and role data in single query
- UNIQUE constraints prevent duplicate checks
- Read-only transactions for GET endpoints

## Files Reference

### Java Code
- `FeatureFlag.java` - Entity with Role foreign key
- `FeatureFlagDTO.java` - DTO with roleId and roleName
- `GlobalFlag.java` - Entity for global flags
- `GlobalFlagDTO.java` - DTO for global flags
- `FeatureFlagService.java` - Role-based feature management
- `GlobalFlagService.java` - Global feature management
- `FeatureFlagController.java` - ADMIN endpoints
- `FeatureFlagPublicController.java` - Public endpoints
- `SelfInviteController.java` - Self-invitation endpoint

### Database Scripts
- `scripts/init-db.sql` - Complete initialization (idempotent)
- `scripts/migrate-feature-flags.sql` - Migration for existing databases

### Documentation
- `scripts/FEATURE_FLAGS_COMPLETE_GUIDE.md` - This guide
- `scripts/DATABASE_INIT_GUIDE.md` - Database initialization guide
- `README.md` - Application documentation
