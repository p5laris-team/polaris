CREATE INDEX IF NOT EXISTS idx_character_talk_messages_user_character_created
    ON character_talk_messages(user_id, character_id, created_at, id);
