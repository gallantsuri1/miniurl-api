-- MiniURL - Seed 1000 Random URLs for Admin User
-- Run this script to populate the database with test data
-- Usage: mysql -u root -p miniurldb < seed-urls.sql

-- Get admin user ID and store in variable
SET @admin_id = (SELECT id FROM users WHERE username = 'admin' LIMIT 1);

-- Check if admin exists
SELECT CONCAT('Admin user ID: ', @admin_id) AS 'Status';

-- Delete existing URLs for admin (optional - comment out to keep existing)
-- DELETE FROM urls WHERE user_id = @admin_id;

-- Insert 1000 random URLs using INSERT IGNORE to skip duplicates
INSERT IGNORE INTO urls (original_url, short_code, user_id, created_at, access_count)
SELECT 
    CONCAT(
        'https://www.',
        ELT(FLOOR(1 + RAND() * 10), 
            'example.com', 'test.com', 'demo.org', 'sample.net', 'fake.io',
            'mock.co', 'dummy.app', 'trial.dev', 'temp.site', 'random.web'
        ),
        '/',
        SUBSTRING(MD5(RAND()), 1, 8), '/',
        SUBSTRING(MD5(RAND()), 1, 8), '/',
        SUBSTRING(MD5(RAND()), 1, 8)
    ) AS original_url,
    UPPER(SUBSTRING(MD5(RAND()), 1, 6)) AS short_code,
    @admin_id AS user_id,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY) AS created_at,
    FLOOR(RAND() * 1001) AS access_count
FROM 
    (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) a,
    (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) b,
    (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) c,
    (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) d,
    (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) e,
    (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) f;

-- Show summary
SELECT 
    'URLs seeded successfully!' AS Status,
    COUNT(*) AS 'Total URLs',
    MIN(created_at) AS 'Oldest URL',
    MAX(created_at) AS 'Newest URL',
    SUM(access_count) AS 'Total Clicks'
FROM urls 
WHERE user_id = @admin_id;

-- Show sample URLs
SELECT 
    short_code AS 'Short Code',
    original_url AS 'Original URL',
    access_count AS 'Clicks',
    created_at AS 'Created'
FROM urls 
WHERE user_id = @admin_id 
ORDER BY created_at DESC 
LIMIT 10;
