package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.CharacterCareLog;

import java.util.List;

public interface CharacterCareLogRepository extends JpaRepository<CharacterCareLog, Long> {

    /** 캐릭터의 돌봄 로그를 최신순으로 조회 */
    List<CharacterCareLog> findByCharacterIdOrderByCreatedAtDesc(Long characterId);
}
