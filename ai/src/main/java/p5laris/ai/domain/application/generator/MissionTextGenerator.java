package p5laris.ai.domain.application.generator;

import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;

/**
 * 미션 문구 생성 provider를 교체하기 위한 포트다.
 *
 * application service는 local/Gemini/OpenAI 중 어떤 구현을 쓰는지 모르고 이 계약만 호출한다.
 */
public interface MissionTextGenerator {

    MissionTextCandidate generate(MissionTextGenerationCommand command);
}
