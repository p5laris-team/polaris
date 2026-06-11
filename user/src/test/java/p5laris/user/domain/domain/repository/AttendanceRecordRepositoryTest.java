package p5laris.user.domain.domain.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import p5laris.user.domain.domain.entity.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AttendanceRecordRepositoryTest {

    @Autowired
    private AttendanceRecordRepository repository;

    @Test
    void mapsEntityAndReturnsMonthlyRecordsInDescendingDateOrder() {
        repository.saveAllAndFlush(List.of(
                record(1L, LocalDate.of(2026, 6, 1), 1),
                record(1L, LocalDate.of(2026, 6, 10), 2),
                record(1L, LocalDate.of(2026, 7, 1), 3),
                record(2L, LocalDate.of(2026, 6, 20), 4)
        ));

        List<AttendanceRecord> result =
                repository.findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                        1L,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30)
                );

        assertThat(result)
                .extracting(AttendanceRecord::getAttendanceDate)
                .containsExactly(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1));
        assertThat(result)
                .extracting(AttendanceRecord::getStreakCount)
                .containsExactly(2, 1);
    }

    @Test
    void userAndAttendanceDateCombinationIsUnique() {
        repository.saveAndFlush(record(1L, LocalDate.of(2026, 6, 11), 1));

        assertThatThrownBy(() ->
                repository.saveAndFlush(record(1L, LocalDate.of(2026, 6, 11), 2))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsQuerySeparatesUsersAndDates() {
        repository.saveAndFlush(record(1L, LocalDate.of(2026, 6, 11), 1));

        assertThat(repository.existsByUserIdAndAttendanceDate(1L, LocalDate.of(2026, 6, 11))).isTrue();
        assertThat(repository.existsByUserIdAndAttendanceDate(1L, LocalDate.of(2026, 6, 12))).isFalse();
        assertThat(repository.existsByUserIdAndAttendanceDate(2L, LocalDate.of(2026, 6, 11))).isFalse();
    }

    private AttendanceRecord record(Long userId, LocalDate attendanceDate, int streakCount) {
        return AttendanceRecord.builder()
                .userId(userId)
                .attendanceDate(attendanceDate)
                .rewardStarPiece(10)
                .streakCount(streakCount)
                .build();
    }
}
