package p5laris.mission.domain.infrastructure.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import p5laris.common.utils.EmbeddingVectorUtils;
import p5laris.mission.domain.application.memory.UserMemoryEmbeddingJob;
import p5laris.mission.domain.application.memory.UserMemoryRagHit;
import p5laris.mission.domain.domain.entity.UserMemory;
import p5laris.mission.domain.domain.enums.UserMemoryEmbeddingStatus;
import p5laris.mission.domain.domain.enums.UserMemorySourceType;
import p5laris.mission.domain.domain.enums.UserMemoryType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * pgvector 컬럼을 명시 SQL로 다루는 JDBC repository다.
 *
 * Hibernate vector 매핑을 추가하지 않고 저장, 검색, row claim 쿼리를 직접 제어한다.
 */
@Repository
@RequiredArgsConstructor
public class UserMemoryEmbeddingJdbcRepository {

    private static final int LAST_ERROR_MESSAGE_MAX_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void upsertPending(UserMemory memory, String model, int dimension, LocalDateTime nextAttemptAt) {
        jdbcTemplate.update("""
                        INSERT INTO user_memory_embeddings (
                            user_memory_id,
                            user_id,
                            embedding_model,
                            embedding_dimension,
                            status,
                            attempt_count,
                            next_attempt_at,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_memory_id, embedding_model, embedding_dimension)
                        DO UPDATE SET
                            user_id = EXCLUDED.user_id,
                            embedding = NULL,
                            status = EXCLUDED.status,
                            attempt_count = 0,
                            next_attempt_at = EXCLUDED.next_attempt_at,
                            last_error_message = NULL,
                            embedded_at = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                memory.getId(),
                memory.getUserId(),
                model,
                dimension,
                UserMemoryEmbeddingStatus.PENDING.name(),
                nextAttemptAt
        );
    }

    public List<UserMemoryEmbeddingJob> claimDue(LocalDateTime now, int batchSize, LocalDateTime lockExpiresAt) {
        return jdbcTemplate.query("""
                        WITH picked AS (
                            SELECT e.id
                            FROM user_memory_embeddings e
                            WHERE e.status IN (?, ?)
                              AND e.next_attempt_at <= ?
                            ORDER BY e.next_attempt_at ASC, e.id ASC
                            FOR UPDATE SKIP LOCKED
                            LIMIT ?
                        ),
                        claimed AS (
                            UPDATE user_memory_embeddings e
                            SET status = ?,
                                next_attempt_at = ?,
                                updated_at = CURRENT_TIMESTAMP
                            FROM picked
                            WHERE e.id = picked.id
                            RETURNING e.id, e.user_memory_id, e.user_id, e.attempt_count
                        )
                        SELECT
                            c.id,
                            c.user_memory_id,
                            c.user_id,
                            c.attempt_count,
                            m.content
                        FROM claimed c
                        JOIN user_memories m ON m.id = c.user_memory_id
                        ORDER BY c.id ASC
                        """,
                (rs, rowNum) -> new UserMemoryEmbeddingJob(
                        rs.getLong("id"),
                        rs.getLong("user_memory_id"),
                        rs.getLong("user_id"),
                        rs.getString("content"),
                        rs.getInt("attempt_count")
                ),
                UserMemoryEmbeddingStatus.PENDING.name(),
                UserMemoryEmbeddingStatus.PROCESSING.name(),
                now,
                Math.max(1, batchSize),
                UserMemoryEmbeddingStatus.PROCESSING.name(),
                lockExpiresAt
        );
    }

    public void markSucceeded(Long id, List<Float> normalizedVector, LocalDateTime embeddedAt) {
        jdbcTemplate.update("""
                        UPDATE user_memory_embeddings
                        SET embedding = CAST(? AS vector),
                            status = ?,
                            next_attempt_at = ?,
                            last_error_message = NULL,
                            embedded_at = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                EmbeddingVectorUtils.toPgVectorLiteral(normalizedVector),
                UserMemoryEmbeddingStatus.COMPLETED.name(),
                embeddedAt,
                embeddedAt,
                id
        );
    }

    public void recordFailure(Long id, String errorMessage, LocalDateTime nextAttemptAt, int maxAttempts) {
        jdbcTemplate.update("""
                        UPDATE user_memory_embeddings
                        SET attempt_count = attempt_count + 1,
                            status = CASE
                                WHEN attempt_count + 1 >= ? THEN ?
                                ELSE ?
                            END,
                            next_attempt_at = ?,
                            last_error_message = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                Math.max(1, maxAttempts),
                UserMemoryEmbeddingStatus.FAILED.name(),
                UserMemoryEmbeddingStatus.PENDING.name(),
                nextAttemptAt,
                truncate(errorMessage),
                id
        );
    }

    public List<UserMemoryRagHit> searchSimilar(
            Long userId,
            String model,
            int dimension,
            List<Float> normalizedQueryVector,
            int topK,
            double similarityThreshold
    ) {
        String vectorLiteral = EmbeddingVectorUtils.toPgVectorLiteral(normalizedQueryVector);
        double maxDistance = 1.0d - Math.min(1.0d, Math.max(0.0d, similarityThreshold));
        return jdbcTemplate.query("""
                        SELECT
                            user_memory_id,
                            memory_type,
                            source_type,
                            content,
                            metadata_json,
                            importance,
                            created_at,
                            distance
                        FROM (
                            SELECT
                                m.id AS user_memory_id,
                                m.memory_type,
                                m.source_type,
                                m.content,
                                m.metadata_json::text AS metadata_json,
                                m.importance,
                                m.created_at,
                                e.embedding <=> CAST(? AS vector) AS distance
                            FROM user_memory_embeddings e
                            JOIN user_memories m ON m.id = e.user_memory_id
                            WHERE e.user_id = ?
                              AND e.status = ?
                              AND e.embedding_model = ?
                              AND e.embedding_dimension = ?
                              AND e.embedding IS NOT NULL
                        ) hits
                        WHERE distance <= ?
                        ORDER BY distance ASC, importance DESC, created_at DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> toRagHit(rs),
                vectorLiteral,
                userId,
                UserMemoryEmbeddingStatus.COMPLETED.name(),
                model,
                dimension,
                maxDistance,
                Math.max(1, topK)
        );
    }

    private UserMemoryRagHit toRagHit(ResultSet rs) throws SQLException {
        return new UserMemoryRagHit(
                rs.getLong("user_memory_id"),
                UserMemoryType.valueOf(rs.getString("memory_type")),
                UserMemorySourceType.valueOf(rs.getString("source_type")),
                rs.getString("content"),
                parseJson(rs.getString("metadata_json")),
                rs.getInt("importance"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getDouble("distance")
        );
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= LAST_ERROR_MESSAGE_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, LAST_ERROR_MESSAGE_MAX_LENGTH);
    }
}
