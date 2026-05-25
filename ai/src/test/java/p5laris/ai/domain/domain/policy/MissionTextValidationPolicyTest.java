package p5laris.ai.domain.domain.policy;

import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.exception.FallbackRequiredException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionTextValidationPolicyTest {

    private final MissionTextValidationPolicy policy = new MissionTextValidationPolicy();

    @Test
    void 무무_문구는_해석_밖에_무무_발화만_있으면_통과한다() {
        MissionTextCandidate candidate = new MissionTextCandidate(
                "무우... 무...? (해석: 오래된 알림 하나를 지워볼까요?)",
                "무...? (해석: 지우고 나서 마음이 조금 가벼워졌나요?)",
                "무...! (해석: 작은 정리도 충분히 반짝였어요.)"
        );

        assertThatCode(() -> policy.validate(candidate, "MUMU"))
                .doesNotThrowAnyException();
    }

    @Test
    void 무무_문구는_해석_밖에_미션_내용이_섞이면_거절한다() {
        MissionTextCandidate candidate = new MissionTextCandidate(
                "무우... 오래된 알림 하나 지우는 무우...? (해석: 오래된 알림 하나를 지워볼까요?)",
                "무...? (해석: 지우고 나서 마음이 조금 가벼워졌나요?)",
                "무...! (해석: 작은 정리도 충분히 반짝였어요.)"
        );

        assertThatThrownBy(() -> policy.validate(candidate, "MUMU"))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.INVALID_OUTPUT);
    }

    @Test
    void 무무_문구는_해석이_없으면_거절한다() {
        MissionTextCandidate candidate = new MissionTextCandidate(
                "무우... 무...?",
                "무...? (해석: 해보고 나서 어땠나요?)",
                "무...! (해석: 작은 완료도 충분히 반짝였어요.)"
        );

        assertThatThrownBy(() -> policy.validate(candidate, "MUMU"))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.INVALID_OUTPUT);
    }

    @Test
    void 무무가_아니면_일반_한국어_문구를_허용한다() {
        MissionTextCandidate candidate = new MissionTextCandidate(
                "오래된 알림 하나만 지워보자. 작은 빛이 될 거야.",
                "지우고 나서 마음이 조금 가벼워졌어?",
                "잘했어. 오늘의 작은 정리를 기억할게."
        );

        assertThatCode(() -> policy.validate(candidate, "NOVA"))
                .doesNotThrowAnyException();
    }
}
