-- =============================================================
-- Polaris Character Domain - Add idempotency_key to character_care_logs
-- =============================================================

ALTER TABLE character_care_logs
ADD COLUMN idempotency_key VARCHAR(100) NOT NULL UNIQUE;
