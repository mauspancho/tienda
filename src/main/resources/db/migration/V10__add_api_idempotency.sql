CREATE TABLE api_idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(60) NOT NULL,
    operation_name VARCHAR(80) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_api_idempotency UNIQUE (username, operation_name, key_hash)
);

CREATE INDEX idx_api_idempotency_created_at ON api_idempotency_record (created_at);
