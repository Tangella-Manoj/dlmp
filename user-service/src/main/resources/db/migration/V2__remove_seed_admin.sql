-- V2: Remove the seeded admin account whose password was published in V1 source.
-- A replacement admin can be created securely via the ADMIN_EMAIL / ADMIN_PASSWORD
-- environment variables (see AdminSeeder) or by promoting a registered user.
DELETE FROM refresh_tokens
 WHERE user_id = '00000000-0000-0000-0000-000000000001';

DELETE FROM users
 WHERE id = '00000000-0000-0000-0000-000000000001'
   AND email = 'admin@dlmp.com';
