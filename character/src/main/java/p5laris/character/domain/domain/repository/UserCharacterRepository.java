package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import p5laris.character.domain.domain.entity.UserCharacter;

import java.util.List;
import java.util.Optional;

public interface UserCharacterRepository extends JpaRepository<UserCharacter, Long> {

    /**
     * 사용자의 현재 활성 캐릭터 조회.
     * MVP에서 사용자는 활성 캐릭터를 1개만 가진다 (AGENTS.md §20.1).
     */
    Optional<UserCharacter> findByUserIdAndActiveTrue(Long userId);

    /** 사용자의 전체 캐릭터 목록 조회 */
    List<UserCharacter> findByUserId(Long userId);

    /** 상태 알림 점검 대상인 활성 캐릭터를 페이지 단위로 조회 */
    Page<UserCharacter> findByActiveTrue(Pageable pageable);

    /** 사용자가 해당 캐릭터를 소유하는지 확인 (소유권 검증) */
    boolean existsByIdAndUserId(Long id, Long userId);
}
