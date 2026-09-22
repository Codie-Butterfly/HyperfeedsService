ALTER TABLE chick_bookings ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE chick_bookings
 ADD COLUMN sales_channel VARCHAR(20) NOT NULL DEFAULT 'APP',
 ADD COLUMN walk_in_customer_id UUID REFERENCES walk_in_customers(id),
 ADD COLUMN customer_name VARCHAR(200),
 ADD COLUMN customer_phone VARCHAR(32),
 ADD COLUMN created_by UUID REFERENCES users(id),
 ADD COLUMN request_id UUID UNIQUE,
 ADD COLUMN request_fingerprint TEXT,
 ADD COLUMN invoice_copy_count INTEGER NOT NULL DEFAULT 0 CHECK(invoice_copy_count >= 0),
 ADD CONSTRAINT valid_chick_customer CHECK (
   (sales_channel='APP' AND user_id IS NOT NULL) OR
   (sales_channel='WALK_IN' AND user_id IS NULL AND walk_in_customer_id IS NOT NULL
    AND customer_name IS NOT NULL AND created_by IS NOT NULL));
