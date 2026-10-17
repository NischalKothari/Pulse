CREATE TABLE services (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    url TEXT NOT NULL,
    check_interval_seconds INTEGER NOT NULL CHECK (check_interval_seconds > 0),
    timeout_ms INTEGER NOT NULL CHECK (timeout_ms > 0),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_services_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);