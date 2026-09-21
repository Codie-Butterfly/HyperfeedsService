CREATE TABLE walk_in_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL CHECK(length(trim(name))>0),
    phone_number VARCHAR(32),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_walk_in_customer_name ON walk_in_customers(lower(name));
CREATE UNIQUE INDEX uq_walk_in_customer_phone ON walk_in_customers(phone_number) WHERE phone_number IS NOT NULL;

ALTER TABLE orders ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE orders
    ADD COLUMN sales_channel VARCHAR(20) NOT NULL DEFAULT 'APP',
    ADD COLUMN customer_name VARCHAR(200),
    ADD COLUMN walk_in_customer_id UUID REFERENCES walk_in_customers(id),
    ADD COLUMN customer_phone VARCHAR(32),
    ADD COLUMN created_by UUID REFERENCES users(id),
    ADD COLUMN request_id UUID UNIQUE,
    ADD COLUMN request_fingerprint VARCHAR(64),
    ADD CONSTRAINT valid_order_customer CHECK (
        (sales_channel='APP' AND user_id IS NOT NULL) OR
        (sales_channel='WALK_IN' AND customer_name IS NOT NULL AND created_by IS NOT NULL)
    );
ALTER TABLE payments
    ADD COLUMN external_method VARCHAR(30),
    ADD COLUMN external_reference VARCHAR(120),
    ADD COLUMN recorded_by UUID REFERENCES users(id);
CREATE INDEX idx_orders_walk_in_customer ON orders(customer_phone) WHERE sales_channel='WALK_IN';
