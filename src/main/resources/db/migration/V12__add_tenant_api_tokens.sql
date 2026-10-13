-- Stable token revocation across password/role changes and tenant suspension.
ALTER TABLE tenant ADD COLUMN auth_epoch BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN auth_epoch BIGINT NOT NULL DEFAULT 0,
    ADD UNIQUE KEY uk_app_user_tenant_id (tenant_id, id);

CREATE TABLE api_refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    account_state VARCHAR(64) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by_hash CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_api_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_api_refresh_token_user FOREIGN KEY (tenant_id, user_id) REFERENCES app_user (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_api_refresh_token_user_active
    ON api_refresh_token (user_id, revoked_at, expires_at);
