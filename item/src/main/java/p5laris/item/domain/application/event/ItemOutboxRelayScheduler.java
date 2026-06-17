package p5laris.item.domain.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import com.p5laris.proto.eventlog.v1.RecordEventLogRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import p5laris.common.outbox.OutboxBackoffPolicy;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.item.domain.domain.entity.OutboxEvent;
import p5laris.item.domain.domain.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemOutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    @PostConstruct
    public void init() {
        meterRegistry.gauge("outbox.pending.count", outboxEventRepository,
                repo -> repo.countByStatus("PENDING"));
    }

    @Value("${spring.application.name:item}")
    private String sourceService;

    @GrpcClient("event-log")
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;

    @Scheduled(fixedDelayString = "5000")
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(
                LocalDateTime.now(),
                org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent pendingEvent : pendingEvents) {
            OutboxEventClaimResult claimResult = claimEvent(pendingEvent.getId());
            if (claimResult == null) {
                continue;
            }

            String aggregateType = claimResult.aggregateType();
            String payload = claimResult.payload();
            String idempotencyKey = claimResult.idempotencyKey();

            try {
                if ("ITEM_EVENT_LOG".equals(aggregateType)) {
                    ItemEventLogEvent event = objectMapper.readValue(payload, ItemEventLogEvent.class);
                    eventLogStub.recordEventLog(toRequest(event, idempotencyKey));
                }
                
                markSuccess(pendingEvent.getId());
                
                meterRegistry.counter("outbox.events.processed",
                        "status", "SUCCESS",
                        "aggregate_type", aggregateType
                ).increment();
                
            } catch (Exception e) {
                log.error("Outbox event failed. id={}, type={}", pendingEvent.getId(), aggregateType, e);
                
                markFailure(pendingEvent.getId(), e.getMessage());

                meterRegistry.counter("outbox.events.processed",
                        "status", "FAILURE",
                        "aggregate_type", aggregateType
                ).increment();
            }
        }
    }

    private record OutboxEventClaimResult(String aggregateType, String payload, String idempotencyKey) {}

    private OutboxEventClaimResult claimEvent(Long eventId) {
        return transactionTemplate.execute(status -> {
            OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(eventId)
                    .orElse(null);

            if (outboxEvent == null) {
                return null;
            }

            if (!"PENDING".equals(outboxEvent.getStatus())) {
                return null;
            }

            if (outboxEvent.getNextAttemptAt() != null && outboxEvent.getNextAttemptAt().isAfter(LocalDateTime.now())) {
                return null;
            }

            outboxEvent.processing();
            outboxEventRepository.saveAndFlush(outboxEvent);
            return new OutboxEventClaimResult(
                    outboxEvent.getAggregateType(),
                    outboxEvent.getPayload(),
                    outboxEvent.getIdempotencyKey()
            );
        });
    }

    private void markSuccess(Long eventId) {
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));
            outboxEvent.success();
            outboxEventRepository.saveAndFlush(outboxEvent);
            log.debug("Outbox event succeeded. id={}", eventId);
        });
    }

    private void markFailure(Long eventId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));
            LocalDateTime nextAttempt = OutboxBackoffPolicy.nextAttemptAt(LocalDateTime.now(), outboxEvent.getAttemptCount() + 1);
            outboxEvent.fail(errorMessage, nextAttempt, MAX_ATTEMPTS);
            outboxEventRepository.saveAndFlush(outboxEvent);
        });
    }

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    @Transactional
    public void cleanupSucceededEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(1);
        outboxEventRepository.deleteSucceededEvents(threshold);
        log.info("Cleaned up succeeded outbox events older than 1 day");
    }

    private RecordEventLogRequest toRequest(ItemEventLogEvent event, String idempotencyKey) throws JsonProcessingException {
        RecordEventLogRequest.Builder builder = RecordEventLogRequest.newBuilder()
                .setEventId(idempotencyKey)
                .setEventType(event.eventType())
                .setSourceService(sourceService)
                .setOccurredAt(toTimestamp(event.occurredAt().toInstant()));

        if (event.userId() != null) {
            builder.setUserId(event.userId());
        }
        if (event.refType() != null && !event.refType().isBlank()) {
            builder.setRefType(event.refType());
        }
        if (event.refId() != null) {
            builder.setRefId(event.refId());
        }
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            builder.setPropertiesJson(objectMapper.writeValueAsString(event.metadata()));
        }

        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
