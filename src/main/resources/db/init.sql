-- ============================================================
-- GoLive Backend — Production Schema Initialisation
-- ============================================================
-- Run this script ONCE against the production database
-- before the first deployment.
--
-- Safe to run multiple times — all statements use IF NOT EXISTS.
-- ============================================================

-- Enable trigram extension for efficient LIKE search
-- Required for: GET /api/streams?query={term}
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ── STREAMS TABLE ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS streams (
                                       stream_id     UUID          PRIMARY KEY,
                                       room_name     VARCHAR(255)  NOT NULL UNIQUE,
    host_key      VARCHAR(255)  NOT NULL,
    title         VARCHAR(255)  NOT NULL,
    category      VARCHAR(100),
    stream_type   VARCHAR(20),
    status        VARCHAR(20)   NOT NULL
    CHECK (status IN ('CREATED', 'LIVE', 'ENDED')),
    viewer_count  INTEGER       NOT NULL DEFAULT 0,
    likes_count   INTEGER       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    started_at    TIMESTAMPTZ,
    ended_at      TIMESTAMPTZ
    );

-- ── INDEXES ───────────────────────────────────────────────────

-- Status filter — used by every listing query
CREATE INDEX IF NOT EXISTS idx_streams_status
    ON streams (status);

-- Status + start time — used by ordered LIVE listing
CREATE INDEX IF NOT EXISTS idx_streams_status_started
    ON streams (status, started_at DESC);

-- Trigram indexes for case-insensitive LIKE search on title and category
CREATE INDEX IF NOT EXISTS idx_streams_title_trgm
    ON streams USING gin (lower(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_streams_category_trgm
    ON streams USING gin (lower(category) gin_trgm_ops);

-- ============================================================
-- Verification query — run after setup to confirm schema
-- ============================================================
-- SELECT table_name FROM information_schema.tables
-- WHERE table_schema = 'public'
-- ORDER BY table_name;