CREATE INDEX IF NOT EXISTS idx_user_memory_embeddings_dispatch_due
    ON user_memory_embeddings(status, next_attempt_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX IF NOT EXISTS idx_user_memory_embeddings_rag_lookup
    ON user_memory_embeddings(user_id, status, embedding_model, embedding_dimension, user_memory_id)
    WHERE embedding IS NOT NULL;
