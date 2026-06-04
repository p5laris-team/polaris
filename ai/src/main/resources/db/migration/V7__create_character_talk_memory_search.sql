CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE character_talk_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    character_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    last_message_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    message_count INT NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    total_actual_prompt_tokens INT CHECK (total_actual_prompt_tokens >= 0),
    total_actual_completion_tokens INT CHECK (total_actual_completion_tokens >= 0),
    total_actual_tokens INT CHECK (total_actual_tokens >= 0),
    summary_created_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_character_talk_sessions_session_id UNIQUE (session_id),
    CONSTRAINT chk_character_talk_sessions_status CHECK (
        status IN ('ACTIVE', 'EXPIRED', 'MEMORY_READY')
    )
);

CREATE INDEX idx_character_talk_sessions_user_character_expires
    ON character_talk_sessions(user_id, character_id, expires_at DESC);

CREATE INDEX idx_character_talk_sessions_status_expires
    ON character_talk_sessions(status, expires_at, id);

CREATE TABLE character_talk_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sequence INT NOT NULL CHECK (sequence > 0),
    request_id VARCHAR(120) NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_character_talk_messages_session
        FOREIGN KEY (session_id)
        REFERENCES character_talk_sessions(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_character_talk_messages_session_sequence
        UNIQUE (session_id, sequence),
    CONSTRAINT chk_character_talk_messages_role CHECK (
        role IN ('USER', 'ASSISTANT')
    )
);

CREATE INDEX idx_character_talk_messages_session_sequence
    ON character_talk_messages(session_id, sequence);

CREATE INDEX idx_character_talk_messages_created_at
    ON character_talk_messages(created_at);

CREATE TABLE character_talk_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    source_session_id BIGINT NOT NULL,
    memory_type VARCHAR(30) NOT NULL,
    summary TEXT NOT NULL,
    embedding_model VARCHAR(80) NOT NULL,
    embedding_dimension INT NOT NULL,
    embedding vector(768),
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_character_talk_memories_session
        FOREIGN KEY (source_session_id)
        REFERENCES character_talk_sessions(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_character_talk_memories_session_type
        UNIQUE (source_session_id, memory_type),
    CONSTRAINT chk_character_talk_memories_type CHECK (
        memory_type IN ('SESSION_SUMMARY')
    ),
    CONSTRAINT chk_character_talk_memories_dimension CHECK (
        embedding_dimension = 768
    )
);

CREATE INDEX idx_character_talk_memories_user_character_created
    ON character_talk_memories(user_id, character_id, created_at DESC);

CREATE OR REPLACE VIEW daily_character_talk_metrics_view AS
SELECT
    DATE(created_at) AS metric_date,
    COUNT(*) AS session_count,
    SUM(message_count) AS message_count,
    AVG(total_actual_tokens) AS avg_actual_tokens,
    MAX(total_actual_tokens) AS max_actual_tokens,
    AVG(total_actual_prompt_tokens) AS avg_actual_prompt_tokens,
    AVG(total_actual_completion_tokens) AS avg_actual_completion_tokens
FROM character_talk_sessions
GROUP BY DATE(created_at);
