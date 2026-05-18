package p5laris.user.domain.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    boolean existsByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);
    
    List<AttendanceRecord> findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
            Long userId, LocalDate startDate, LocalDate endDate);
}
