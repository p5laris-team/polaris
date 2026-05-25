package p5laris.ai.domain.infrastructure.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.generator.MissionTextGenerator;
import p5laris.ai.domain.domain.enums.CharacterToneType;
import p5laris.ai.domain.domain.policy.CharacterTonePolicy;

import java.util.Objects;

/**
 * 외부 AI 없이 캐릭터 타입별 규칙으로 문구를 만드는 rule-based generator다.
 *
 * provider 장애 또는 운영에서 외부 AI를 끈 상태에서도 API 흐름을 유지하는 기본 구현체다.
 */
@Component
@RequiredArgsConstructor
public class RuleBasedMissionTextGenerator implements MissionTextGenerator {

    private final CharacterTonePolicy characterTonePolicy;

    // 캐릭터 타입을 해석한 뒤, 해당 캐릭터의 말투 정책으로 문구 3개를 만든다.
    @Override
    public MissionTextCandidate generate(MissionTextGenerationCommand command) {
        CharacterToneType toneType = characterTonePolicy.resolve(command.characterType());
        long variationSeed = variationSeed(command);

        return new MissionTextCandidate(
                toneType.characterMessage(command.baseTitle(), variationSeed),
                toneType.completionQuestion(variationSeed),
                toneType.completionResponse(variationSeed)
        );
    }

    private long variationSeed(MissionTextGenerationCommand command) {
        // requestId는 재시도마다 달라질 수 있으므로 제외한다. 같은 미션 문맥이면 같은 말투가 나와야 한다.
        return Objects.hash(
                command.userId(),
                command.characterId(),
                command.characterType(),
                command.missionTemplateId(),
                command.baseTitle()
        );
    }
}
