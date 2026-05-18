package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.AttendanceRecord;
import p5laris.user.domain.domain.AttendanceRecordRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final WalletService walletService;

    private static final int ATTENDANCE_REWARD = 10;

    @Transactional
    public AttendanceRecord attend(Long userId) {
        LocalDate today = LocalDate.now();
        
        if (attendanceRecordRepository.existsByUserIdAndAttendanceDate(userId, today)) {
            throw new RuntimeException("ALREADY_ATTENDED");
        }

        LocalDate yesterday = today.minusDays(1);
        int streakCount = 1;
        
        List<AttendanceRecord> pastRecords = attendanceRecordRepository.findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                userId, yesterday, yesterday);
                
        if (!pastRecords.isEmpty()) {
            streakCount = pastRecords.get(0).getStreakCount() + 1;
        }

        AttendanceRecord record = AttendanceRecord.builder()
                .userId(userId)
                .attendanceDate(today)
                .rewardStarPiece(ATTENDANCE_REWARD)
                .streakCount(streakCount)
                .build();
                
        attendanceRecordRepository.saveAndFlush(record);

        String dateStr = today.format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String idempotencyKey = "ATTENDANCE:" + userId + ":" + dateStr;
        
        walletService.earnStarPiece(
                userId, 
                ATTENDANCE_REWARD, 
                "ATTENDANCE", 
                "ATTENDANCE", 
                record.getId(), 
                idempotencyKey
        );

        return record;
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecord> getAttendanceRecords(Long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return attendanceRecordRepository.findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                userId, startDate, endDate);
    }
}
