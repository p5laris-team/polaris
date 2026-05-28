package p5laris.user.domain.application.event;

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
import p5laris.user.domain.domain.entity.OutboxEvent;
import p5laris.user.domain.domain.repository.OutboxEventRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:user}")
    private String sourceService;

    @GrpcClient("event-log")
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    @GrpcClient("notification")
    private com.p5laris.proto.notification.v1.NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> events = outboxEventRepository.findPendingOrFailedEvents(LocalDateTime.now());
        
        for (OutboxEvent outboxEvent : events) {
            try {
                // 상태를 PROCESSING으로 변경하여 락 효과
                outboxEvent.processing();
                outboxEventRepository.saveAndFlush(outboxEvent);

                if ("USER_EVENT_LOG".equals(outboxEvent.getAggregateType())) {
                    UserEventLogEvent event = objectMapper.readValue(outboxEvent.getPayload(), UserEventLogEvent.class);
                    eventLogStub.recordEventLog(toRequest(event));
                } else if ("NOTIFICATION_REQUEST".equals(outboxEvent.getAggregateType())) {
                    NotificationRequestEvent event = objectMapper.readValue(outboxEvent.getPayload(), NotificationRequestEvent.class);
                    com.p5laris.proto.notification.v1.SendPushNotificationRequest req = com.p5laris.proto.notification.v1.SendPushNotificationRequest.newBuilder()
                            .setUserId(event.userId())
                            .setTitle(event.title())
                            .setBody(event.body())
                            .setNotificationType(com.p5laris.proto.notification.v1.NotificationType.valueOf(event.notificationType()))
                            .build();
                    notificationStub.sendPushNotification(req);
                }
                
                outboxEvent.success();
                log.debug("Successfully relayed outbox event: {}", outboxEvent.getId());
            } catch (Exception e) {
                log.error("Failed to relay outbox event: {}", outboxEvent.getId(), e);
                
                int attempt = outboxEvent.getAttemptCount() + 1;
                // 지수 백오프: 1분 -> 2분 -> 4분 -> 8분 ...
                long backoffMinutes = (long) Math.pow(2, attempt - 1);
                LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(backoffMinutes);
                
                outboxEvent.fail(e.getMessage(), nextAttempt);
            }
        }
    }

    private RecordEventLogRequest toRequest(UserEventLogEvent event) throws Exception {
        RecordEventLogRequest.Builder builder = RecordEventLogRequest.newBuilder()
                .setEventId(UUID.randomUUID().toString())
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
