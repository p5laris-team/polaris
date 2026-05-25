package p5laris.ai.domain.domain.policy;

import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.exception.FallbackRequiredException;

import java.util.List;

/**
 * AI provider 또는 rule-based generator가 만든 문구가 서비스 정책에 맞는지 검증한다.
 *
 * Polaris 미션 문구는 짧고 가벼워야 하며, 사용자를 비난하거나 죄책감 들게 만들면 안 된다.
 */
@Component
public class MissionTextValidationPolicy {

    private static final int MAX_TEXT_LENGTH = 100;
    private static final List<String> PROHIBITED_EXPRESSIONS = List.of(
            "왜 못",
            "게으르",
            "한심",
            "실패자",
            "못난",
            "정신 차려",
            "안 하면",
            "벌 받을"
    );

    // 제안 문구, 완료 질문, 완료 반응 모두 사용자에게 보이는 텍스트라 같은 기준으로 검증한다.
    public void validate(MissionTextCandidate candidate) {
        validateText(candidate.characterMessage());
        validateText(candidate.completionQuestion());
        validateText(candidate.completionCharacterResponse());
    }

    // 비어 있거나, 말풍선 길이를 넘거나, 금지 표현이 포함되면 fallback 흐름으로 보낸다.
    private void validateText(String value) {
        if (value == null || value.isBlank()) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "AI 문구가 비어 있습니다.");
        }

        if (value.length() > MAX_TEXT_LENGTH) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "AI 문구가 최대 길이를 초과했습니다.");
        }

        for (String expression : PROHIBITED_EXPRESSIONS) {
            if (value.contains(expression)) {
                throw new FallbackRequiredException(AiErrorType.POLICY_VIOLATION, "AI 문구에 금지 표현이 포함되어 있습니다.");
            }
        }
    }
}
