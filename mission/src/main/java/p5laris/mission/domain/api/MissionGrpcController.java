package p5laris.mission.domain.api;

import com.p5laris.proto.mission.v1.CreateNextMissionRequest;
import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.GetCurrentMissionRequest;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.GetMissionDetailRequest;
import com.p5laris.proto.mission.v1.GetMissionDetailResponse;
import com.p5laris.proto.mission.v1.GetTodayMissionsRequest;
import com.p5laris.proto.mission.v1.GetTodayMissionsResponse;
import com.p5laris.proto.mission.v1.HealthStatus;
import com.p5laris.proto.mission.v1.MissionServiceGrpc;
import com.p5laris.proto.mission.v1.PingPongRequest;
import com.p5laris.proto.mission.v1.PingPongResponse;
import com.p5laris.proto.mission.v1.RejectMissionRequest;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import com.p5laris.proto.mission.v1.StartCompletionSessionRequest;
import com.p5laris.proto.mission.v1.StartCompletionSessionResponse;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerRequest;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.mission.domain.application.MissionService;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;

import java.time.LocalDate;

@GrpcService
@RequiredArgsConstructor
public class MissionGrpcController extends MissionServiceGrpc.MissionServiceImplBase {

    private final MissionService missionService;

    // mission gRPC 서버가 살아 있는지 확인하는 단순 헬스체크 메서드다.
    @Override
    public void pingPong(PingPongRequest request, StreamObserver<PingPongResponse> responseObserver) {
        PingPongResponse response = PingPongResponse.newBuilder()
                .setHealthStatus(HealthStatus.HEALTHY)
                .setMessage(request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // 현재 미션 조회 gRPC 엔드포인트다.
    // request에는 characterId가 있지만, 현재 미션 조회 정책은 유저 기준이므로 userId만 service로 넘긴다.
    @Override
    public void getCurrentMission(
            GetCurrentMissionRequest request,
            StreamObserver<GetCurrentMissionResponse> responseObserver
    ) {
        try {
            GetCurrentMissionResponse response = missionService.getCurrentMission(
                    request.getUserId()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MissionException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // 오늘 미션 히스토리 조회 gRPC 엔드포인트다.
    // 오늘 stack 전체를 userId 기준으로 조회하고, 현재 진행 중 미션 id도 함께 내려준다.
    @Override
    public void getTodayMissions(
            GetTodayMissionsRequest request,
            StreamObserver<GetTodayMissionsResponse> responseObserver
    ) {
        try {
            GetTodayMissionsResponse response = missionService.getTodayMissions(
                    request.getUserId(),
                    parseMissionDate(request)
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MissionException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // 미션 상세 조회 gRPC 엔드포인트다.
    // 목록에서는 답변 미리보기만 내려주고, 전문은 상세 조회에서만 반환한다.
    @Override
    public void getMissionDetail(
            GetMissionDetailRequest request,
            StreamObserver<GetMissionDetailResponse> responseObserver
    ) {
        try {
            GetMissionDetailResponse response = missionService.getMissionDetail(
                    request.getUserId(),
                    request.getMissionId()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MissionException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // 다음 미션 생성 gRPC 엔드포인트다.
    // 이때 characterId는 "어떤 캐릭터가 제안했는지" user_missions에 저장하기 위해 사용한다.
    @Override
    public void createNextMission(
            CreateNextMissionRequest request,
            StreamObserver<CreateNextMissionResponse> responseObserver
    ) {
        try {
            CreateNextMissionResponse response = missionService.createNextMission(
                    request.getUserId(),
                    request.getCharacterId(),
                    request.getLastMissionId()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MissionException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // 미션 거절 gRPC 엔드포인트다.
    // 거절 권한 확인은 userId + missionId로 처리하고, characterId는 소유권 조건으로 사용하지 않는다.
    @Override
    public void rejectMission(
            RejectMissionRequest request,
            StreamObserver<RejectMissionResponse> responseObserver
    ) {
        try {
            RejectMissionResponse response = missionService.rejectMission(
                    request.getUserId(),
                    request.getMissionId()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MissionException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // 완료 질문 세션 시작 gRPC 엔드포인트다.
    // OFFERED 상태 미션을 ANSWERING으로 바꾸고 완료 질문 1개를 반환한다.
    @Override
    public void startCompletionSession(
            StartCompletionSessionRequest request,
            StreamObserver<StartCompletionSessionResponse> responseObserver
    ) {
        try {
            StartCompletionSessionResponse response = missionService.startCompletionSession(
                    request.getUserId(),
                    request.getMissionId()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MissionException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // 완료 답변 제출 gRPC 엔드포인트다.
    // ANSWERING 상태 미션에 텍스트 답변을 저장하고 COMPLETED로 전환한다.
    @Override
    public void submitCompletionAnswer(
            SubmitCompletionAnswerRequest request,
            StreamObserver<SubmitCompletionAnswerResponse> responseObserver
    ) {
        try {
            SubmitCompletionAnswerResponse response = missionService.submitCompletionAnswer(
                    request.getUserId(),
                    request.getMissionId(),
                    request.getAnswer()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MissionException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // mission 도메인 예외를 gateway가 이해할 수 있는 gRPC status로 변환한다.
    private Status toStatus(MissionException e) {
        MissionErrorCode errorCode = e.getErrorCode();

        return switch (errorCode) {
            case MISSION_NOT_FOUND, MISSION_TEMPLATE_NOT_FOUND -> Status.NOT_FOUND;
            case MISSION_ANSWER_INVALID -> Status.INVALID_ARGUMENT;
            case MISSION_ALREADY_COMPLETED -> Status.ALREADY_EXISTS;
            case MISSION_DAILY_LIMIT_EXCEEDED, MISSION_REJECT_LIMIT_EXCEEDED -> Status.RESOURCE_EXHAUSTED;
            case MISSION_REWARD_FAILED -> Status.UNAVAILABLE;
            case MISSION_INVALID_STATUS, MISSION_ACTIVE_ALREADY_EXISTS -> Status.FAILED_PRECONDITION;
        };
    }

    private LocalDate parseMissionDate(GetTodayMissionsRequest request) {
        if (!request.hasMissionDate() || request.getMissionDate().isBlank()) {
            return null;
        }

        return LocalDate.parse(request.getMissionDate());
    }
}
