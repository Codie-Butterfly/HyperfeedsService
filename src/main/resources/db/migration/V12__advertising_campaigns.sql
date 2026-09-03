CREATE TABLE advertising_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_type VARCHAR(30) NOT NULL CHECK (template_type IN ('DISCOUNT','SPECIAL','CHICKS','NEW_PRODUCT')),
    branch_id UUID REFERENCES branches(id),
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    image_url TEXT,
    cta_label VARCHAR(80),
    cta_route VARCHAR(160),
    starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_advertising_campaigns_active_window ON advertising_campaigns(active, starts_at, ends_at);
CREATE INDEX idx_advertising_campaigns_branch ON advertising_campaigns(branch_id, ends_at DESC);
