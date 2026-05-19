package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;

/**
 * 미션 문구 생성 provider를 교체하기 위한 포트다.
 *
 * 현재 구현체는 LocalMissionTextGenerator지만, 다음 PR에서 Gemini/OpenAI 구현체를 같은 계약으로 추가할 수 있다.
 */
public interface MissionTextGenerator {

    MissionTextCandidate generate(MissionTextGenerationCommand command);
}
