package p5laris.item.domain.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import com.p5laris.proto.eventlog.v1.RecordEventLogRequest;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class ItemEventLogEventListener {

    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:item}")
    private String sourceService;

    @GrpcClient("event-log")
    private EventLogServiceGrpc.EventLogServiceBlockingStub eventLogStub;

    public ItemEventLogEventListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItemEventLogEvent event) {
        try {
            eventLogStub.recordEventLog(toRequest(event));
        } catch (Exception e) {
            log.warn("Failed to send item event log. eventType={}, userId={}",
                    event.eventType(), event.userId(), e);
        }
    }

    private RecordEventLogRequest toRequest(ItemEventLogEvent event) throws Exception {
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
