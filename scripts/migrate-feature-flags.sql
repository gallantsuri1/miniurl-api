-- Feature Flags Migration Script
-- For existing MiniURL databases
-- Run this to add role-based feature flags

USE miniurldb;

-- ===========================================
-- Add target_role column if it doesn't exist
-- ===========================================
ALTER TABLE feature_flags 
ADD COLUMN IF NOT EXISTS target_role VARCHAR(50) DEFAULT 'USER';

-- ===========================================
-- Add unique constraint on (feature_key, target_role)
-- ===========================================
ALTER TABLE feature_flags
DROP INDEX IF EXISTS feature_key;

ALTER TABLE feature_flags
ADD UNIQUE KEY uk_feature_role (feature_key, target_role);

-- ===========================================
-- Insert/Update USER Role Features
-- ===========================================
INSERT INTO feature_flags (feature_key, feature_name, description, enabled, target_role, created_at, updated_at)
VALUES 
    ('USER_SIGNUP', 'User Sign Up', 'Allow new user registration', true, 'USER', NOW(), NOW()),
    ('DASHBOARD', 'Dashboard', 'User dashboard access', true, 'USER', NOW(), NOW()),
    ('PROFILE_PAGE', 'Profile Page', 'User profile management', true, 'USER', NOW(), NOW()),
    ('SETTINGS_PAGE', 'Settings Page', 'Account settings and password change', true, 'USER', NOW(), NOW()),
    ('EXPORT_JSON', 'Export to JSON', 'Export user data as JSON', true, 'USER', NOW(), NOW()),
    ('URL_SHORTENING', 'URL Shortening', 'Create short URLs', true, 'USER', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    target_role = 'USER',
    updated_at = NOW();

-- ===========================================
-- Insert/Update ADMIN Role Features
-- ===========================================
INSERT INTO feature_flags (feature_key, feature_name, description, enabled, target_role, created_at, updated_at)
VALUES 
    ('EMAIL_INVITE', 'Email Invitations', 'Send email invitations for user signup', true, 'ADMIN', NOW(), NOW()),
    ('USER_MANAGEMENT', 'User Management', 'Admin user management', true, 'ADMIN', NOW(), NOW()),
    ('FEATURE_MANAGEMENT', 'Feature Management', 'Manage application features', true, 'ADMIN', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    target_role = 'ADMIN',
    updated_at = NOW();

-- ===========================================
-- Verification Query
-- ===========================================
SELECT 
    feature_key AS 'Feature Key',
    feature_name AS 'Feature Name',
    description AS 'Description',
    enabled AS 'Enabled',
    target_role AS 'Target Role'
FROM feature_flags
ORDER BY target_role, feature_name;

-- ===========================================
-- Summary
-- ===========================================
SELECT 
    target_role AS 'Role',
    COUNT(*) AS 'Total Features',
    SUM(CASE WHEN enabled = true THEN 1 ELSE 0 END) AS 'Enabled Features'
FROM feature_flags
GROUP BY target_role
ORDER BY target_role;
