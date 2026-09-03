-- Clients that authenticate to the gateway via the x-api-key header.
-- The raw key is never stored; only its bcrypt hash.
CREATE TABLE clients (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    api_key_hash  VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_clients_name ON clients (name);
