package p5laris.user.domain.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.user.domain.application.event.UserEventLogEvent;
import p5laris.user.domain.domain.entity.AttendanceRecord;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.domain.repository.AttendanceRecordRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void firstAttendanceCreatesRewardAndEvents() {
        LocalDate today = LocalDate.now();
        AttendanceService service = new AttendanceService(attendanceRecordRepository, walletService, eventPublisher);
        when(attendanceRecordRepository.existsByUserIdAndAttendanceDate(1L, today)).thenReturn(false);
        when(attendanceRecordRepository.findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                1L, today.minusDays(1), today.minusDays(1)
        )).thenReturn(List.of());
        when(attendanceRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            AttendanceRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 10L);
            return record;
        });
        StarPieceTransaction transaction = StarPieceTransaction.builder()
                .id(20L)
                .userId(1L)
                .transactionType("EARN")
                .amount(10)
                .balanceAfter(110)
                .reason("ATTENDANCE")
                .refType("ATTENDANCE")
                .refId(10L)
                .build();
        when(walletService.earnStarPiece(
                eq(1L), eq(10), eq("ATTENDANCE"), eq("ATTENDANCE"), eq(10L), any()
        )).thenReturn(transaction);

        AttendanceRecord result = service.attend(1L);

        assertThat(result.getAttendanceDate()).isEqualTo(today);
        assertThat(result.getRewardStarPiece()).isEqualTo(10);
        assertThat(result.getStreakCount()).isEqualTo(1);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(walletService).earnStarPiece(
                eq(1L), eq(10), eq("ATTENDANCE"), eq("ATTENDANCE"), eq(10L), keyCaptor.capture()
        );
        assertThat(keyCaptor.getValue())
                .isEqualTo("ATTENDANCE:1:" + today.format(DateTimeFormatter.BASIC_ISO_DATE));
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(any(UserEventLogEvent.class));
    }

    @Test
    void yesterdayAttendanceIncrementsStreak() {
        LocalDate today = LocalDate.now();
        AttendanceRecord yesterday = AttendanceRecord.builder()
                .userId(1L)
                .attendanceDate(today.minusDays(1))
                .rewardStarPiece(10)
                .streakCount(7)
                .build();
        AttendanceService service = new AttendanceService(attendanceRecordRepository, walletService, eventPublisher);
        when(attendanceRecordRepository.existsByUserIdAndAttendanceDate(1L, today)).thenReturn(false);
        when(attendanceRecordRepository.findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                1L, today.minusDays(1), today.minusDays(1)
        )).thenReturn(List.of(yesterday));
        when(attendanceRecordRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            AttendanceRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 11L);
            return record;
        });
        when(walletService.earnStarPiece(any(), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(StarPieceTransaction.builder().id(21L).build());

        AttendanceRecord result = service.attend(1L);

        assertThat(result.getStreakCount()).isEqualTo(8);
    }

    @Test
    void duplicateAttendanceDoesNotPayRewardAgain() {
        LocalDate today = LocalDate.now();
        AttendanceService service = new AttendanceService(attendanceRecordRepository, walletService, eventPublisher);
        when(attendanceRecordRepository.existsByUserIdAndAttendanceDate(1L, today)).thenReturn(true);

        assertThatThrownBy(() -> service.attend(1L))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.ALREADY_ATTENDED);

        verify(attendanceRecordRepository, never()).saveAndFlush(any());
        verify(walletService, never()).earnStarPiece(any(), any(Integer.class), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void monthlyLookupUsesFirstAndLastDay() {
        AttendanceService service = new AttendanceService(attendanceRecordRepository, walletService, eventPublisher);
        when(attendanceRecordRepository.findByUserIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
                1L, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)
        )).thenReturn(List.of());

        assertThat(service.getAttendanceRecords(1L, 2024, 2)).isEmpty();
    }
}
