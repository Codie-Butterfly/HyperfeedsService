ALTER TABLE users ADD COLUMN email VARCHAR(320);
CREATE UNIQUE INDEX uq_users_email ON users(lower(email)) WHERE email IS NOT NULL;
