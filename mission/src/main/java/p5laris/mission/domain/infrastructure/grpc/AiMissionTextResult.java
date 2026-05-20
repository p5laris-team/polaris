package p5laris.mission.domain.infrastructure.grpc;

/**
 * ai 모듈이 생성한 미션 말투 문구 결과다.
 *
 * mission 모듈은 gRPC 응답 객체를 직접 들고 다니지 않고,
 * 저장에 필요한 값만 이 record로 받아 사용한다.
 */
public record AiMissionTextResult(
        Long aiGenerationId,
        String characterMessage,
        String completionQuestion,
        String completionCharacterResponse,
        boolean fallbackUsed
) {
}
