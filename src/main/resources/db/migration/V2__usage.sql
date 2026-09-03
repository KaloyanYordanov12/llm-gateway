-- Per-request usage and cost, one row per proxied (non-cached) provider call.
CREATE TABLE usage_records (
    id            BIGSERIAL      PRIMARY KEY,
    client_id     BIGINT         NOT NULL REFERENCES clients (id),
    model         VARCHAR(255)   NOT NULL,
    input_tokens  INTEGER        NOT NULL,
    output_tokens INTEGER        NOT NULL,
    cost          NUMERIC(18, 8) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX ix_usage_records_client ON usage_records (client_id);
