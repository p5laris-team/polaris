package p5laris.ai.domain.domain.policy;

import org.junit.jupiter.api.Test;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.CharacterToneType;
import p5laris.ai.domain.exception.FallbackRequiredException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacterTonePolicyTest {

    private final CharacterTonePolicy policy = new CharacterTonePolicy();

    @Test
    void resolvesTrimmedCaseInsensitiveCharacterType() {
        assertThat(policy.resolve("  nova  ")).isEqualTo(CharacterToneType.NOVA);
    }

    @Test
    void rejectsBlankCharacterTypeAsInvalidOutput() {
        assertThatThrownBy(() -> policy.resolve(" "))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.INVALID_OUTPUT);
    }

    @Test
    void rejectsUnsupportedCharacterTypeAsInvalidOutput() {
        assertThatThrownBy(() -> policy.resolve("UNKNOWN"))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.INVALID_OUTPUT);
    }
}
