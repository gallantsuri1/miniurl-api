-- ===========================================
-- Migration: 2FA and OTP Resend Cooldown
-- Run once against existing database
-- Safe: uses conditional checks to avoid errors on re-run
-- ===========================================

USE miniurldb;

-- 1. Add last_otp_sent_at column to users table (if not exists)
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'miniurldb'
      AND table_name = 'users'
      AND column_name = 'last_otp_sent_at'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN last_otp_sent_at DATETIME',
    'SELECT ''Column last_otp_sent_at already exists - skipping'''
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1b. Add theme column to users table (if not exists)
SET @theme_col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'miniurldb'
      AND table_name = 'users'
      AND column_name = 'theme'
);

SET @theme_sql = IF(@theme_col_exists = 0,
    'ALTER TABLE users ADD COLUMN theme ENUM(''LIGHT'', ''DARK'', ''OCEAN'', ''FOREST'') DEFAULT ''LIGHT''',
    'SELECT ''Column theme already exists - skipping'''
);

PREPARE theme_stmt FROM @theme_sql;
EXECUTE theme_stmt;
DEALLOCATE PREPARE theme_stmt;

-- 2. Remove unused otp_tokens table (if exists)
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
