-- Streaming can record a partial usage row when a stream aborts mid-way. Existing
-- (non-streaming) rows are complete, so the column defaults to TRUE.
ALTER TABLE usage_records
    ADD COLUMN complete BOOLEAN NOT NULL DEFAULT TRUE;
