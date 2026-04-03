# Database Initialization Guide

## Overview

All database initialization is now handled through the `init-db.sql` script. This ensures:
- ✅ **Idempotent** - Safe to run multiple times without data loss
- ✅ **Complete** - Creates all tables and inserts default data
- ✅ **No Java Dependencies** - Database is self-contained
- ✅ **Data Preservation** - Existing data is NEVER overwritten

## What Gets Initialized

### Tables Created (9 total)
1. **roles** - User roles (ADMIN, USER)
2. **users** - User accounts
3. **urls** - Shortened URLs
4. **otp_tokens** - OTP verification tokens
5. **verification_tokens** - Email verification tokens
6. **audit_logs** - System audit trail
7. **features** - Master feature definitions (9 features)
8. **feature_flags** - Role-based feature flags (16 flags)
9. **global_flags** - Global feature flags (1 flag)

### Default Data

**Roles:**
- ADMIN (id=1)
- USER (id=2)

**Features (9 total):**
- USER_SIGNUP (global)
- PROFILE_PAGE, EXPORT_JSON, URL_SHORTENING, DASHBOARD, SETTINGS_PAGE (role-based)
- EMAIL_INVITE, USER_MANAGEMENT, FEATURE_MANAGEMENT (role-based)

**Feature Flags:**
- 8 flags for USER role (all enabled)
- 8 flags for ADMIN role (all enabled)

**Global Flags:**
- USER_SIGNUP (enabled)

**Admin User:**
- Username: `admin`
- Email: `admin@example.com`
- Password: `admin123#` (BCrypt hashed)
- Role: ADMIN
- Must change password: Yes

## How to Initialize

### Fresh Installation

```bash
# Copy script to container
docker cp scripts/init-db.sql mysql-db:/tmp/init-db.sql

# Run initialization
docker exec mysql-db bash -c "mysql -u root -prootpass -e 'CREATE DATABASE IF NOT EXISTS miniurldb; SOURCE /tmp/init-db.sql'"
```

### Existing Database (Safe Update)

```bash
# Copy script to container
docker cp scripts/init-db.sql mysql-db:/tmp/init-db.sql

# Run initialization (safe - won't overwrite existing data)
docker exec mysql-db bash -c "mysql -u root -prootpass miniurldb -e 'SOURCE /tmp/init-db.sql'"
```

### Using Docker Compose

The database is automatically initialized when you run:

```bash
docker compose up -d
```

The `init-db.sql` script is mounted as a volume and runs automatically on first startup.

## Idempotency Features

### Table Creation
```sql
CREATE TABLE IF NOT EXISTS ...
```
Tables are only created if they don't exist.

### Role Insertion
```sql
INSERT INTO roles (id, name, description) VALUES ...
ON DUPLICATE KEY UPDATE name=name;
```
Roles are inserted with id=1 and id=2. If they exist, they're not modified.

### Feature Insertion
```sql
INSERT INTO features (feature_key, feature_name, description) VALUES ...
ON DUPLICATE KEY UPDATE feature_name = VALUES(feature_name);
```
Features are inserted or updated if they exist.

### Admin User Creation
```sql
INSERT INTO users (...) SELECT ... FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
```
Admin user is ONLY created if no user with username='admin' exists. This preserves any password changes you've made.

### Feature Flags
```sql
INSERT INTO feature_flags (feature_id, role_id, enabled) SELECT ...
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled);
```
Feature flags are inserted or updated if they exist.

## Verification Queries

After initialization, verify the data:

```sql
-- Count records in each table
SELECT 'Roles' AS table_name, COUNT(*) AS count FROM roles
UNION ALL SELECT 'Users', COUNT(*) FROM users
UNION ALL SELECT 'Features', COUNT(*) FROM features
UNION ALL SELECT 'Feature Flags', COUNT(*) FROM feature_flags
UNION ALL SELECT 'Global Flags', COUNT(*) FROM global_flags;

-- Verify admin user
SELECT username, email, role_id, must_change_password 
FROM users WHERE username='admin';

-- Verify feature flags by role
SELECT r.name AS role, COUNT(*) AS count 
FROM feature_flags ff 
JOIN roles r ON ff.role_id = r.id 
GROUP BY r.name;
```

## Expected Output

```
table_name      count
Roles           2
Users           1
Features        9
Feature Flags   16
Global Flags    1

username        email               role_id  must_change_password
admin           admin@example.com   1        1

role    count
ADMIN   8
USER    8
```

## Troubleshooting

### Tables Don't Exist
Make sure you're running the script on the correct database:
```sql
USE miniurldb;
SOURCE /tmp/init-db.sql;
```

### Admin User Not Created
Check if a user with username='admin' already exists:
```sql
SELECT * FROM users WHERE username='admin';
```

### Feature Flags Missing
Re-run the init script - it's safe and won't duplicate data:
```bash
docker exec mysql-db bash -c "mysql -u root -prootpass miniurldb -e 'SOURCE /tmp/init-db.sql'"
```

## Migration from Old Schema

If you have an existing database with the old schema:

1. **Backup your database first!**
   ```bash
   docker exec mysql-db mysqldump -u root -prootpass miniurldb > backup.sql
   ```

2. **Run the new init script**
   ```bash
   docker cp scripts/init-db.sql mysql-db:/tmp/init-db.sql
   docker exec mysql-db bash -c "mysql -u root -prootpass miniurldb -e 'SOURCE /tmp/init-db.sql'"
   ```

3. **Verify data**
   Run the verification queries above to ensure all tables and data exist.

## Security Notes

- The default admin password is `admin123#`
- **Change this immediately after first login!**
- The script will NOT overwrite an existing admin user's password
- All passwords are stored as BCrypt hashes
