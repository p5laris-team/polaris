package p5laris.mission.domain.application.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.mission.domain.infrastructure.config.MissionMemoryEmbeddingProperties;
import p5laris.mission.domain.infrastructure.config.MissionRagProperties;
import p5laris.mission.domain.infrastructure.grpc.AiTextEmbeddingClient;
import p5laris.mission.domain.infrastructure.grpc.AiTextEmbeddingResult;
import p5laris.mission.domain.infrastructure.repository.UserMemoryEmbeddingJdbcRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserMemoryEmbeddingDispatcherTest {

    private final UserMemoryEmbeddingJdbcRepository repository = mock(UserMemoryEmbeddingJdbcRepository.class);
    private final AiTextEmbeddingClient aiTextEmbeddingClient = mock(AiTextEmbeddingClient.class);
    private final MissionMemoryEmbeddingProperties embeddingProperties = embeddingProperties();
    private final MissionRagProperties ragProperties = ragProperties();
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-06-12T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final UserMemoryEmbeddingDispatcher dispatcher = new UserMemoryEmbeddingDispatcher(
            repository,
            aiTextEmbeddingClient,
            embeddingProperties,
            ragProperties,
            transactionTemplate,
            clock
    );

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void claim된_job만_embedding을_호출하고_정규화한_벡터를_저장한다() {
        LocalDateTime now = LocalDateTime.now(clock);
        UserMemoryEmbeddingJob job = new UserMemoryEmbeddingJob(
                1L,
                10L,
                1001L,
                "밤에는 강한 운동보다 가벼운 스트레칭을 선호한다.",
                0
        );
        when(repository.claimDue(now, 10, now.plusSeconds(30)))
                .thenReturn(List.of(job));
        when(aiTextEmbeddingClient.generateTextEmbedding(any(), anyLong()))
                .thenReturn(Optional.of(new AiTextEmbeddingResult(
                        "gemini-embedding-001",
                        3,
                        List.of(3.0f, 4.0f, 0.0f),
                        "request"
                )));

        int succeededCount = dispatcher.dispatchDue(10);

        assertThat(succeededCount).isEqualTo(1);
        verify(aiTextEmbeddingClient).generateTextEmbedding(any(), eq(3_000L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Float>> vectorCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).markSucceeded(eq(1L), vectorCaptor.capture(), eq(now));
        assertThat(vectorCaptor.getValue()).containsExactly(0.6f, 0.8f, 0.0f);
    }

    @Test
    void claim된_job이_없으면_provider를_호출하지_않는다() {
        LocalDateTime now = LocalDateTime.now(clock);
        when(repository.claimDue(now, 10, now.plusSeconds(30)))
                .thenReturn(List.of());

        int succeededCount = dispatcher.dispatchDue(10);

        assertThat(succeededCount).isZero();
        verifyNoInteractions(aiTextEmbeddingClient);
        verify(repository, never()).markSucceeded(anyLong(), any(), any());
        verify(repository, never()).recordFailure(anyLong(), any(), any(), anyInt());
    }

    @Test
    void embedding_응답이_비어_있으면_failure로_기록한다() {
        LocalDateTime now = LocalDateTime.now(clock);
        UserMemoryEmbeddingJob job = new UserMemoryEmbeddingJob(1L, 10L, 1001L, "기억", 1);
        when(repository.claimDue(now, 10, now.plusSeconds(30)))
                .thenReturn(List.of(job));
        when(aiTextEmbeddingClient.generateTextEmbedding(any(), anyLong()))
                .thenReturn(Optional.empty());

        int succeededCount = dispatcher.dispatchDue(10);

        assertThat(succeededCount).isZero();
        verify(repository).recordFailure(
                eq(1L),
                contains("비어"),
                eq(now.plusSeconds(20)),
                eq(3)
        );
        verify(repository, never()).markSucceeded(anyLong(), any(), any());
    }

    @Test
    void embedding_model_dimension이_정책과_다르면_failure로_기록한다() {
        LocalDateTime now = LocalDateTime.now(clock);
        UserMemoryEmbeddingJob job = new UserMemoryEmbeddingJob(1L, 10L, 1001L, "기억", 0);
        when(repository.claimDue(now, 10, now.plusSeconds(30)))
                .thenReturn(List.of(job));
        when(aiTextEmbeddingClient.generateTextEmbedding(any(), anyLong()))
                .thenReturn(Optional.of(new AiTextEmbeddingResult(
                        "other-model",
                        3,
                        List.of(3.0f, 4.0f, 0.0f),
                        "request"
                )));

        int succeededCount = dispatcher.dispatchDue(10);

        assertThat(succeededCount).isZero();
        verify(repository).recordFailure(
                eq(1L),
                contains("model/dimension"),
                eq(now.plusSeconds(10)),
                eq(3)
        );
        verify(repository, never()).markSucceeded(anyLong(), any(), any());
    }

    private MissionMemoryEmbeddingProperties embeddingProperties() {
        MissionMemoryEmbeddingProperties properties = new MissionMemoryEmbeddingProperties();
        properties.setProcessingTimeoutSeconds(30);
        properties.setRetryInitialDelaySeconds(10);
        properties.setRetryMaxDelaySeconds(60);
        properties.setMaxAttempts(3);
        properties.setEmbeddingDeadlineMs(3_000L);
        return properties;
    }

    private MissionRagProperties ragProperties() {
        MissionRagProperties properties = new MissionRagProperties();
        properties.setEmbeddingModel("gemini-embedding-001");
        properties.setEmbeddingDimension(3);
        return properties;
    }
}
