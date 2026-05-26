package p5laris.mission.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.mission.core.entity.BaseEntity;
import p5laris.mission.domain.domain.enums.MissionRewardOutboxStatus;

import java.time.LocalDateTime;

/**
 * 미션 완료 보상을 wallet 모듈에 지급하기 위한 outbox 엔티티다.
 *
 * mission DB에는 "어떤 미션 보상을 어떤 멱등키로 지급해야 하는지"를 먼저 남긴다.
 * wallet gRPC 호출이 실패해도 이 row가 남아 있으므로 스케줄러가 같은 idempotencyKey로 안전하게 재시도할 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mission_reward_outbox")
public class MissionRewardOutbox extends BaseEntity {

    private static final int LAST_ERROR_MESSAGE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_id", nullable = false, unique = true)
    private Long missionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reward_star_piece", nullable = false)
    private int rewardStarPiece;

    @Column(name = "idempotency_key", nullable = false, length = 120, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MissionRewardOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    public static MissionRewardOutbox pending(
            UserMission mission,
            String idempotencyKey,
            LocalDateTime nextAttemptAt
    ) {
        MissionRewardOutbox outbox = new MissionRewardOutbox();
        outbox.missionId = mission.getId();
        outbox.userId = mission.getUserId();
        outbox.rewardStarPiece = mission.getRewardStarPiece();
        outbox.idempotencyKey = idempotencyKey;
        outbox.status = MissionRewardOutboxStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.nextAttemptAt = nextAttemptAt;
        return outbox;
    }

    // PROCESSING 상태의 nextAttemptAt은 "다른 스케줄러가 다시 집을 수 있는 lock 만료 시각"으로 사용한다.
    public void markProcessing(LocalDateTime lockExpiresAt) {
        this.status = MissionRewardOutboxStatus.PROCESSING;
        this.nextAttemptAt = lockExpiresAt;
    }

    public void markSucceeded(LocalDateTime succeededAt) {
        this.status = MissionRewardOutboxStatus.SUCCEEDED;
        this.nextAttemptAt = succeededAt;
        this.lastErrorMessage = null;
    }

    // 실패 횟수를 올리고 다음 재시도 시각을 기록한다. maxAttempts에 도달하면 자동 재처리 대상에서 제외한다.
    public void recordFailure(String errorMessage, LocalDateTime nextAttemptAt, int maxAttempts) {
        this.attemptCount++;
        this.lastErrorMessage = truncate(errorMessage);
        this.nextAttemptAt = nextAttemptAt;
        this.status = this.attemptCount >= maxAttempts
                ? MissionRewardOutboxStatus.FAILED
                : MissionRewardOutboxStatus.PENDING;
    }

    // PENDING 또는 lock이 만료된 PROCESSING row만 스케줄러가 다시 발송할 수 있다.
    public boolean canBeClaimed(LocalDateTime now) {
        return (status == MissionRewardOutboxStatus.PENDING || status == MissionRewardOutboxStatus.PROCESSING)
                && !nextAttemptAt.isAfter(now);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= LAST_ERROR_MESSAGE_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, LAST_ERROR_MESSAGE_MAX_LENGTH);
    }
}
