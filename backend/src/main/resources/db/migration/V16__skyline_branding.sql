-- Move demo user email addresses to the Skyline CRM namespace.
-- Password hashes and role assignments remain unchanged.
UPDATE users
SET email = REPLACE(email, '@saivandan.local', '@skyline.local')
WHERE email LIKE '%@saivandan.local';
