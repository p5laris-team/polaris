package p5laris.item.domain.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import com.p5laris.proto.eventlog.v1.RecordEventLogRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import p5laris.item.domain.domain.entity.OutboxEvent;
import p5laris.item.domain.domain.repository.OutboxEventRepository;

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

    @Value("${spring.application.name:item}")
    private String sourceService;

    @GrpcClient("event-log")
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingOrFailedEvents(LocalDateTime.now());

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent outboxEvent : pendingEvents) {
            try {
                if ("ITEM_EVENT_LOG".equals(outboxEvent.getAggregateType())) {
                    ItemEventLogEvent event = objectMapper.readValue(outboxEvent.getPayload(), ItemEventLogEvent.class);
                    eventLogStub.recordEventLog(toRequest(event, outboxEvent.getIdempotencyKey()));
                }
                
                outboxEvent.success();
                outboxEventRepository.save(outboxEvent);
                log.debug("Outbox event succeeded. id={}", outboxEvent.getId());
                
            } catch (Exception e) {
                log.error("Outbox event failed. id={}, type={}", outboxEvent.getId(), outboxEvent.getAggregateType(), e);
                LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes((long) Math.pow(2, outboxEvent.getAttemptCount()));
                outboxEvent.fail(e.getMessage(), nextAttempt);
                outboxEventRepository.save(outboxEvent);
            }
        }
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
