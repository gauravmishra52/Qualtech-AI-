-- Create face_users table for storing face authentication data
CREATE TABLE IF NOT EXISTS face_users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    face_embedding TEXT,
    image_url VARCHAR(500),
    s3_key VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    department VARCHAR(100),
    position VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_face_users_email UNIQUE (email)
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_face_users_email ON face_users(email);
CREATE INDEX IF NOT EXISTS idx_face_users_active ON face_users(is_active);
