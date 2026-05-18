package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.ShareCard;

import java.util.Optional;

public interface ShareCardRepository extends JpaRepository<ShareCard, Long> {

    /** 사용자 + 캐릭터 기준으로 공유 카드 조회 */
    Optional<ShareCard> findByUserIdAndCharacterId(Long userId, Long characterId);
}
