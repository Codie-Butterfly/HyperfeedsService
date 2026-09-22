INSERT INTO roles(code,description) VALUES('CEO','Company performance dashboard and sales targets') ON CONFLICT DO NOTHING;
ALTER TABLE orders ADD COLUMN subtotal NUMERIC(19,2),
 ADD COLUMN discount_type VARCHAR(12) NOT NULL DEFAULT 'NONE' CHECK(discount_type IN ('NONE','PERCENTAGE','FIXED')),
 ADD COLUMN discount_value NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(discount_value>=0),
 ADD COLUMN discount_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK(discount_amount>=0),
 ADD COLUMN discount_reason VARCHAR(500),
 ADD COLUMN discount_recorded_by UUID REFERENCES users(id);
UPDATE orders SET subtotal=total;
ALTER TABLE orders ADD CONSTRAINT valid_discount_total CHECK(subtotal IS NULL OR (discount_amount<=subtotal AND total=subtotal-discount_amount));
CREATE TABLE sales_targets (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 branch_id UUID REFERENCES branches(id),
 scope_key TEXT GENERATED ALWAYS AS (coalesce(branch_id::text,'COMPANY')) STORED,
 target_month DATE NOT NULL CHECK(extract(day from target_month)=1),
 currency CHAR(3) NOT NULL,
 amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
 updated_by UUID NOT NULL REFERENCES users(id),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(target_month,currency,scope_key)
);
