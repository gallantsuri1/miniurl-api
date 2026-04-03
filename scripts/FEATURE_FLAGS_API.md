# Feature Flags API Documentation

## Overview

Feature flags are organized into two categories:
1. **Role-Based Features** - Features tied to specific roles (USER, ADMIN)
2. **Global Features** - Features not tied to any role (e.g., USER_SIGNUP)

## Database Schema

### Normalized Design

**features** table (master data - 9 rows):
- Stores feature metadata (key, name, description)
- Each feature defined once

**feature_flags** table (role-based status - 16 rows):
- Links features to roles via foreign key
- Stores enabled status per role
- 8 features × 2 roles = 16 rows

**global_flags** table (global status - 1 row):
- For features not tied to roles
- Currently: USER_SIGNUP

## API Endpoints

### Public Endpoints (No Auth)

#### GET /api/features/global
Returns all global flags (e.g., USER_SIGNUP status).

**Response:**
```json
{
  "success": true,
  "message": "Global flags retrieved successfully",
  "data": {
    "flags": [
      {
        "id": 1,
        "featureKey": "USER_SIGNUP",
        "featureName": "User Sign Up",
        "enabled": true
      }
    ],
    "count": 1
  }
}
```

### Authenticated User Endpoints

#### GET /api/features
Returns feature flags for the authenticated user's role.

**USER Role Response:**
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

**ADMIN Role Response:**
```json
{
  "success": true,
  "message": "Features for ADMIN role retrieved successfully",
  "data": {
    "features": [...],
    "count": 8,
    "role": "ADMIN"
  }
}
```

### Admin-Only Endpoints

#### GET /api/admin/features
Returns ALL feature flags (both USER and ADMIN roles).

#### GET /api/admin/features/{id}
Get a specific feature flag by ID.

#### PUT /api/admin/features/{id}/toggle
Toggle a feature flag on/off.

**Request:**
```json
{
  "enabled": true
}
```

**Response:**
```json
{
  "success": true,
  "message": "Feature 'DASHBOARD' (role: USER) has been enabled successfully.",
  "data": {...}
}
```

#### GET /api/admin/features/global
Get all global flags (ADMIN view).

#### GET /api/admin/features/global/{id}
Get a specific global flag by ID.

#### PUT /api/admin/features/global/{id}/toggle
Toggle a global flag on/off.

**Request:**
```json
{
  "enabled": false
}
```

## Features

### Role-Based Features (8 features × 2 roles = 16 flags)

1. **PROFILE_PAGE** - User profile management
2. **EXPORT_JSON** - Export user data as JSON
3. **URL_SHORTENING** - Create short URLs
4. **DASHBOARD** - User dashboard access
5. **SETTINGS_PAGE** - Account settings and password change
6. **EMAIL_INVITE** - Email invitations
7. **USER_MANAGEMENT** - User management
8. **FEATURE_MANAGEMENT** - Feature management

### Global Features (1 feature)

1. **USER_SIGNUP** - Allow new user registration

## Security

| Endpoint | Authentication | Authorization |
|----------|---------------|---------------|
| GET /api/features/global | None | Public |
| GET /api/features | Required | Any authenticated user |
| GET /api/admin/features | Required | ADMIN role only |
| PUT /api/admin/features/{id}/toggle | Required | ADMIN role only |
| GET /api/admin/features/global | Required | ADMIN role only |
| PUT /api/admin/features/global/{id}/toggle | Required | ADMIN role only |

## Benefits of Normalized Schema

1. **No Data Duplication** - Feature metadata stored once
2. **Role Extensibility** - Add new roles without schema changes
3. **Referential Integrity** - Foreign keys ensure data consistency
4. **Cascade Deletes** - Deleting a feature removes all related flags
5. **Separation of Concerns** - Global vs role-based features clearly separated
