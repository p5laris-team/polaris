package p5laris.ai.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.ai.domain.domain.entity.AiMissionGeneration;

/**
 * ai_mission_generations 저장 repository다.
 */
public interface AiMissionGenerationRepository extends JpaRepository<AiMissionGeneration, Long> {
}
