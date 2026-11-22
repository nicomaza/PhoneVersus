-- Ejecutar manualmente en PostgreSQL antes de desplegar la sincronizacion de fichas de Tienda Porte.
-- No elimina ni transforma contenido existente.

ALTER TABLE seo_product_content
    ADD COLUMN IF NOT EXISTS porte_reference_id BIGINT,
    ADD COLUMN IF NOT EXISTS porte_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS porte_last_synced_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS porte_sync_status VARCHAR(60),
    ADD COLUMN IF NOT EXISTS porte_sync_error VARCHAR(1000);

ALTER TABLE seo_product_highlight
    ADD COLUMN IF NOT EXISTS source_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS source_text VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS manually_edited BOOLEAN;

UPDATE seo_product_highlight SET manually_edited = FALSE WHERE manually_edited IS NULL;
ALTER TABLE seo_product_highlight ALTER COLUMN manually_edited SET DEFAULT FALSE;
ALTER TABLE seo_product_highlight ALTER COLUMN manually_edited SET NOT NULL;

ALTER TABLE seo_product_spec_section
    ADD COLUMN IF NOT EXISTS source_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS source_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS manually_edited BOOLEAN;

UPDATE seo_product_spec_section SET manually_edited = FALSE WHERE manually_edited IS NULL;
ALTER TABLE seo_product_spec_section ALTER COLUMN manually_edited SET DEFAULT FALSE;
ALTER TABLE seo_product_spec_section ALTER COLUMN manually_edited SET NOT NULL;

ALTER TABLE seo_product_spec
    ADD COLUMN IF NOT EXISTS source_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS source_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_value VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS manually_edited BOOLEAN;

UPDATE seo_product_spec SET manually_edited = FALSE WHERE manually_edited IS NULL;
ALTER TABLE seo_product_spec ALTER COLUMN manually_edited SET DEFAULT FALSE;
ALTER TABLE seo_product_spec ALTER COLUMN manually_edited SET NOT NULL;

ALTER TABLE seo_product_image
    ADD COLUMN IF NOT EXISTS source_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS source_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS imported_from_porte BOOLEAN;

UPDATE seo_product_image SET imported_from_porte = FALSE WHERE imported_from_porte IS NULL;
ALTER TABLE seo_product_image ALTER COLUMN imported_from_porte SET DEFAULT FALSE;
ALTER TABLE seo_product_image ALTER COLUMN imported_from_porte SET NOT NULL;

CREATE TABLE IF NOT EXISTS seo_product_import_exclusion (
    id BIGSERIAL PRIMARY KEY,
    content_id BIGINT NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    source_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seo_product_import_exclusion_content
        FOREIGN KEY (content_id) REFERENCES seo_product_content(id) ON DELETE CASCADE,
    CONSTRAINT uk_seo_product_import_exclusion
        UNIQUE (content_id, item_type, source_key)
);

CREATE INDEX IF NOT EXISTS idx_seo_product_content_porte_reference
    ON seo_product_content (porte_reference_id);
CREATE INDEX IF NOT EXISTS idx_seo_product_content_porte_status
    ON seo_product_content (porte_sync_status, porte_last_synced_at);
CREATE INDEX IF NOT EXISTS idx_seo_product_highlight_source
    ON seo_product_highlight (content_id, source_key);
CREATE INDEX IF NOT EXISTS idx_seo_product_spec_section_source
    ON seo_product_spec_section (content_id, source_key);
CREATE INDEX IF NOT EXISTS idx_seo_product_spec_source
    ON seo_product_spec (section_id, source_key);
CREATE INDEX IF NOT EXISTS idx_seo_product_image_source
    ON seo_product_image (content_id, source_key);
CREATE INDEX IF NOT EXISTS idx_seo_product_import_exclusion_content
    ON seo_product_import_exclusion (content_id);
