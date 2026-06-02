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

        for (OutboxEvent pendingEvent : pendingEvents) {
            try {
                // 비관적 락 획득 및 최신 상태 조회
                OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(pendingEvent.getId())
                        .orElse(null);

                if (outboxEvent == null) {
                    continue;
                }

                // 최신 상태 재검증 (이미 성공했거나 타 서버가 처리 중인 경우 건너뜀)
                if (!"PENDING".equals(outboxEvent.getStatus()) && !"FAILED".equals(outboxEvent.getStatus())) {
                    continue;
                }

                // 재시도 대기 시각이 아직 지나지 않은 경우 건너뜀 (타 서버가 처리 실패 후 대기 중인 경우)
                if (outboxEvent.getNextAttemptAt() != null && outboxEvent.getNextAttemptAt().isAfter(LocalDateTime.now())) {
                    continue;
                }

                // 상태를 PROCESSING으로 변경하여 락 효과
                outboxEvent.processing();
                outboxEventRepository.saveAndFlush(outboxEvent);

                if ("ITEM_EVENT_LOG".equals(outboxEvent.getAggregateType())) {
                    ItemEventLogEvent event = objectMapper.readValue(outboxEvent.getPayload(), ItemEventLogEvent.class);
                    eventLogStub.recordEventLog(toRequest(event, outboxEvent.getIdempotencyKey()));
                }
                
                outboxEvent.success();
                outboxEventRepository.saveAndFlush(outboxEvent);
                log.debug("Outbox event succeeded. id={}", outboxEvent.getId());
                
            } catch (Exception e) {
                log.error("Outbox event failed. id={}, type={}", pendingEvent.getId(), pendingEvent.getAggregateType(), e);
                
                // 락이 잡힌 최신 엔티티의 attemptCount를 기준으로 갱신
                OutboxEvent outboxEvent = outboxEventRepository.findByIdForUpdate(pendingEvent.getId()).orElse(pendingEvent);
                LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes((long) Math.pow(2, outboxEvent.getAttemptCount()));
                outboxEvent.fail(e.getMessage(), nextAttempt);
                outboxEventRepository.saveAndFlush(outboxEvent);
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
