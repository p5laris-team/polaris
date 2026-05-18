package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.AttendanceService;
import p5laris.user.domain.domain.AttendanceRecord;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class AttendanceGrpcController extends AttendanceServiceGrpc.AttendanceServiceImplBase {

    private final AttendanceService attendanceService;

    @Override
    public void attend(AttendRequest request, StreamObserver<AttendResponse> responseObserver) {
        try {
            AttendanceRecord record = attendanceService.attend(request.getUserId());
            
            AttendResponse response = AttendResponse.newBuilder()
                    .setRecord(toDto(record))
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getAttendanceRecords(GetAttendanceRecordsRequest request, StreamObserver<GetAttendanceRecordsResponse> responseObserver) {
        try {
            List<AttendanceRecord> records = attendanceService.getAttendanceRecords(
                    request.getUserId(), request.getYear(), request.getMonth());
            
            List<AttendanceRecordDto> dtos = records.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
                    
            GetAttendanceRecordsResponse response = GetAttendanceRecordsResponse.newBuilder()
                    .addAllRecords(dtos)
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private AttendanceRecordDto toDto(AttendanceRecord record) {
        return AttendanceRecordDto.newBuilder()
                .setId(record.getId())
                .setAttendanceDate(record.getAttendanceDate().toString())
                .setRewardStarPiece(record.getRewardStarPiece())
                .setStreakCount(record.getStreakCount())
                .build();
    }
}
