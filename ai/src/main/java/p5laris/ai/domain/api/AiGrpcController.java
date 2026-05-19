package p5laris.ai.domain.api;

import com.p5laris.proto.ai.v1.AiErrorType;
import com.p5laris.proto.ai.v1.AiGenerationStatus;
import com.p5laris.proto.ai.v1.AiServiceGrpc;
import com.p5laris.proto.ai.v1.GenerateMissionTextsRequest;
import com.p5laris.proto.ai.v1.GenerateMissionTextsResponse;
import com.p5laris.proto.ai.v1.HealthStatus;
import com.p5laris.proto.ai.v1.PingPongRequest;
import com.p5laris.proto.ai.v1.PingPongResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.ai.domain.application.AiMissionTextService;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.dto.MissionTextGenerationResult;
import p5laris.ai.domain.exception.AiErrorCode;
import p5laris.ai.domain.exception.AiException;

/**
 * AI 모듈의 gRPC 진입점이다.
 *
 * proto message를 application service가 이해하는 command로 바꾸고,
 * application 결과를 다시 proto response로 변환한다.
 */
@GrpcService
@RequiredArgsConstructor
public class AiGrpcController extends AiServiceGrpc.AiServiceImplBase {

    private final AiMissionTextService aiMissionTextService;

    // AI gRPC 서버가 살아 있는지 확인하는 단순 헬스체크 메서드다.
    @Override
    public void pingPong(PingPongRequest request, StreamObserver<PingPongResponse> responseObserver) {
        PingPongResponse response = PingPongResponse.newBuilder()
                .setHealthStatus(HealthStatus.HEALTHY)
                .setMessage(request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // 선택된 미션 템플릿을 캐릭터 말투 문구 3개로 변환하는 gRPC 메서드다.
    @Override
    public void generateMissionTexts(
            GenerateMissionTextsRequest request,
            StreamObserver<GenerateMissionTextsResponse> responseObserver
    ) {
        try {
            MissionTextGenerationResult result = aiMissionTextService.generateMissionTexts(toCommand(request));
            responseObserver.onNext(toResponse(result));
            responseObserver.onCompleted();
        } catch (AiException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // proto request는 내부 계층에 직접 넘기지 않고 command로 변환한다.
    private MissionTextGenerationCommand toCommand(GenerateMissionTextsRequest request) {
        return new MissionTextGenerationCommand(
                request.getUserId(),
                request.getCharacterId(),
                request.getCharacterType(),
                request.getMissionTemplateId(),
                request.getBaseTitle(),
                request.getBaseDescription(),
                request.getCategory(),
                request.getDifficulty(),
                request.getFallbackCharacterMessage(),
                request.getFallbackQuestion(),
                request.getFallbackCompletionResponse(),
                request.getOnboardingContextJson(),
                request.getRecentMissionContextJson(),
                request.getRequestId()
        );
    }

    // application result를 gRPC 응답 계약에 맞는 proto response로 변환한다.
    private GenerateMissionTextsResponse toResponse(MissionTextGenerationResult result) {
        GenerateMissionTextsResponse.Builder builder = GenerateMissionTextsResponse.newBuilder()
                .setAiGenerationId(result.aiGenerationId())
                .setStatus(toProtoStatus(result.status()))
                .setCharacterMessage(result.characterMessage())
                .setCompletionQuestion(result.completionQuestion())
                .setCompletionCharacterResponse(result.completionCharacterResponse())
                .setFallbackUsed(result.fallbackUsed())
                .setRequestId(result.requestId());

        if (result.errorType() != null) {
            builder.setErrorType(toProtoErrorType(result.errorType()));
        }

        return builder.build();
    }

    // 내부 enum과 proto enum은 타입이 다르므로 명시적으로 매핑한다.
    private AiGenerationStatus toProtoStatus(p5laris.ai.domain.domain.enums.AiGenerationStatus status) {
        return switch (status) {
            case SUCCESS -> AiGenerationStatus.AI_GENERATION_STATUS_SUCCESS;
            case FALLBACK -> AiGenerationStatus.AI_GENERATION_STATUS_FALLBACK;
            case FAILED -> AiGenerationStatus.AI_GENERATION_STATUS_FAILED;
        };
    }

    // AI 실패 원인도 proto enum으로 변환해 호출자가 fallback 원인을 알 수 있게 한다.
    private AiErrorType toProtoErrorType(p5laris.ai.domain.domain.enums.AiErrorType errorType) {
        return switch (errorType) {
            case TIMEOUT -> AiErrorType.AI_ERROR_TYPE_TIMEOUT;
            case RATE_LIMIT -> AiErrorType.AI_ERROR_TYPE_RATE_LIMIT;
            case INVALID_OUTPUT -> AiErrorType.AI_ERROR_TYPE_INVALID_OUTPUT;
            case POLICY_VIOLATION -> AiErrorType.AI_ERROR_TYPE_POLICY_VIOLATION;
            case PROVIDER_ERROR -> AiErrorType.AI_ERROR_TYPE_PROVIDER_ERROR;
            case UNKNOWN -> AiErrorType.AI_ERROR_TYPE_UNKNOWN;
        };
    }

    // AI 도메인 예외를 gateway가 해석 가능한 gRPC status로 변환한다.
    private Status toStatus(AiException e) {
        AiErrorCode errorCode = e.getErrorCode();

        return switch (errorCode) {
            case AI_INVALID_REQUEST -> Status.INVALID_ARGUMENT;
            case AI_DUPLICATED_REQUEST -> Status.ALREADY_EXISTS;
            case AI_FALLBACK_INVALID -> Status.FAILED_PRECONDITION;
            case AI_GENERATION_FAILED -> Status.INTERNAL;
        };
    }
}
