ALTER TABLE orders ADD COLUMN invoice_copy_count INTEGER NOT NULL DEFAULT 0 CHECK(invoice_copy_count >= 0);
