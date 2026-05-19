package p5laris.eventlog.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.eventlog.domain.domain.entity.EventLog;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
}
