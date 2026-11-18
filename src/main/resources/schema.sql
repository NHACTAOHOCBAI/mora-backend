-- Schema DDL for MORA Backend
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    avatar_url VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spaces (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    avatar_url VARCHAR(255),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS space_members (
    id BIGSERIAL PRIMARY KEY,
    space_id BIGINT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(space_id, user_id)
);

CREATE TABLE IF NOT EXISTS space_invitations (
    id BIGSERIAL PRIMARY KEY,
    space_id BIGINT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    inviter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invite_code VARCHAR(64) NOT NULL UNIQUE,
    invite_email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'EDITOR',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS space_member_invitations (
    id BIGSERIAL PRIMARY KEY,
    space_id BIGINT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    inviter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'EDITOR',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_member_invites_invitee_status ON space_member_invitations (invitee_id, status);

-- Compatibility migration: earlier local schemas required an invitee for every
-- invitation. Shareable invitation links intentionally do not have one.
ALTER TABLE space_invitations ALTER COLUMN invitee_id DROP NOT NULL;

CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    space_id BIGINT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    uploader_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(100),
    total_pages INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_pages (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    space_id BIGINT REFERENCES spaces(id) ON DELETE CASCADE,
    document_id BIGINT REFERENCES documents(id) ON DELETE SET NULL,
    sender_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    message_type VARCHAR(20) NOT NULL DEFAULT 'SPACE',
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    citations TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS study_sets (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    space_id BIGINT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    created_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS study_set_documents (
    id BIGSERIAL PRIMARY KEY,
    study_set_id BIGINT NOT NULL REFERENCES study_sets(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    UNIQUE(study_set_id, document_id)
);

CREATE TABLE IF NOT EXISTS flashcards (
    id BIGSERIAL PRIMARY KEY,
    study_set_id BIGINT NOT NULL REFERENCES study_sets(id) ON DELETE CASCADE,
    front TEXT NOT NULL, back TEXT NOT NULL,
    document_id BIGINT, document_name VARCHAR(255), page_number INT, source_quote TEXT
);

CREATE TABLE IF NOT EXISTS quiz_questions (
    id BIGSERIAL PRIMARY KEY,
    study_set_id BIGINT NOT NULL REFERENCES study_sets(id) ON DELETE CASCADE,
    question TEXT NOT NULL, options_json TEXT NOT NULL, correct_option_index INT NOT NULL,
    explanation TEXT NOT NULL, document_id BIGINT, document_name VARCHAR(255), page_number INT, source_quote TEXT
);

-- Remove obsolete account handle on existing installations (no cascading deletes).
ALTER TABLE users DROP COLUMN IF EXISTS username;

-- Preserve existing sessions until an account actually resets its password.
ALTER TABLE users ADD COLUMN IF NOT EXISTS credential_version BIGINT NOT NULL DEFAULT 0;

-- Legal consent for the published Terms of Use and Privacy Policy.
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_accepted_version VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_accepted_version VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_accepted_at TIMESTAMP;

-- Safe, user-facing failure metadata for the admin operations console.
ALTER TABLE documents ADD COLUMN IF NOT EXISTS failure_code VARCHAR(80);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS failure_message VARCHAR(500);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP;

-- Dashboard "Tiếp tục học": retain a lightweight timestamp for recent activity.
ALTER TABLE spaces ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP;
UPDATE spaces SET last_activity_at = COALESCE(last_activity_at, updated_at, created_at);
CREATE INDEX IF NOT EXISTS idx_spaces_last_activity_at ON spaces (last_activity_at DESC);

-- Invitation links are shareable. Named recipients are managed by direct member addition.
DROP INDEX IF EXISTS idx_space_invitations_recipient_pending;
ALTER TABLE space_invitations DROP COLUMN IF EXISTS invite_email;

-- Debug-only AI prompt data must never persist or be exposed to users.
ALTER TABLE chat_messages DROP COLUMN IF EXISTS condensed_question;
ALTER TABLE chat_messages DROP COLUMN IF EXISTS prompt_sent;
