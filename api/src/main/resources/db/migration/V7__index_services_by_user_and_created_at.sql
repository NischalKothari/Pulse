CREATE INDEX idx_services_user_created_id
ON services (user_id, created_at DESC, id DESC);