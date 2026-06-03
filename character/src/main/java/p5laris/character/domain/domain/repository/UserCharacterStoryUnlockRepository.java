package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.UserCharacterStoryUnlock;

import java.util.Collection;
import java.util.List;

public interface UserCharacterStoryUnlockRepository extends JpaRepository<UserCharacterStoryUnlock, Long> {

    /**
     * 후보 조각 중 이미 해금한 조각을 한 번에 조회한다.
     *
     * 조각마다 개별 exists 조회를 하지 않도록 IN 조회로 묶어 N+1을 피한다.
     */
    List<UserCharacterStoryUnlock> findByUserIdAndUserCharacterIdAndStoryFragmentIdIn(
            Long userId,
            Long userCharacterId,
            Collection<Long> storyFragmentIds
    );
}
