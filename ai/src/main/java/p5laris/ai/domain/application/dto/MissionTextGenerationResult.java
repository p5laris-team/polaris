package p5laris.ai.domain.application.dto;

import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiGenerationStatus;

/**
 * AI 미션 문구 생성 유스케이스의 최종 결과다.
 *
 * gRPC controller는 이 값을 proto response로 변환한다.
 */
public record MissionTextGenerationResult(
        Long aiGenerationId,
        AiGenerationStatus status,
        String characterMessage,
        String completionQuestion,
        String completionCharacterResponse,
        boolean fallbackUsed,
        AiErrorType errorType,
        String requestId
) {
}
