CREATE TABLE chick_batches (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), branch_id UUID NOT NULL REFERENCES branches(id), breed VARCHAR(120) NOT NULL,
 hatch_date DATE NOT NULL, available_quantity INTEGER NOT NULL CHECK(available_quantity>=0), reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK(reserved_quantity>=0),
 price_per_chick NUMERIC(19,2) NOT NULL CHECK(price_per_chick>=0), currency CHAR(3) NOT NULL DEFAULT 'USD', active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), CHECK(reserved_quantity<=available_quantity)
);
CREATE TABLE chick_bookings (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), reference VARCHAR(30) NOT NULL UNIQUE, user_id UUID NOT NULL REFERENCES users(id), batch_id UUID NOT NULL REFERENCES chick_batches(id),
 quantity INTEGER NOT NULL CHECK(quantity>0), status VARCHAR(30) NOT NULL, expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE carts (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id), branch_id UUID NOT NULL REFERENCES branches(id), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE UNIQUE INDEX uq_active_cart_user ON carts(user_id) WHERE status='ACTIVE';
CREATE TABLE cart_items (cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE, product_id UUID NOT NULL REFERENCES products(id), quantity NUMERIC(19,3) NOT NULL CHECK(quantity>0), PRIMARY KEY(cart_id,product_id));
CREATE TABLE orders (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), reference VARCHAR(30) NOT NULL UNIQUE, user_id UUID NOT NULL REFERENCES users(id), branch_id UUID NOT NULL REFERENCES branches(id), status VARCHAR(30) NOT NULL, total NUMERIC(19,2) NOT NULL CHECK(total>=0), currency CHAR(3) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE order_items (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE, product_id UUID NOT NULL REFERENCES products(id), product_name VARCHAR(200) NOT NULL, quantity NUMERIC(19,3) NOT NULL, unit_price NUMERIC(19,2) NOT NULL, line_total NUMERIC(19,2) NOT NULL);
CREATE TABLE payments (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES orders(id), provider VARCHAR(30) NOT NULL, provider_reference VARCHAR(120) UNIQUE, status VARCHAR(30) NOT NULL, redirect_url TEXT, amount NUMERIC(19,2) NOT NULL, currency CHAR(3) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE announcements (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), title VARCHAR(200) NOT NULL, body TEXT NOT NULL, branch_id UUID REFERENCES branches(id), published_from TIMESTAMPTZ NOT NULL, published_until TIMESTAMPTZ, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE specials (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), product_id UUID NOT NULL REFERENCES products(id), branch_id UUID REFERENCES branches(id), promotional_price NUMERIC(19,2) NOT NULL CHECK(promotional_price>=0), currency CHAR(3) NOT NULL, starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, CHECK(ends_at>starts_at));
CREATE TABLE notifications (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, type VARCHAR(50) NOT NULL, title VARCHAR(200) NOT NULL, body TEXT NOT NULL, data JSONB NOT NULL DEFAULT '{}'::jsonb, read_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_notifications_user_created ON notifications(user_id,created_at DESC);
CREATE TABLE livestock_questions (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES users(id), subject VARCHAR(200) NOT NULL, question TEXT NOT NULL, status VARCHAR(30) NOT NULL, ai_draft TEXT, expert_answer TEXT, answered_by UUID REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), answered_at TIMESTAMPTZ);
