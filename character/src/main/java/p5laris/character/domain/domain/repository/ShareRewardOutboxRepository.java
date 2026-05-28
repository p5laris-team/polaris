package p5laris.character.domain.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.character.domain.domain.entity.ShareRewardOutbox;
import p5laris.character.domain.domain.enums.ShareRewardOutboxStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShareRewardOutboxRepository extends JpaRepository<ShareRewardOutbox, Long> {

    Optional<ShareRewardOutbox> findByShareLogId(Long shareLogId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from ShareRewardOutbox o
            where o.id = :id
            """)
    Optional<ShareRewardOutbox> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o.id
            from ShareRewardOutbox o
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
            @Param("pendingStatus") ShareRewardOutboxStatus pendingStatus,
            @Param("processingStatus") ShareRewardOutboxStatus processingStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
