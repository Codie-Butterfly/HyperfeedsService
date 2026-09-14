ALTER TABLE chick_bookings
    ADD COLUMN deposit_payment_method VARCHAR(30),
    ADD COLUMN deposit_paid_at TIMESTAMPTZ;

ALTER TABLE payments ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE payments ADD COLUMN chick_booking_id UUID REFERENCES chick_bookings(id);
ALTER TABLE payments ADD CONSTRAINT chk_payment_target CHECK (
    (order_id IS NOT NULL AND chick_booking_id IS NULL) OR
    (order_id IS NULL AND chick_booking_id IS NOT NULL)
);

CREATE INDEX idx_payments_chick_booking ON payments(chick_booking_id);
