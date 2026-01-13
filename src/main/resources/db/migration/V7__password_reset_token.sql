CREATE TABLE password_reset_token (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id)
);
