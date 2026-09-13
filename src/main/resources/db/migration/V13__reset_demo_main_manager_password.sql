-- Keep the deployed acceptance-test main manager accessible with the documented
-- temporary credential used for interface testing.
UPDATE users
SET password_hash = '$2a$12$LMvPkUWzvyDldfdQQHKH2ekOni9UO33IecQ7.7k4l4fKgNJyvPLn2',
    active = TRUE,
    phone_verified = TRUE,
    employee = TRUE,
    updated_at = now()
WHERE phone_number = '+263771234567';
