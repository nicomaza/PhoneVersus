CREATE TABLE IF NOT EXISTS home_banner (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    alt_text VARCHAR(500) NOT NULL,
    link_url VARCHAR(2048),
    desktop_stored_filename VARCHAR(100) NOT NULL,
    desktop_public_path VARCHAR(255) NOT NULL,
    desktop_original_filename VARCHAR(512) NOT NULL,
    desktop_mime_type VARCHAR(50) NOT NULL,
    mobile_stored_filename VARCHAR(100),
    mobile_public_path VARCHAR(255),
    mobile_original_filename VARCHAR(512),
    mobile_mime_type VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_home_banner_desktop_filename UNIQUE (desktop_stored_filename),
    CONSTRAINT uk_home_banner_mobile_filename UNIQUE (mobile_stored_filename)
);

CREATE INDEX IF NOT EXISTS idx_home_banner_active_order
    ON home_banner (active, sort_order, id);
