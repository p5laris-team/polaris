package p5laris.item.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import p5laris.item.domain.domain.entity.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT o FROM OutboxEvent o WHERE (o.status = 'PENDING' OR o.status = 'FAILED') AND o.nextAttemptAt <= :now ORDER BY o.nextAttemptAt ASC")
    List<OutboxEvent> findPendingOrFailedEvents(@Param("now") LocalDateTime now);
}
