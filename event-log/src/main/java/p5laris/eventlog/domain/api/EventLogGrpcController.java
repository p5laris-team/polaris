package p5laris.eventlog.domain.api;

import com.google.protobuf.Timestamp;
import com.p5laris.proto.eventlog.v1.EventLogServiceGrpc;
import com.p5laris.proto.eventlog.v1.RecordEventLogRequest;
import com.p5laris.proto.eventlog.v1.RecordEventLogResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.eventlog.domain.application.EventLogService;
import p5laris.eventlog.domain.domain.dto.EventLogRequest;
import p5laris.eventlog.domain.domain.dto.EventLogResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class EventLogGrpcController extends EventLogServiceGrpc.EventLogServiceImplBase {

    private final EventLogService eventLogService;

    @Override
    public void recordEventLog(
            RecordEventLogRequest request,
            StreamObserver<RecordEventLogResponse> responseObserver
    ) {
        try {
            EventLogResponse result = eventLogService.recordEventLog(toRequest(request));

            RecordEventLogResponse response = RecordEventLogResponse.newBuilder()
                    .setRecorded(result.recorded())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        }
    }

    private EventLogRequest toRequest(RecordEventLogRequest request) {
        return new EventLogRequest(
                UUID.fromString(request.getEventId()),
                request.getEventType(),
                request.hasSourceService() ? request.getSourceService() : null,
                request.hasUserId() ? request.getUserId() : null,
                request.hasAnonymousId() ? request.getAnonymousId() : null,
                request.hasRefType() ? request.getRefType() : null,
                request.hasRefId() ? request.getRefId() : null,
                request.hasPropertiesJson() ? request.getPropertiesJson() : null,
                request.hasContextJson() ? request.getContextJson() : null,
                toLocalDateTime(request.getOccurredAt())
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        Instant instant = Instant.ofEpochSecond(
                timestamp.getSeconds(),
                timestamp.getNanos()
        );

        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
