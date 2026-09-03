INSERT INTO roles (code, description) VALUES ('MAIN_MANAGER', 'Company-wide operations manager')
ON CONFLICT (code) DO NOTHING;

-- Promote the acceptance-test account created in V10 to the company-wide manager role.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'MAIN_MANAGER'
WHERE u.phone_number = '+263771234567'
ON CONFLICT DO NOTHING;

DELETE FROM user_roles ur
USING users u, roles r
WHERE ur.user_id=u.id AND ur.role_id=r.id
  AND u.phone_number='+263771234567' AND r.code='BRANCH_MANAGER';

CREATE TABLE stock_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES branches(id),
    product_id UUID NOT NULL REFERENCES products(id),
    requested_by UUID NOT NULL REFERENCES users(id),
    requested_quantity NUMERIC(19,3) NOT NULL CHECK (requested_quantity > 0),
    note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','FULFILLED')),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_requests_status_created ON stock_requests(status, created_at DESC);
CREATE INDEX idx_stock_requests_branch_created ON stock_requests(branch_id, created_at DESC);
