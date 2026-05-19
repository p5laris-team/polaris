package p5laris.ai.domain.infrastructure.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.generator.MissionTextGenerator;
import p5laris.ai.domain.domain.enums.CharacterToneType;
import p5laris.ai.domain.domain.policy.CharacterTonePolicy;

/**
 * 외부 AI 없이 캐릭터 타입별 고정 정책으로 문구를 만드는 local generator다.
 *
 * provider 장애 또는 개발 환경에서도 API 흐름을 검증할 수 있게 하는 기본 구현체다.
 */
@Component
@RequiredArgsConstructor
public class LocalMissionTextGenerator implements MissionTextGenerator {

    private final CharacterTonePolicy characterTonePolicy;

    // 캐릭터 타입을 해석한 뒤, 해당 캐릭터의 말투 정책으로 문구 3개를 만든다.
    @Override
    public MissionTextCandidate generate(MissionTextGenerationCommand command) {
        CharacterToneType toneType = characterTonePolicy.resolve(command.characterType());

        return new MissionTextCandidate(
                toneType.characterMessage(command.baseTitle()),
                toneType.completionQuestion(),
                toneType.completionResponse()
        );
    }
}
