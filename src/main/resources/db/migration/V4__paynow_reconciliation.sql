ALTER TABLE payments ADD COLUMN poll_url TEXT;
ALTER TABLE payments ADD COLUMN instructions TEXT;
ALTER TABLE payments ADD COLUMN last_polled_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN failure_reason TEXT;
CREATE INDEX idx_payments_pending_poll ON payments(status, created_at) WHERE status IN ('CREATED','SENT_TO_SUBSCRIBER');
