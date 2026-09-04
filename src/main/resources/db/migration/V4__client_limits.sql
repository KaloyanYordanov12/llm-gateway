-- Per-client multi-tenant controls. Both columns are nullable: NULL means
-- "use the global default" for the rate limit and "no cap" for the budget, so
-- existing clients keep their exact prior behaviour. Additive, forward-only.
ALTER TABLE clients
    ADD COLUMN rate_limit INTEGER,
    ADD COLUMN budget     NUMERIC(18, 8);
