-- Schema DDL for MORA Backend
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
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
    invitee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS user_ai_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    gemini_api_key VARCHAR(255),
    selected_model VARCHAR(100),
    parser_model VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_ai_daily_usages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    request_count INT DEFAULT 0,
    token_count INT DEFAULT 0,
    UNIQUE(user_id, usage_date)
);

CREATE TABLE IF NOT EXISTS benchmark_questions (
    id BIGSERIAL PRIMARY KEY,
    space_id BIGINT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    document_id BIGINT REFERENCES documents(id) ON DELETE SET NULL,
    question TEXT NOT NULL,
    ground_truth TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS benchmark_runs (
    id BIGSERIAL PRIMARY KEY,
    space_id BIGINT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    approach_name VARCHAR(100) NOT NULL,
    faithfulness DOUBLE PRECISION,
    answer_relevance DOUBLE PRECISION,
    context_precision DOUBLE PRECISION,
    context_recall DOUBLE PRECISION,
    avg_latency_ms BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS benchmark_run_details (
    id BIGSERIAL PRIMARY KEY,
    benchmark_run_id BIGINT NOT NULL REFERENCES benchmark_runs(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    retrieved_contexts TEXT,
    generated_answer TEXT,
    ground_truth TEXT,
    latency_ms BIGINT,
    faithfulness DOUBLE PRECISION,
    answer_relevance DOUBLE PRECISION,
    context_precision DOUBLE PRECISION,
    context_recall DOUBLE PRECISION
);
