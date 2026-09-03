-- Restricted employee account for acceptance testing against the deployed service.
-- The plaintext password is intentionally not stored in source control.
INSERT INTO users (
    id, phone_number, first_name, last_name, password_hash,
    phone_verified, employee, active, preferred_branch_id
) VALUES (
    '10000000-0000-0000-0000-000000000001',
    '+263771234567',
    'Demo',
    'Employee',
    '$2y$12$ZTggAITjQ1ILcP/6cFOdc.n3Ydy8yRu/fkNwe6H/UqnykaEBlAShC',
    TRUE,
    TRUE,
    TRUE,
    '11000000-0000-0000-0000-000000000001'
)
ON CONFLICT (phone_number) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    password_hash = EXCLUDED.password_hash,
    phone_verified = TRUE,
    employee = TRUE,
    active = TRUE,
    preferred_branch_id = EXCLUDED.preferred_branch_id,
    updated_at = now();

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'BRANCH_MANAGER'
WHERE u.phone_number = '+263771234567'
ON CONFLICT DO NOTHING;

INSERT INTO employee_branches (user_id, branch_id)
SELECT u.id, b.id
FROM users u
JOIN branches b ON b.code = 'AMTEC'
WHERE u.phone_number = '+263771234567'
ON CONFLICT DO NOTHING;
