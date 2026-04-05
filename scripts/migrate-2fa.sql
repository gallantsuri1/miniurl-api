-- ===========================================
-- Migration: 2FA and OTP Resend Cooldown
-- Run once against existing database
-- Safe: uses IF NOT EXISTS / NOT EXISTS checks
-- ===========================================

USE miniurldb;

-- 1. Add last_otp_sent_at column to users table (for 30s resend cooldown)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_otp_sent_at DATETIME;

-- 2. Remove unused otp_tokens table (OTP stored on users table instead)
DROP TABLE IF EXISTS otp_tokens;

-- 3. Add TWO_FACTOR_AUTH feature (skip if already exists)
INSERT INTO features (feature_key, feature_name, description)
SELECT 'TWO_FACTOR_AUTH', 'Two-Factor Authentication', 'Require OTP verification after login'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM features WHERE feature_key = 'TWO_FACTOR_AUTH'
);

-- 4. Add TWO_FACTOR_AUTH global flag (enabled by default, skip if already exists)
INSERT INTO global_flags (feature_id, enabled)
SELECT f.id, true
FROM features f
WHERE f.feature_key = 'TWO_FACTOR_AUTH'
  AND NOT EXISTS (
      SELECT 1 FROM global_flags gf WHERE gf.feature_id = f.id
  );
