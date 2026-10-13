CREATE TABLE api_idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    username VARCHAR(60) NOT NULL,
    operation_name VARCHAR(80) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_api_idempotency UNIQUE (tenant_id, user_id, operation_name, key_hash),
    CONSTRAINT fk_api_idempotency_user FOREIGN KEY (tenant_id, user_id) REFERENCES app_user (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_api_idempotency_created_at ON api_idempotency_record (created_at);
