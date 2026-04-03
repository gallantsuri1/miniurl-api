-- Feature Flags Initialization Script
-- Adds role-based feature flags to the feature_flags table
-- Run this after the main init-db.sql script

USE miniurldb;

-- ===========================================
-- FEATURE FLAGS TABLE
-- ===========================================
-- Ensure the feature_flags table has the target_role column
ALTER TABLE feature_flags 
ADD COLUMN IF NOT EXISTS target_role VARCHAR(50) DEFAULT 'USER';

-- ===========================================
-- USER ROLE FEATURES
-- ===========================================
-- These features are available to regular USER role users

INSERT INTO feature_flags (feature_key, feature_name, description, enabled, target_role, created_at, updated_at)
VALUES 
    ('USER_SIGNUP', 'User Sign Up', 'Allow new user registration', true, 'USER', NOW(), NOW()),
    ('DASHBOARD', 'Dashboard', 'User dashboard access', true, 'USER', NOW(), NOW()),
    ('PROFILE_PAGE', 'Profile Page', 'User profile management', true, 'USER', NOW(), NOW()),
    ('SETTINGS_PAGE', 'Settings Page', 'Account settings and password change', true, 'USER', NOW(), NOW()),
    ('EXPORT_JSON', 'Export to JSON', 'Export user data as JSON', true, 'USER', NOW(), NOW()),
    ('URL_SHORTENING', 'URL Shortening', 'Create short URLs', true, 'USER', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    target_role = VALUES(target_role),
    updated_at = NOW();

-- ===========================================
-- ADMIN ROLE FEATURES
-- ===========================================
-- These features are available to ADMIN role users only

INSERT INTO feature_flags (feature_key, feature_name, description, enabled, target_role, created_at, updated_at)
VALUES 
    ('EMAIL_INVITE', 'Email Invitations', 'Send email invitations for user signup', true, 'ADMIN', NOW(), NOW()),
    ('USER_MANAGEMENT', 'User Management', 'Admin user management', true, 'ADMIN', NOW(), NOW()),
    ('FEATURE_MANAGEMENT', 'Feature Management', 'Manage application features', true, 'ADMIN', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    target_role = VALUES(target_role),
    updated_at = NOW();

-- ===========================================
-- VERIFICATION
-- ===========================================
-- Display all feature flags with their roles

SELECT 
    feature_key AS 'Feature Key',
    feature_name AS 'Feature Name',
    description AS 'Description',
    enabled AS 'Enabled',
    target_role AS 'Target Role'
FROM feature_flags
ORDER BY target_role, feature_name;

-- ===========================================
-- SUMMARY
-- ===========================================

SELECT 
    target_role AS 'Role',
    COUNT(*) AS 'Feature Count',
    SUM(CASE WHEN enabled = true THEN 1 ELSE 0 END) AS 'Enabled Count'
FROM feature_flags
GROUP BY target_role
ORDER BY target_role;
