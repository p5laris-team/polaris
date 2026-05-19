package p5laris.mission.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;

import java.util.Optional;

public interface MissionCompletionAnswerRepository extends JpaRepository<MissionCompletionAnswer, Long> {

    // 미션당 완료 질문/답변은 하나만 존재한다.
    Optional<MissionCompletionAnswer> findByMissionId(Long missionId);
}
