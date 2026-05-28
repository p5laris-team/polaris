package p5laris.character.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.character.domain.domain.enums.ShareRewardOutboxStatus;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "share_reward_outbox")
public class ShareRewardOutbox {

    private static final int LAST_ERROR_MESSAGE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_log_id", nullable = false, unique = true)
    private Long shareLogId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reward_star_piece", nullable = false)
    private int rewardStarPiece;

    @Column(name = "idempotency_key", nullable = false, length = 120, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShareRewardOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static ShareRewardOutbox pending(ShareLog shareLog, String idempotencyKey, LocalDateTime nextAttemptAt) {
        ShareRewardOutbox outbox = new ShareRewardOutbox();
        outbox.shareLogId = shareLog.getId();
        outbox.userId = shareLog.getUserId();
        outbox.rewardStarPiece = shareLog.getRewardStarPiece();
        outbox.idempotencyKey = idempotencyKey;
        outbox.status = ShareRewardOutboxStatus.PENDING;
        outbox.attemptCount = 0;
        outbox.nextAttemptAt = nextAttemptAt;
        return outbox;
    }

    public void markProcessing(LocalDateTime lockExpiresAt) {
        this.status = ShareRewardOutboxStatus.PROCESSING;
        this.nextAttemptAt = lockExpiresAt;
    }

    public void markSucceeded(LocalDateTime succeededAt) {
        this.status = ShareRewardOutboxStatus.SUCCEEDED;
        this.nextAttemptAt = succeededAt;
        this.lastErrorMessage = null;
    }

    public void recordFailure(String errorMessage, LocalDateTime nextAttemptAt, int maxAttempts) {
        this.attemptCount++;
        this.lastErrorMessage = truncate(errorMessage);
        this.nextAttemptAt = nextAttemptAt;
        this.status = this.attemptCount >= maxAttempts
                ? ShareRewardOutboxStatus.FAILED
                : ShareRewardOutboxStatus.PENDING;
    }

    public boolean canBeClaimed(LocalDateTime now) {
        return (status == ShareRewardOutboxStatus.PENDING || status == ShareRewardOutboxStatus.PROCESSING)
                && !nextAttemptAt.isAfter(now);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
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
