package p5laris.ai.domain.application.dto;

/**
 * provider 또는 local generator가 만든 문구 후보 3종이다.
 *
 * 이 값은 바로 저장하지 않고 MissionTextValidationPolicy 검증을 통과해야 한다.
 */
public record MissionTextCandidate(
        String characterMessage,
        String completionQuestion,
        String completionCharacterResponse
) {
}
