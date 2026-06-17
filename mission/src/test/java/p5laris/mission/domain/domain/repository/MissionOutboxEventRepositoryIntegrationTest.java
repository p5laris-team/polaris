package p5laris.mission.domain.domain.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import p5laris.mission.domain.domain.entity.MissionOutboxEvent;
import p5laris.mission.domain.domain.enums.MissionOutboxEventStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MissionOutboxEventRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("mission")
                    .withUsername("mission")
                    .withPassword("mission");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private MissionOutboxEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayJsonbMappingAndDispatchableOrderingWorkOnPostgres() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 12, 0);
        long laterId = insertOutbox(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                now.minusMinutes(1),
                "reward-later"
        );
        long firstId = insertOutbox(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PROCESSING,
                now.minusMinutes(5),
                "reward-first"
        );
        insertOutbox(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                now.plusMinutes(1),
                "reward-not-due"
        );
        insertOutbox(
                MissionOutboxEvent.EVENT_TYPE_MISSION_CHARACTER_EXP_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                now.minusMinutes(10),
                "different-event"
        );

        List<Long> result = repository.findDispatchableIds(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                MissionOutboxEventStatus.PROCESSING,
                now,
                PageRequest.of(0, 10)
        );

        assertThat(result).containsExactly(firstId, laterId);
        MissionOutboxEvent mapped = repository.findById(firstId).orElseThrow();
        JsonNode payload = mapped.getPayload();
        assertThat(payload.get("userId").asLong()).isEqualTo(1001L);
        assertThat(payload.get("rewardStarPiece").asInt()).isEqualTo(10);
    }

    @Test
    void retryStateChangesArePersistedAndFailedRowsLeaveDispatchQueue() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 12, 0);
        long outboxId = insertOutbox(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                now.minusMinutes(1),
                "retry-state"
        );
        MissionOutboxEvent event = repository.findById(outboxId).orElseThrow();

        event.markProcessing(now.plusSeconds(30));
        repository.saveAndFlush(event);
        assertThat(repository.findById(outboxId).orElseThrow().getStatus())
                .isEqualTo(MissionOutboxEventStatus.PROCESSING);

        event.recordFailure("wallet timeout", now.plusMinutes(1), 2);
        repository.saveAndFlush(event);
        assertThat(event.getStatus()).isEqualTo(MissionOutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);

        event.recordFailure("wallet timeout again", now.plusMinutes(2), 2);
        repository.saveAndFlush(event);

        MissionOutboxEvent failed = repository.findById(outboxId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(MissionOutboxEventStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(2);
        assertThat(repository.findDispatchableIds(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                MissionOutboxEventStatus.PROCESSING,
                now.plusHours(1),
                PageRequest.of(0, 10)
        )).doesNotContain(outboxId);
    }

    @Test
    void postgresUniqueConstraintPreventsDuplicateIdempotencyKey() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 12, 0);
        insertOutbox(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                now,
                "duplicate-key"
        );

        assertThatThrownBy(() -> insertOutbox(
                MissionOutboxEvent.EVENT_TYPE_MISSION_REWARD_REQUESTED,
                MissionOutboxEventStatus.PENDING,
                now,
                "duplicate-key"
        )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private long insertOutbox(
            String eventType,
            MissionOutboxEventStatus status,
            LocalDateTime nextAttemptAt,
            String idempotencyKey
    ) {
        Long id = jdbcTemplate.queryForObject("""
                insert into mission_outbox_events (
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    payload,
                    idempotency_key,
                    status,
                    attempt_count,
                    next_attempt_at,
                    created_at,
                    updated_at
                ) values (
                    'MISSION',
                    501,
                    ?,
                    cast(? as jsonb),
                    ?,
                    ?,
                    0,
                    ?,
                    current_timestamp,
                    current_timestamp
                )
                returning id
                """,
                Long.class,
                eventType,
                """
                        {"missionId":501,"userId":1001,"rewardStarPiece":10}
                        """,
                idempotencyKey,
                status.name(),
                nextAttemptAt
        );
        return id;
    }
}
