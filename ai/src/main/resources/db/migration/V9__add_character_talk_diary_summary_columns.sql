ALTER TABLE character_talk_memories
    ADD COLUMN diary_text TEXT;

UPDATE character_talk_memories
SET diary_text = summary
WHERE diary_text IS NULL;

ALTER TABLE character_talk_memories
    ALTER COLUMN diary_text SET NOT NULL;
