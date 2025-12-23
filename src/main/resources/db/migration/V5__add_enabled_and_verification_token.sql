-- Add enabled and verification_token columns to users table
ALTER TABLE users ADD COLUMN enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN verification_token VARCHAR(255);
