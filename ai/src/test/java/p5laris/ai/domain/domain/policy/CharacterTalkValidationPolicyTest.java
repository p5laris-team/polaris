package p5laris.ai.domain.domain.policy;

import org.junit.jupiter.api.Test;
import p5laris.ai.domain.exception.FallbackRequiredException;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacterTalkValidationPolicyTest {

    private final CharacterTalkValidationPolicy policy = new CharacterTalkValidationPolicy(
            new AiCharacterTalkProperties()
    );

    @Test
    void 무무_대화는_해석_밖에_감정_발화만_있으면_통과한다() {
        assertThatCode(() -> policy.validateReply(
                "무무 ㅠㅠ... (해석: 오늘 많이 버거웠구나. 나 지금 네 편에서 듣고 있어.)",
                "MUMU"
        )).doesNotThrowAnyException();
    }

    @Test
    void 무무_대화는_해석_밖에_한국어_의미문장이_섞이면_거절한다() {
        assertThatThrownBy(() -> policy.validateReply(
                "무무 오늘 힘들었겠다 (해석: 오늘 많이 버거웠구나.)",
                "MUMU"
        )).isInstanceOf(FallbackRequiredException.class);
    }

    @Test
    void 무무_대화는_해석_닫는_괄호_뒤에_문장이_이어지면_거절한다() {
        assertThatThrownBy(() -> policy.validateReply(
                "무무! (해석: 안녕!) 이야기 잘 이어지고 있다니 다행이야.",
                "MUMU"
        )).isInstanceOf(FallbackRequiredException.class);
    }
}
