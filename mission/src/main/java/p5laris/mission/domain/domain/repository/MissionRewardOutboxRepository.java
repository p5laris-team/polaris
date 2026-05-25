package p5laris.mission.domain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.mission.domain.domain.entity.MissionRewardOutbox;
import p5laris.mission.domain.domain.enums.MissionRewardOutboxStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 미션 보상 outbox 조회 repository다.
 *
 * 재처리 스케줄러는 due row의 id 목록만 먼저 조회하고, 실제 발송 직전에는 findByIdForUpdate로 다시 lock을 잡는다.
 */
public interface MissionRewardOutboxRepository extends JpaRepository<MissionRewardOutbox, Long> {

    Optional<MissionRewardOutbox> findByMissionId(Long missionId);

    // 같은 outbox row를 여러 서버가 동시에 발송하지 않도록 발송 직전에 pessimistic lock을 잡는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from MissionRewardOutbox o
            where o.id = :id
            """)
    Optional<MissionRewardOutbox> findByIdForUpdate(@Param("id") Long id);

    // 재처리 시각이 지난 PENDING row와 lock이 만료된 PROCESSING row를 오래된 순서대로 가져온다.
    @Query("""
            select o.id
            from MissionRewardOutbox o
            where (
                o.status = :pendingStatus
                and o.nextAttemptAt <= :now
            ) or (
                o.status = :processingStatus
                and o.nextAttemptAt <= :now
            )
            order by o.nextAttemptAt asc, o.id asc
            """)
    List<Long> findDispatchableIds(
            @Param("pendingStatus") MissionRewardOutboxStatus pendingStatus,
            @Param("processingStatus") MissionRewardOutboxStatus processingStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
