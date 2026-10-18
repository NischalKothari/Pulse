
CREATE TABLE idempotency_records (
    user_id UUID NOT NULL,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,

    request_hash CHAR(64) NOT NULL,

    response_status INTEGER,
    response_body TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_idempotency_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT uq_idempotency_scope
        UNIQUE (user_id, operation, idempotency_key),

    CONSTRAINT chk_idempotency_response
        CHECK (
            (response_status IS NULL AND response_body IS NULL)
            OR
            (response_status IS NOT NULL AND response_body IS NOT NULL)
        )
);