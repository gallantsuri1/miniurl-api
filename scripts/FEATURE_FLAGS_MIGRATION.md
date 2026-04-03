# Feature Flags Schema Migration

## Overview

Normalized feature flags schema to eliminate data duplication by separating master feature definitions from role-based enabled status.

## Old Schema

```sql
CREATE TABLE feature_flags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feature_key VARCHAR(100) NOT NULL,
    feature_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT false,
    target_role VARCHAR(50) DEFAULT 'USER',
    UNIQUE KEY uk_feature_role (feature_key, target_role)
);
```

**Problem:** Feature metadata (name, description) duplicated for each role (18 rows with repeated data).

## New Schema

### Features Table (Master Data)
```sql
CREATE TABLE features (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feature_key VARCHAR(100) NOT NULL UNIQUE,
    feature_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000)
);
```

### Feature Flags Table (Role-based Status)
```sql
CREATE TABLE feature_flags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feature_id BIGINT NOT NULL,
    target_role VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_feature_flags_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE,
    UNIQUE KEY uk_feature_role (feature_id, target_role)
);
```

**Benefit:** Feature metadata stored once (9 rows in features), role-based status in separate table (18 rows in feature_flags).

## Migration Steps

### 1. Create features table from existing data
```sql
CREATE TABLE features (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feature_key VARCHAR(100) NOT NULL UNIQUE,
    feature_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000)
);

INSERT INTO features (feature_key, feature_name, description)
SELECT DISTINCT feature_key, feature_name, description 
FROM feature_flags;
```

### 2. Update feature_flags to use foreign key
```sql
ALTER TABLE feature_flags 
ADD COLUMN feature_id BIGINT AFTER id;

UPDATE feature_flags ff
JOIN features f ON ff.feature_key = f.feature_key
SET ff.feature_id = f.id;

ALTER TABLE feature_flags 
MODIFY feature_id BIGINT NOT NULL,
ADD CONSTRAINT fk_feature_flags_feature 
FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE,
DROP INDEX uk_feature_role,
ADD UNIQUE KEY uk_feature_role (feature_id, target_role);

ALTER TABLE feature_flags 
DROP COLUMN feature_key,
DROP COLUMN feature_name,
DROP COLUMN description;
```

## 9 Master Features

1. USER_SIGNUP - User Sign Up
2. PROFILE_PAGE - Profile Page
3. EXPORT_JSON - Export to JSON
4. URL_SHORTENING - URL Shortening
5. DASHBOARD - Dashboard
6. SETTINGS_PAGE - Settings Page
7. EMAIL_INVITE - Email Invitations
8. USER_MANAGEMENT - User Management
9. FEATURE_MANAGEMENT - Feature Management

## Entity Relationship

```
Feature (1) ──< FeatureFlag (N)
   │              │
   │              ├─ feature_id (FK)
   │              ├─ target_role (USER/ADMIN)
   │              └─ enabled (boolean)
   │
   ├─ id
   ├─ feature_key
   ├─ feature_name
   └─ description
```

## API Changes

No API changes required. The service layer handles the entity relationship transparently.

## Benefits

1. **No Data Duplication** - Feature metadata stored once
2. **Easier Maintenance** - Update feature name/description in one place
3. **Referential Integrity** - Foreign key ensures valid feature references
4. **Cascade Delete** - Deleting a feature automatically removes all role flags
5. **Better Normalization** - Follows database normalization best practices
